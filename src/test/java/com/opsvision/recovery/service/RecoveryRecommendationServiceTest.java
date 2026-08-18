package com.opsvision.recovery.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.deployment.repository.DeploymentRepository;
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
import com.opsvision.recovery.model.RecoveryAction;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
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
class RecoveryRecommendationServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private RootCauseAnalysisService rootCauseAnalysisService;

    @Mock
    private DeploymentRepository deploymentRepository;

    private RecoveryRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecoveryRecommendationService(
                incidentRepository,
                rootCauseAnalysisService,
                deploymentRepository
        );
    }

    @Test
    void recommend_missingIncident_throws() {
        when(incidentRepository.findByIdWithTimeline(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recommend(99L))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void deploymentRegression_highConfidence_recommendsRollbackWithPriorVersion() {
        Instant deployAt = Instant.parse("2026-08-18T10:04:00Z");
        ProjectRepository repo = new ProjectRepository("acme", "api", "main", "https://github.com/acme/api");
        setId(repo, 1L);

        Deployment previous = new Deployment(repo, "v1deadbeef", "main", "prod", DeploymentStatus.SUCCEEDED);
        setId(previous, 1L);
        previous.setDeployedAt(deployAt.minusSeconds(3600));

        Deployment current = new Deployment(repo, "v2cafebabe", "main", "prod", DeploymentStatus.SUCCEEDED);
        setId(current, 2L);
        current.setDeployedAt(deployAt);

        Incident incident = new Incident("Elevated errors", IncidentSeverity.HIGH, deployAt.plusSeconds(120));
        setId(incident, 10L);
        incident.setDeployment(current);
        incident.setCommitSha("v2cafebabe");

        when(deploymentRepository.findByRepositoryIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(current, previous));

        RootCauseAnalysisResult rca = rca(
                10L,
                new ProbableCause(
                        "Recent deployment likely introduced elevated error rate or reduced availability",
                        0.91,
                        "DEPLOYMENT_REGRESSION",
                        List.of("Error rate increased two minutes after deployment")
                )
        );

        RecoveryRecommendationResult result = service.recommendFrom(incident, rca);

        assertThat(result.action()).isEqualTo(RecoveryAction.ROLLBACK);
        assertThat(result.targetVersion()).isEqualTo("v1deadbeef");
        assertThat(result.targetDeploymentId()).isEqualTo(1L);
        assertThat(result.requiresHumanApproval()).isTrue();
        assertThat(result.executionMode())
                .isEqualTo(RecoveryRecommendationResult.EXECUTION_MODE_RECOMMENDATION_ONLY);
        assertThat(result.reason()).containsIgnoringCase("previous version");
        assertThat(result.notes()).anyMatch(n -> n.toLowerCase().contains("recommendation only"));
    }

    @Test
    void workloadZeroReadyWithoutDeploy_recommendsRestart() {
        Instant at = Instant.parse("2026-08-18T11:00:00Z");
        Incident incident = new Incident("Unhealthy workload", IncidentSeverity.CRITICAL, at);
        setId(incident, 11L);
        incident.addTimelineEntry(new IncidentTimelineEntry(
                at, TimelineEntryType.WORKLOAD, TimelineSource.KUBERNETES,
                "Unhealthy workload",
                "ready=0 desired=3 unavailable=3",
                "workload_unhealthy",
                0
        ));

        RootCauseAnalysisResult rca = rca(
                11L,
                new ProbableCause(
                        "Workload rollout left zero ready replicas",
                        0.88,
                        "WORKLOAD_ROLLOUT",
                        List.of("ready=0")
                )
        );

        RecoveryRecommendationResult result = service.recommendFrom(incident, rca);

        assertThat(result.action()).isEqualTo(RecoveryAction.RESTART);
        assertThat(result.requiresHumanApproval()).isTrue();
    }

    @Test
    void podInstabilityWithoutCrash_recommendsRestart() {
        Instant at = Instant.parse("2026-08-18T12:00:00Z");
        Incident incident = new Incident("Restarts", IncidentSeverity.HIGH, at);
        setId(incident, 12L);
        incident.addTimelineEntry(new IncidentTimelineEntry(
                at, TimelineEntryType.POD, TimelineSource.KUBERNETES,
                "Elevated pod restart count",
                "restarts=5",
                "pod_restarts",
                0
        ));

        RootCauseAnalysisResult rca = rca(
                12L,
                new ProbableCause(
                        "Elevated pod restart count indicates runtime instability",
                        0.70,
                        "POD_INSTABILITY",
                        List.of("restarts")
                )
        );

        assertThat(service.recommendFrom(incident, rca).action()).isEqualTo(RecoveryAction.RESTART);
    }

    @Test
    void schedulingPressure_recommendsScaleUp() {
        Instant at = Instant.parse("2026-08-18T13:00:00Z");
        Incident incident = new Incident("Scheduling", IncidentSeverity.MEDIUM, at);
        setId(incident, 13L);
        incident.addTimelineEntry(new IncidentTimelineEntry(
                at, TimelineEntryType.KUBERNETES_EVENT, TimelineSource.KUBERNETES,
                "Warning events",
                "FailedScheduling: insufficient cpu",
                "k8s_warnings",
                0
        ));

        RootCauseAnalysisResult rca = rca(
                13L,
                new ProbableCause(
                        "Kubernetes warning events indicate cluster or scheduling pressure",
                        0.52,
                        "KUBERNETES_EVENTS",
                        List.of("FailedScheduling")
                )
        );

        assertThat(service.recommendFrom(incident, rca).action()).isEqualTo(RecoveryAction.SCALE_UP);
    }

    @Test
    void metricsOnly_recommendsInvestigate() {
        Instant at = Instant.parse("2026-08-18T14:00:00Z");
        Incident incident = new Incident("Metrics", IncidentSeverity.HIGH, at);
        setId(incident, 14L);

        RootCauseAnalysisResult rca = rca(
                14L,
                new ProbableCause(
                        "Service metrics show degradation without a correlated deployment",
                        0.48,
                        "METRIC_DEGRADATION",
                        List.of("availability low")
                )
        );

        assertThat(service.recommendFrom(incident, rca).action()).isEqualTo(RecoveryAction.INVESTIGATE);
    }

    @Test
    void resolvedIncident_recommendsNoAction() {
        Instant at = Instant.parse("2026-08-18T15:00:00Z");
        Incident incident = new Incident("Done", IncidentSeverity.LOW, at);
        setId(incident, 15L);
        incident.setStatus(IncidentStatus.RESOLVED);

        RootCauseAnalysisResult rca = rca(
                15L,
                new ProbableCause("x", 0.9, "DEPLOYMENT_REGRESSION", List.of())
        );

        RecoveryRecommendationResult result = service.recommendFrom(incident, rca);

        assertThat(result.action()).isEqualTo(RecoveryAction.NO_ACTION);
        assertThat(result.requiresHumanApproval()).isFalse();
    }

    @Test
    void moderateDeployRegression_recommendsInvestigateNotRollback() {
        ProjectRepository repo = new ProjectRepository("acme", "api", "main", "https://github.com/acme/api");
        setId(repo, 1L);
        Deployment current = new Deployment(repo, "abc", "main", "prod", DeploymentStatus.SUCCEEDED);
        setId(current, 2L);

        Incident incident = new Incident("maybe", IncidentSeverity.MEDIUM, Instant.parse("2026-08-18T16:00:00Z"));
        setId(incident, 16L);
        incident.setDeployment(current);

        when(deploymentRepository.findByRepositoryIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(current));

        RootCauseAnalysisResult rca = rca(
                16L,
                new ProbableCause("maybe deploy", 0.55, "DEPLOYMENT_REGRESSION", List.of())
        );

        assertThat(service.recommendFrom(incident, rca).action()).isEqualTo(RecoveryAction.INVESTIGATE);
    }

    private static RootCauseAnalysisResult rca(long incidentId, ProbableCause top) {
        return new RootCauseAnalysisResult(
                incidentId,
                Instant.parse("2026-08-18T12:00:00Z"),
                "deterministic-correlation",
                "summary",
                List.of(top),
                List.of()
        );
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
