package com.opsvision.incident.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import com.opsvision.evidence.repository.FindingRepository;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.model.TimelineSource;
import com.opsvision.incident.repository.IncidentRepository;
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
class RootCauseAnalysisServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private FindingRepository findingRepository;

    private RootCauseAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new RootCauseAnalysisService(incidentRepository, findingRepository);
    }

    @Test
    void analyze_missingIncident_throws() {
        when(incidentRepository.findByIdWithTimeline(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(99L))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void deploymentThenErrorsAndRestarts_ranksDeploymentRegressionHigh() {
        Instant deployAt = Instant.parse("2026-08-18T10:04:00Z");
        Instant errorAt = Instant.parse("2026-08-18T10:06:00Z");
        Instant restartAt = Instant.parse("2026-08-18T10:07:00Z");

        ProjectRepository repo = new ProjectRepository("acme", "api", "main", "https://github.com/acme/api");
        Deployment deployment = new Deployment(repo, "abc123", "main", "prod", DeploymentStatus.SUCCEEDED);
        setId(deployment, 7L);
        deployment.setDeployedAt(deployAt);

        Incident incident = new Incident("Elevated error ratio (api)", IncidentSeverity.HIGH, errorAt);
        setId(incident, 1L);
        incident.setDeployment(deployment);
        incident.setCommitSha("abc123");
        incident.addTimelineEntry(new IncidentTimelineEntry(
                deployAt, TimelineEntryType.DEPLOYMENT, TimelineSource.DEPLOYMENT,
                "Deployment completed", "commit abc123", "deployment", 0
        ));
        incident.addTimelineEntry(new IncidentTimelineEntry(
                errorAt, TimelineEntryType.METRIC, TimelineSource.PROMETHEUS,
                "Elevated error ratio", "Error ratio 0.18 exceeds threshold 0.05", "error_ratio", 1
        ));
        incident.addTimelineEntry(new IncidentTimelineEntry(
                restartAt, TimelineEntryType.POD, TimelineSource.KUBERNETES,
                "Elevated pod restart count", "Total pod restarts=5 (threshold=3)", "pod_restarts", 2
        ));

        when(findingRepository.findByDeploymentId(7L)).thenReturn(List.of());

        RootCauseAnalysisResult result = service.analyzeIncident(incident);

        assertThat(result.method()).isEqualTo(RootCauseAnalysisService.METHOD);
        assertThat(result.probableCauses()).isNotEmpty();
        assertThat(result.probableCauses().getFirst().category()).isEqualTo("DEPLOYMENT_REGRESSION");
        assertThat(result.probableCauses().getFirst().confidence()).isGreaterThanOrEqualTo(0.85);
        assertThat(result.probableCauses().getFirst().evidence())
                .anyMatch(e -> e.contains("error"))
                .anyMatch(e -> e.toLowerCase().contains("restart") || e.contains("pod"));
    }

    @Test
    void zeroReadyWorkload_ranksWorkloadRollout() {
        Instant at = Instant.parse("2026-08-18T11:00:00Z");
        Incident incident = new Incident("Unhealthy workload (api)", IncidentSeverity.CRITICAL, at);
        setId(incident, 2L);
        incident.addTimelineEntry(new IncidentTimelineEntry(
                at, TimelineEntryType.WORKLOAD, TimelineSource.KUBERNETES,
                "Unhealthy workload rollout: api",
                "ready=0 desired=3 unavailable=3 status=Progressing",
                "workload_unhealthy:api",
                0
        ));

        RootCauseAnalysisResult result = service.analyzeIncident(incident);

        assertThat(result.probableCauses().getFirst().category()).isEqualTo("WORKLOAD_ROLLOUT");
        assertThat(result.probableCauses().getFirst().confidence()).isGreaterThanOrEqualTo(0.85);
        assertThat(result.probableCauses().getFirst().cause()).containsIgnoringCase("zero ready");
    }

    @Test
    void crashLoopEvents_ranksPodInstability() {
        Instant at = Instant.parse("2026-08-18T12:00:00Z");
        Incident incident = new Incident("Pod crashes (api)", IncidentSeverity.HIGH, at);
        setId(incident, 3L);
        incident.addTimelineEntry(new IncidentTimelineEntry(
                at, TimelineEntryType.KUBERNETES_EVENT, TimelineSource.KUBERNETES,
                "Kubernetes warning events detected",
                "2 warning event(s); reasons: BackOff, CrashLoopBackOff",
                "k8s_warnings",
                0
        ));

        RootCauseAnalysisResult result = service.analyzeIncident(incident);

        assertThat(result.probableCauses().getFirst().category()).isEqualTo("POD_INSTABILITY");
        assertThat(result.probableCauses().getFirst().cause()).containsIgnoringCase("crash");
    }

    @Test
    void metricsOnlyWithoutDeploy_metricDegradationWithLimitation() {
        Instant at = Instant.parse("2026-08-18T13:00:00Z");
        Incident incident = new Incident("Low availability (api)", IncidentSeverity.HIGH, at);
        setId(incident, 4L);
        incident.addTimelineEntry(new IncidentTimelineEntry(
                at, TimelineEntryType.METRIC, TimelineSource.PROMETHEUS,
                "Low service availability",
                "Availability 0.80 below minimum 0.95",
                "availability",
                0
        ));

        RootCauseAnalysisResult result = service.analyzeIncident(incident);

        assertThat(result.probableCauses().getFirst().category()).isEqualTo("METRIC_DEGRADATION");
        assertThat(result.limitations())
                .anyMatch(l -> l.toLowerCase().contains("deployment") || l.toLowerCase().contains("metrics"));
    }

    @Test
    void emptyTimeline_insufficientData() {
        Incident incident = new Incident("Mystery", IncidentSeverity.MEDIUM,
                Instant.parse("2026-08-18T14:00:00Z"));
        setId(incident, 5L);

        RootCauseAnalysisResult result = service.analyzeIncident(incident);

        assertThat(result.probableCauses()).hasSize(1);
        assertThat(result.probableCauses().getFirst().category()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.limitations()).isNotEmpty();
    }

    @Test
    void criticalFindings_addSecurityContributingHypothesis() {
        Instant deployAt = Instant.parse("2026-08-18T15:00:00Z");
        Instant errorAt = Instant.parse("2026-08-18T15:05:00Z");

        ProjectRepository repo = new ProjectRepository("acme", "api", "main", "https://github.com/acme/api");
        Deployment deployment = new Deployment(repo, "def456", "main", "prod", DeploymentStatus.SUCCEEDED);
        setId(deployment, 9L);
        deployment.setDeployedAt(deployAt);

        Finding finding = new Finding(
                FindingType.CONTAINER,
                FindingSeverity.CRITICAL,
                "CVE-2024-0001 rce",
                "critical container vuln"
        );

        Incident incident = new Incident("Errors after deploy", IncidentSeverity.HIGH, errorAt);
        setId(incident, 6L);
        incident.setDeployment(deployment);
        incident.addTimelineEntry(new IncidentTimelineEntry(
                deployAt, TimelineEntryType.DEPLOYMENT, TimelineSource.DEPLOYMENT,
                "Deployment completed", "ok", "deployment", 0
        ));
        incident.addTimelineEntry(new IncidentTimelineEntry(
                errorAt, TimelineEntryType.METRIC, TimelineSource.PROMETHEUS,
                "Elevated error ratio", "Error ratio 0.12", "error_ratio", 1
        ));

        when(findingRepository.findByDeploymentId(9L)).thenReturn(List.of(finding));

        RootCauseAnalysisResult result = service.analyzeIncident(incident);

        assertThat(result.probableCauses())
                .anyMatch(c -> "SECURITY_FINDINGS".equals(c.category()));
        assertThat(result.probableCauses())
                .anyMatch(c -> "DEPLOYMENT_REGRESSION".equals(c.category()));
        assertThat(result.limitations())
                .anyMatch(l -> l.toLowerCase().contains("security"));
    }

    @Test
    void analyze_loadsIncidentFromRepository() {
        Instant at = Instant.parse("2026-08-18T16:00:00Z");
        Incident incident = new Incident("t", IncidentSeverity.LOW, at);
        setId(incident, 10L);
        when(incidentRepository.findByIdWithTimeline(10L)).thenReturn(Optional.of(incident));

        RootCauseAnalysisResult result = service.analyze(10L);

        assertThat(result.incidentId()).isEqualTo(10L);
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
