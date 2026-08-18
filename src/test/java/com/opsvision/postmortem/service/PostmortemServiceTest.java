package com.opsvision.postmortem.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.model.IncidentStatus;
import com.opsvision.incident.model.ProbableCause;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.model.TimelineSource;
import com.opsvision.incident.repository.IncidentRepository;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.postmortem.model.PostmortemResult;
import com.opsvision.recovery.model.RecoveryAction;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
import com.opsvision.recovery.service.RecoveryRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostmortemServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private RootCauseAnalysisService rootCauseAnalysisService;

    @Mock
    private RecoveryRecommendationService recoveryRecommendationService;

    private PostmortemService service;

    @BeforeEach
    void setUp() {
        service = new PostmortemService(
                incidentRepository,
                rootCauseAnalysisService,
                recoveryRecommendationService
        );
    }

    @Test
    void generate_missingIncident_throws() {
        when(incidentRepository.findByIdWithTimeline(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(99L))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void generateFrom_deploymentRegression_buildsStructuredPostmortem() {
        Instant detected = Instant.parse("2026-08-18T10:05:00Z");
        Instant resolved = Instant.parse("2026-08-18T10:45:00Z");

        ProjectRepository repo = new ProjectRepository("acme", "api", "main", "https://github.com/acme/api");
        setId(repo, 1L);
        Deployment deployment = new Deployment(repo, "v2cafebabe", "main", "prod", DeploymentStatus.SUCCEEDED);
        setId(deployment, 2L);

        Incident incident = new Incident("Elevated errors", IncidentSeverity.HIGH, detected);
        setId(incident, 10L);
        incident.setDeployment(deployment);
        incident.setCommitSha("v2cafebabe");
        incident.setEnvironment("prod");
        incident.setNamespace("prod");
        incident.setWorkloadName("api");
        incident.setSummary("Error ratio exceeded threshold after deploy");
        incident.setResolvedAt(resolved);
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.addTimelineEntry(new IncidentTimelineEntry(
                detected.minusSeconds(60),
                TimelineEntryType.DEPLOYMENT,
                TimelineSource.DEPLOYMENT,
                "Deployment completed",
                "commit=v2cafebabe",
                "deploy",
                0
        ));
        incident.addTimelineEntry(new IncidentTimelineEntry(
                detected,
                TimelineEntryType.METRIC,
                TimelineSource.PROMETHEUS,
                "Error rate spike",
                "error_ratio=0.12",
                "error_ratio",
                1
        ));

        RootCauseAnalysisResult rca = new RootCauseAnalysisResult(
                10L,
                Instant.parse("2026-08-18T11:00:00Z"),
                "deterministic-correlation",
                "RCA summary",
                List.of(new ProbableCause(
                        "Recent deployment likely introduced elevated error rate or reduced availability",
                        0.91,
                        "DEPLOYMENT_REGRESSION",
                        List.of("Error rate increased two minutes after deployment")
                )),
                List.of()
        );

        RecoveryRecommendationResult recovery = new RecoveryRecommendationResult(
                10L,
                Instant.parse("2026-08-18T11:00:00Z"),
                RecoveryAction.ROLLBACK,
                "Roll back to previous version",
                "v1deadbeef",
                1L,
                "v2cafebabe",
                "DEPLOYMENT_REGRESSION",
                0.91,
                true,
                RecoveryRecommendationResult.EXECUTION_MODE_RECOMMENDATION_ONLY,
                List.of("errors up"),
                List.of("Recommendation only")
        );

        PostmortemResult result = service.generateFrom(incident, rca, recovery);

        assertThat(result.incidentId()).isEqualTo(10L);
        assertThat(result.method()).isEqualTo(PostmortemResult.METHOD);
        assertThat(result.title()).contains("Elevated errors");
        assertThat(result.durationMinutes()).isEqualTo(40L);
        assertThat(result.topRcaCategory()).isEqualTo("DEPLOYMENT_REGRESSION");
        assertThat(result.topRcaConfidence()).isEqualTo(0.91);
        assertThat(result.recommendedRecoveryAction()).isEqualTo(RecoveryAction.ROLLBACK);
        assertThat(result.recoveryTargetVersion()).isEqualTo("v1deadbeef");
        assertThat(result.timelineHighlights()).hasSize(2);
        assertThat(result.actionItems()).isNotEmpty();
        assertThat(result.whatWentWell()).anyMatch(s -> s.toLowerCase().contains("timeline"));
        assertThat(result.markdownBody())
                .contains("# Postmortem:")
                .contains("## Executive summary")
                .contains("## Action items")
                .contains("opsvision-incident-id: 10")
                .contains("v1deadbeef");
        assertThat(result.limitations())
                .anyMatch(l -> l.toLowerCase().contains("human review"));
    }

    @Test
    void generateFrom_sparseIncident_stillReturnsDraft() {
        Instant at = Instant.parse("2026-08-18T12:00:00Z");
        Incident incident = new Incident("Sparse", IncidentSeverity.LOW, at);
        setId(incident, 3L);

        RootCauseAnalysisResult rca = new RootCauseAnalysisResult(
                3L,
                at,
                "deterministic-correlation",
                "summary",
                List.of(new ProbableCause(
                        "Insufficient correlated signals to attribute a specific root cause",
                        0.20,
                        "INSUFFICIENT_DATA",
                        List.of("no timeline")
                )),
                List.of("Collect richer timeline signals")
        );

        RecoveryRecommendationResult recovery = new RecoveryRecommendationResult(
                3L,
                at,
                RecoveryAction.INVESTIGATE,
                "Need more data",
                null,
                null,
                null,
                "INSUFFICIENT_DATA",
                0.20,
                true,
                RecoveryRecommendationResult.EXECUTION_MODE_RECOMMENDATION_ONLY,
                List.of(),
                List.of()
        );

        PostmortemResult result = service.generateFrom(incident, rca, recovery);

        assertThat(result.durationMinutes()).isNull();
        assertThat(result.timelineHighlights()).isEmpty();
        assertThat(result.whatWentWrong()).anyMatch(s -> s.toLowerCase().contains("timeline"));
        assertThat(result.actionItems()).anyMatch(s -> s.toLowerCase().contains("tracking issue"));
        assertThat(result.markdownBody()).contains("INSUFFICIENT_DATA");
    }

    private static void setId(Object entity, long id) {
        Class<?> type = entity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("id");
                field.setAccessible(true);
                field.set(entity, id);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("No id field on " + entity.getClass());
    }
}
