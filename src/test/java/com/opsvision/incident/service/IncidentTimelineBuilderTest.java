package com.opsvision.incident.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.model.DetectedSignal;
import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.model.TimelineSource;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.ServiceMetricsSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentTimelineBuilderTest {

    private final IncidentTimelineBuilder builder = new IncidentTimelineBuilder();

    @Test
    void build_ordersDeploymentBeforeLaterSignals() {
        Instant deployTime = Instant.parse("2026-08-18T10:04:00Z");
        Instant metricTime = Instant.parse("2026-08-18T10:06:00Z");
        Instant restartTime = Instant.parse("2026-08-18T10:07:00Z");
        Instant warnTime = Instant.parse("2026-08-18T10:08:00Z");

        ProjectRepository repo = new ProjectRepository("acme", "svc", "main", "https://github.com/acme/svc");
        Deployment deployment = new Deployment(repo, "abc123", "main", "prod", DeploymentStatus.SUCCEEDED);
        deployment.setDeployedAt(deployTime);

        TelemetrySnapshot telemetry = new TelemetrySnapshot(
                "prod",
                "svc",
                metricTime,
                List.of(),
                List.of(new PodSnapshot("svc-1", "prod", "Running", "1/1", 2, null, "n", restartTime)),
                List.of(new KubernetesEventSnapshot(
                        "e", "prod", "Warning", "Unhealthy", "probe failed", "Pod", "svc-1", 1, warnTime
                )),
                new ServiceMetricsSnapshot(10.0, 2.0, 0.2, null, 0.5, null, null, null, null, List.of()),
                true,
                true,
                List.of()
        );

        List<DetectedSignal> signals = List.of(
                new DetectedSignal(
                        "error_ratio",
                        TimelineEntryType.METRIC,
                        TimelineSource.PROMETHEUS,
                        IncidentSeverity.HIGH,
                        metricTime,
                        "Elevated error ratio",
                        "Error rate increased from 1% to 18%"
                )
        );

        List<IncidentTimelineEntry> timeline = builder.build(deployment, telemetry, signals);

        assertThat(timeline).isNotEmpty();
        assertThat(timeline.getFirst().getEntryType()).isEqualTo(TimelineEntryType.DEPLOYMENT);
        assertThat(timeline.getFirst().getOccurredAt()).isEqualTo(deployTime);
        assertThat(timeline.stream().map(IncidentTimelineEntry::getTitle).toList())
                .anyMatch(t -> t.contains("error ratio") || t.contains("Error ratio") || t.contains("Elevated"));
        // sort_order is sequential after sort
        for (int i = 0; i < timeline.size(); i++) {
            assertThat(timeline.get(i).getSortOrder()).isEqualTo(i);
        }
    }
}
