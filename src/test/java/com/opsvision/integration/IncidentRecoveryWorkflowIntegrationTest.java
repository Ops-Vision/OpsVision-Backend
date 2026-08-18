package com.opsvision.integration;

import com.opsvision.deployment.dto.AnalyzeDeploymentRequest;
import com.opsvision.deployment.dto.DeploymentAnalysisResponse;
import com.opsvision.deployment.service.DeploymentAnalysisService;
import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.service.IncidentDetectionService;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.ServiceMetricsSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import com.opsvision.postmortem.model.PostmortemResult;
import com.opsvision.postmortem.service.PostmortemService;
import com.opsvision.recovery.model.RecoveryAction;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
import com.opsvision.recovery.service.RecoveryRecommendationService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critical path: deployment → telemetry → incident → RCA → recovery (+ postmortem draft).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
@Transactional
class IncidentRecoveryWorkflowIntegrationTest {

    @Autowired
    private DeploymentAnalysisService analysisService;

    @Autowired
    private IncidentDetectionService incidentDetectionService;

    @Autowired
    private RootCauseAnalysisService rootCauseAnalysisService;

    @Autowired
    private RecoveryRecommendationService recoveryRecommendationService;

    @Autowired
    private PostmortemService postmortemService;

    @Test
    void elevatedErrorsAfterDeploy_opensIncident_withRcaRecoveryAndPostmortem() {
        DeploymentAnalysisResponse deployment = analysisService.analyze(new AnalyzeDeploymentRequest(
                "opsvision",
                "demo-app",
                "def444inc00000000000000000000000000004",
                "main",
                "production",
                "deploy.yml",
                2002L,
                null,
                null,
                List.of(
                        NormalizedEvidenceInput.builder(EvidenceType.BUILD, EvidenceStatus.PASSED, "ci")
                                .summary("ok")
                                .build(),
                        NormalizedEvidenceInput.builder(EvidenceType.TEST, EvidenceStatus.PASSED, "ci")
                                .summary("ok")
                                .build()
                )
        ));

        Instant t0 = Instant.parse("2026-03-01T10:00:00Z");
        TelemetrySnapshot telemetry = unhealthyTelemetry(t0);

        Optional<Incident> opened = incidentDetectionService.detectFromTelemetry(
                deployment.deployment().id(),
                telemetry
        );

        assertThat(opened).isPresent();
        Incident incident = opened.get();
        assertThat(incident.getId()).isNotNull();
        assertThat(incident.getDeployment()).isNotNull();
        assertThat(incident.getDeployment().getId()).isEqualTo(deployment.deployment().id());
        assertThat(incident.getTimelineEntries()).isNotEmpty();

        Incident loaded = incidentDetectionService.getById(incident.getId());
        RootCauseAnalysisResult rca = rootCauseAnalysisService.analyzeIncident(loaded);
        assertThat(rca.probableCauses()).isNotEmpty();
        assertThat(rca.probableCauses().getFirst().confidence()).isGreaterThan(0.0);

        RecoveryRecommendationResult recovery = recoveryRecommendationService.recommend(incident.getId());
        assertThat(recovery.action()).isNotNull();
        assertThat(recovery.action()).isNotEqualTo(RecoveryAction.NO_ACTION);
        assertThat(recovery.reason()).isNotBlank();
        assertThat(recovery.requiresHumanApproval()).isTrue();

        PostmortemResult postmortem = postmortemService.generate(incident.getId());
        assertThat(postmortem.incidentId()).isEqualTo(incident.getId());
        assertThat(postmortem.markdownBody()).contains("Postmortem");
        assertThat(postmortem.rootCauseSummary()).isNotBlank();
        assertThat(postmortem.recommendedRecoveryAction()).isEqualTo(recovery.action());
        assertThat(postmortem.limitations()).isNotEmpty();
    }

    @Test
    void healthyTelemetry_doesNotOpenIncident() {
        DeploymentAnalysisResponse deployment = analysisService.analyze(new AnalyzeDeploymentRequest(
                "opsvision",
                "demo-app",
                "def555healthy0000000000000000000000005",
                "main",
                "production",
                null,
                null,
                null,
                null,
                List.of()
        ));

        Instant at = Instant.parse("2026-03-01T11:00:00Z");
        TelemetrySnapshot healthy = new TelemetrySnapshot(
                "prod",
                "api",
                at,
                List.of(new WorkloadSnapshot("api", "prod", "Deployment", 2, 2, 2, 2, 0, "Complete", "img:v1")),
                List.of(new PodSnapshot("api-1", "prod", "Running", "True", 0, null, "node-a", at)),
                List.of(),
                new ServiceMetricsSnapshot(10.0, 0.01, 0.001, 0.05, 0.1, 0.2, 0.2, 1e8, 0.999, List.of()),
                true,
                true,
                List.of()
        );

        assertThat(incidentDetectionService.detectFromTelemetry(deployment.deployment().id(), healthy))
                .isEmpty();
    }

    private static TelemetrySnapshot unhealthyTelemetry(Instant at) {
        return new TelemetrySnapshot(
                "prod",
                "api",
                at.plusSeconds(360),
                List.of(new WorkloadSnapshot(
                        "api", "prod", "Deployment",
                        3, 1, 1, 3, 2, "Progressing", "img:v2"
                )),
                List.of(
                        new PodSnapshot("api-a", "prod", "Running", "True", 4, null, "n1", at.plusSeconds(60)),
                        new PodSnapshot("api-b", "prod", "CrashLoopBackOff", "False", 6, "CrashLoopBackOff", "n2", at.plusSeconds(90))
                ),
                List.of(new KubernetesEventSnapshot(
                        "ev-1",
                        "prod",
                        "Warning",
                        "BackOff",
                        "Back-off restarting failed container",
                        "Pod",
                        "api-b",
                        3,
                        at.plusSeconds(300)
                )),
                new ServiceMetricsSnapshot(
                        50.0,
                        9.0,
                        0.18,
                        0.2,
                        1.5,
                        3.0,
                        1.2,
                        5e8,
                        0.82,
                        List.of()
                ),
                true,
                true,
                List.of("synthetic unhealthy snapshot for integration test")
        );
    }
}
