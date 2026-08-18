package com.opsvision.incident.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.model.DetectedSignal;
import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.model.TimelineSource;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.ServiceMetricsSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds a chronological incident timeline from deployment metadata and telemetry.
 */
@Component
public class IncidentTimelineBuilder {

    public List<IncidentTimelineEntry> build(
            Deployment deployment,
            TelemetrySnapshot telemetry,
            List<DetectedSignal> signals
    ) {
        List<Draft> drafts = new ArrayList<>();
        Instant collectedAt = telemetry != null && telemetry.collectedAt() != null
                ? telemetry.collectedAt()
                : Instant.now();

        if (deployment != null) {
            Instant deployedAt = deployment.getDeployedAt() != null
                    ? deployment.getDeployedAt()
                    : deployment.getCreatedAt() != null ? deployment.getCreatedAt() : collectedAt;
            String sha = shortSha(deployment.getCommitSha());
            drafts.add(new Draft(
                    deployedAt,
                    TimelineEntryType.DEPLOYMENT,
                    TimelineSource.DEPLOYMENT,
                    "Deployment " + (deployment.getStatus() != null ? deployment.getStatus().name() : "recorded"),
                    String.format(
                            Locale.ROOT,
                            "Deployment id=%s commit=%s branch=%s environment=%s",
                            deployment.getId(),
                            sha,
                            nullToDash(deployment.getBranch()),
                            nullToDash(deployment.getEnvironment())
                    ),
                    "deployment",
                    0
            ));
        }

        if (telemetry != null) {
            for (WorkloadSnapshot w : telemetry.workloads()) {
                drafts.add(new Draft(
                        collectedAt,
                        TimelineEntryType.WORKLOAD,
                        TimelineSource.KUBERNETES,
                        "Workload " + w.name() + " rollout " + w.rolloutStatus(),
                        String.format(
                                Locale.ROOT,
                                "kind=%s ready=%d/%d unavailable=%d image=%s",
                                w.kind(),
                                w.readyReplicas(),
                                w.desiredReplicas(),
                                w.unavailableReplicas(),
                                nullToDash(w.image())
                        ),
                        "workload:" + w.name(),
                        10
                ));
            }

            for (PodSnapshot pod : telemetry.pods()) {
                if (pod.restartCount() > 0
                        || !"Running".equalsIgnoreCase(pod.phase())
                        || (pod.ready() != null && pod.ready().startsWith("0/"))) {
                    Instant t = pod.startTime() != null ? pod.startTime() : collectedAt;
                    drafts.add(new Draft(
                            t,
                            TimelineEntryType.POD,
                            TimelineSource.KUBERNETES,
                            "Pod " + pod.name() + " phase=" + pod.phase()
                                    + " restarts=" + pod.restartCount(),
                            String.format(
                                    Locale.ROOT,
                                    "ready=%s reason=%s node=%s",
                                    nullToDash(pod.ready()),
                                    nullToDash(pod.reason()),
                                    nullToDash(pod.nodeName())
                            ),
                            "pod:" + pod.name(),
                            20
                    ));
                }
            }

            for (KubernetesEventSnapshot event : telemetry.events()) {
                if (!event.isWarning()) {
                    continue;
                }
                Instant t = event.lastTimestamp() != null ? event.lastTimestamp() : collectedAt;
                drafts.add(new Draft(
                        t,
                        TimelineEntryType.KUBERNETES_EVENT,
                        TimelineSource.KUBERNETES,
                        "Kubernetes warning: " + event.reason(),
                        truncate(event.message(), 4000),
                        "k8s-event:" + event.reason() + ":" + nullToDash(event.involvedName()),
                        30
                ));
            }

            ServiceMetricsSnapshot m = telemetry.metrics();
            if (m != null && m.hasData()) {
                drafts.add(new Draft(
                        collectedAt,
                        TimelineEntryType.METRIC,
                        TimelineSource.PROMETHEUS,
                        "Service metrics snapshot",
                        formatMetrics(m),
                        "metrics",
                        40
                ));
            }

            if (telemetry.notes() != null) {
                for (String note : telemetry.notes()) {
                    if (note == null || note.isBlank()) {
                        continue;
                    }
                    drafts.add(new Draft(
                            collectedAt,
                            TimelineEntryType.NOTE,
                            TimelineSource.SYSTEM,
                            "Collection note",
                            note,
                            "note",
                            90
                    ));
                }
            }
        }

        if (signals != null) {
            for (DetectedSignal signal : signals) {
                drafts.add(new Draft(
                        signal.occurredAt(),
                        TimelineEntryType.SIGNAL,
                        signal.source(),
                        signal.title(),
                        signal.detail(),
                        signal.key(),
                        50
                ));
            }
        }

        drafts.sort(Comparator
                .comparing(Draft::occurredAt)
                .thenComparingInt(Draft::sortOrder)
                .thenComparing(Draft::title));

        List<IncidentTimelineEntry> entries = new ArrayList<>(drafts.size());
        int order = 0;
        for (Draft d : drafts) {
            entries.add(new IncidentTimelineEntry(
                    d.occurredAt(),
                    d.entryType(),
                    d.source(),
                    d.title(),
                    d.detail(),
                    d.signalKey(),
                    order++
            ));
        }
        return entries;
    }

    private static String formatMetrics(ServiceMetricsSnapshot m) {
        return String.format(
                Locale.ROOT,
                "requestRate=%s/s errorRate=%s/s errorRatio=%s p95=%ss availability=%s cpu=%s memory=%s",
                fmt(m.requestRatePerSecond()),
                fmt(m.errorRatePerSecond()),
                fmt(m.errorRatio()),
                fmt(m.latencyP95Seconds()),
                fmt(m.availabilityRatio()),
                fmt(m.cpuCores()),
                fmt(m.memoryBytes())
        );
    }

    private static String fmt(Double v) {
        if (v == null) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String shortSha(String sha) {
        if (sha == null || sha.isBlank()) {
            return "n/a";
        }
        return sha.length() <= 12 ? sha : sha.substring(0, 12);
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 3) + "...";
    }

    private record Draft(
            Instant occurredAt,
            TimelineEntryType entryType,
            TimelineSource source,
            String title,
            String detail,
            String signalKey,
            int sortOrder
    ) {
    }
}
