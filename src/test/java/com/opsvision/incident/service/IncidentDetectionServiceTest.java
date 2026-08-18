package com.opsvision.incident.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.deployment.repository.DeploymentRepository;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.incident.config.IncidentProperties;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.model.IncidentStatus;
import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.repository.IncidentRepository;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.ServiceMetricsSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import com.opsvision.observability.service.TelemetryCollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentDetectionServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private TelemetryCollectionService telemetryCollectionService;

    private IncidentDetectionService service;
    private IncidentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new IncidentProperties();
        service = new IncidentDetectionService(
                incidentRepository,
                deploymentRepository,
                telemetryCollectionService,
                new IncidentTimelineBuilder(),
                properties
        );
    }

    @Test
    void detect_healthyTelemetry_returnsEmpty() {
        TelemetrySnapshot snap = healthySnapshot();

        Optional<Incident> result = service.detectFromTelemetry(null, snap);

        assertThat(result).isEmpty();
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void detect_elevatedErrorRatio_opensIncidentWithTimeline() {
        Instant t0 = Instant.parse("2026-08-18T10:04:00Z");
        Instant t1 = Instant.parse("2026-08-18T10:06:00Z");
        Instant t2 = Instant.parse("2026-08-18T10:07:00Z");
        Instant t3 = Instant.parse("2026-08-18T10:08:00Z");

        ProjectRepository repo = new ProjectRepository("acme", "api", "main", "https://github.com/acme/api");
        Deployment deployment = new Deployment(repo, "abcdef1234567890", "main", "prod", DeploymentStatus.SUCCEEDED);
        // simulate persisted id via reflection-free stub: mock findById
        when(deploymentRepository.findById(1L)).thenReturn(Optional.of(deployment));

        ServiceMetricsSnapshot metrics = new ServiceMetricsSnapshot(
                100.0, 18.0, 0.18, 0.05, 0.4, 0.8, 0.5, 1e9, 0.99, List.of()
        );
        TelemetrySnapshot snap = new TelemetrySnapshot(
                "prod",
                "api",
                t3,
                List.of(new WorkloadSnapshot("api", "prod", "Deployment", 3, 3, 3, 3, 0, "Complete", "img:v2")),
                List.of(new PodSnapshot("api-1", "prod", "Running", "1/1", 4, null, "node-a", t2)),
                List.of(new KubernetesEventSnapshot(
                        "ev1", "prod", "Warning", "BackOff", "Back-off restarting failed container",
                        "Pod", "api-1", 2, t3
                )),
                metrics,
                true,
                true,
                List.of()
        );

        when(incidentRepository.findOpenMatching(anyList(), eq("prod"), eq("api"), isNull()))
                .thenReturn(List.of());
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Incident> result = service.detectFromTelemetry(1L, snap);

        assertThat(result).isPresent();
        Incident incident = result.get();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getSeverity()).isIn(IncidentSeverity.HIGH, IncidentSeverity.CRITICAL);
        assertThat(incident.getTitle()).contains("api");
        assertThat(incident.getTimelineEntries()).isNotEmpty();
        assertThat(incident.getTimelineEntries().stream().map(e -> e.getEntryType()).toList())
                .contains(TimelineEntryType.DEPLOYMENT, TimelineEntryType.SIGNAL, TimelineEntryType.METRIC);

        // chronological-ish: deployment at createdAt may be null — still has entries
        assertThat(incident.getTimelineEntries().getFirst().getSortOrder()).isEqualTo(0);
    }

    @Test
    void detect_noTelemetryAvailable_skipsWhenRequired() {
        TelemetrySnapshot snap = new TelemetrySnapshot(
                "default", null, Instant.now(),
                List.of(), List.of(), List.of(),
                ServiceMetricsSnapshot.empty(),
                false, false,
                List.of("disabled")
        );

        assertThat(service.detectFromTelemetry(null, snap)).isEmpty();
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void detect_missingDeployment_throws() {
        when(deploymentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detectFromTelemetry(99L, healthySnapshot()))
                .isInstanceOf(DeploymentNotFoundException.class);
    }

    @Test
    void getById_missing_throws() {
        when(incidentRepository.findByIdWithTimeline(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(5L))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void collectSignals_unhealthyWorkload_andWarnings() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        TelemetrySnapshot snap = new TelemetrySnapshot(
                "prod",
                "api",
                now,
                List.of(new WorkloadSnapshot("api", "prod", "Deployment", 3, 0, 0, 1, 3, "Progressing", "img:v2")),
                List.of(),
                List.of(new KubernetesEventSnapshot(
                        "e", "prod", "Warning", "Failed", "fail", "Pod", "x", 1, now
                )),
                ServiceMetricsSnapshot.empty(),
                true,
                true,
                List.of()
        );

        var signals = service.collectSignals(snap);
        assertThat(signals).isNotEmpty();
        List<String> keys = signals.stream().map(s -> s.key()).toList();
        assertThat(keys).anyMatch(k -> k.startsWith("workload_unhealthy"));
        assertThat(keys).contains("k8s_warnings");
    }

    @Test
    void detectFromLiveTelemetry_delegatesToCollector() {
        when(telemetryCollectionService.collect("ns", "wl")).thenReturn(healthySnapshot("ns", "wl"));
        assertThat(service.detectFromLiveTelemetry(null, "ns", "wl")).isEmpty();
        verify(telemetryCollectionService).collect("ns", "wl");
    }

    @Test
    void detect_updatesExistingOpenIncident() {
        Instant now = Instant.now();
        Incident existing = new Incident("old", IncidentSeverity.MEDIUM, now.minusSeconds(60));
        existing.setNamespace("prod");
        existing.setWorkloadName("api");

        ServiceMetricsSnapshot metrics = new ServiceMetricsSnapshot(
                10.0, 2.0, 0.2, null, null, null, null, null, null, List.of()
        );
        TelemetrySnapshot snap = new TelemetrySnapshot(
                "prod", "api", now,
                List.of(), List.of(), List.of(),
                metrics, true, true, List.of()
        );

        when(incidentRepository.findOpenMatching(anyList(), eq("prod"), eq("api"), isNull()))
                .thenReturn(List.of(existing));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Incident> result = service.detectFromTelemetry(null, snap);
        assertThat(result).isPresent();
        assertThat(result.get().getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(result.get().getTimelineEntries()).isNotEmpty();
    }

    private static TelemetrySnapshot healthySnapshot() {
        return healthySnapshot("prod", "api");
    }

    private static TelemetrySnapshot healthySnapshot(String ns, String wl) {
        ServiceMetricsSnapshot metrics = new ServiceMetricsSnapshot(
                10.0, 0.01, 0.001, 0.05, 0.1, 0.2, 0.1, 1e8, 1.0, List.of()
        );
        return new TelemetrySnapshot(
                ns, wl, Instant.parse("2026-08-18T12:00:00Z"),
                List.of(new WorkloadSnapshot(wl != null ? wl : "api", ns, "Deployment", 2, 2, 2, 2, 0, "Complete", "img")),
                List.of(new PodSnapshot("p1", ns, "Running", "1/1", 0, null, "n1", Instant.parse("2026-08-18T11:00:00Z"))),
                List.of(),
                metrics,
                true, true, List.of()
        );
    }
}
