package com.opsvision.observability.mapper;

import com.opsvision.observability.dto.TelemetryResponse;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.ServiceMetricsSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import org.springframework.stereotype.Component;

@Component
public class TelemetryMapper {

    public TelemetryResponse toResponse(TelemetrySnapshot snapshot) {
        ServiceMetricsSnapshot m = snapshot.metrics();
        return new TelemetryResponse(
                snapshot.namespace(),
                snapshot.workloadName(),
                snapshot.collectedAt(),
                snapshot.kubernetesAvailable(),
                snapshot.prometheusAvailable(),
                snapshot.totalPodRestarts(),
                snapshot.workloads().stream().map(this::workload).toList(),
                snapshot.pods().stream().map(this::pod).toList(),
                snapshot.events().stream().map(this::event).toList(),
                new TelemetryResponse.MetricsDto(
                        m.requestRatePerSecond(),
                        m.errorRatePerSecond(),
                        m.errorRatio(),
                        m.latencyP50Seconds(),
                        m.latencyP95Seconds(),
                        m.latencyP99Seconds(),
                        m.cpuCores(),
                        m.memoryBytes(),
                        m.availabilityRatio()
                ),
                snapshot.notes()
        );
    }

    private TelemetryResponse.WorkloadDto workload(WorkloadSnapshot w) {
        return new TelemetryResponse.WorkloadDto(
                w.name(), w.namespace(), w.kind(),
                w.desiredReplicas(), w.readyReplicas(), w.availableReplicas(),
                w.updatedReplicas(), w.unavailableReplicas(),
                w.rolloutStatus(), w.image(), w.isHealthy()
        );
    }

    private TelemetryResponse.PodDto pod(PodSnapshot p) {
        return new TelemetryResponse.PodDto(
                p.name(), p.namespace(), p.phase(), p.ready(),
                p.restartCount(), p.reason(), p.nodeName(), p.startTime()
        );
    }

    private TelemetryResponse.EventDto event(KubernetesEventSnapshot e) {
        return new TelemetryResponse.EventDto(
                e.name(), e.namespace(), e.type(), e.reason(), e.message(),
                e.involvedKind(), e.involvedName(), e.count(), e.lastTimestamp(),
                e.isWarning()
        );
    }
}
