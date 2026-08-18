package com.opsvision.incident.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.repository.DeploymentRepository;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.incident.config.IncidentProperties;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.model.DetectedSignal;
import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.model.IncidentStatus;
import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.model.TimelineSource;
import com.opsvision.incident.repository.IncidentRepository;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.ServiceMetricsSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import com.opsvision.observability.service.TelemetryCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Deterministic incident detection from telemetry, correlated with optional deployment context.
 * Does not perform RCA or recovery (later steps).
 */
@Service
public class IncidentDetectionService {

    private static final Logger log = LoggerFactory.getLogger(IncidentDetectionService.class);

    private static final List<IncidentStatus> OPEN_STATUSES = List.copyOf(
            EnumSet.of(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING)
    );

    private final IncidentRepository incidentRepository;
    private final DeploymentRepository deploymentRepository;
    private final TelemetryCollectionService telemetryCollectionService;
    private final IncidentTimelineBuilder timelineBuilder;
    private final IncidentProperties properties;

    public IncidentDetectionService(
            IncidentRepository incidentRepository,
            DeploymentRepository deploymentRepository,
            TelemetryCollectionService telemetryCollectionService,
            IncidentTimelineBuilder timelineBuilder,
            IncidentProperties properties
    ) {
        this.incidentRepository = incidentRepository;
        this.deploymentRepository = deploymentRepository;
        this.telemetryCollectionService = telemetryCollectionService;
        this.timelineBuilder = timelineBuilder;
        this.properties = properties;
    }

    /**
     * Collect live telemetry and evaluate incident detection for an optional deployment.
     *
     * @return empty when no incident-worthy signals; otherwise persisted incident with timeline
     */
    @Transactional
    public Optional<Incident> detectFromLiveTelemetry(
            Long deploymentId,
            String namespace,
            String workload
    ) {
        Deployment deployment = null;
        if (deploymentId != null) {
            deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
        }

        TelemetrySnapshot telemetry = telemetryCollectionService.collect(namespace, workload);
        return evaluateAndPersist(deployment, telemetry);
    }

    /**
     * Evaluate a provided telemetry snapshot (used by tests and future batch jobs).
     */
    @Transactional
    public Optional<Incident> detectFromTelemetry(Long deploymentId, TelemetrySnapshot telemetry) {
        Deployment deployment = null;
        if (deploymentId != null) {
            deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
        }
        return evaluateAndPersist(deployment, telemetry);
    }

    @Transactional(readOnly = true)
    public Incident getById(Long id) {
        return incidentRepository.findByIdWithTimeline(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Incident> list(Pageable pageable) {
        return incidentRepository.findAllByOrderByDetectedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<Incident> listByDeployment(Long deploymentId) {
        if (!deploymentRepository.existsById(deploymentId)) {
            throw new DeploymentNotFoundException(deploymentId);
        }
        return incidentRepository.findByDeploymentIdOrderByDetectedAtDesc(deploymentId);
    }

    Optional<Incident> evaluateAndPersist(Deployment deployment, TelemetrySnapshot telemetry) {
        if (telemetry == null) {
            return Optional.empty();
        }

        if (properties.isRequireTelemetry()
                && !telemetry.kubernetesAvailable()
                && !telemetry.prometheusAvailable()) {
            log.debug("Skipping incident detection: no telemetry sources available");
            return Optional.empty();
        }

        List<DetectedSignal> signals = collectSignals(telemetry);
        if (signals.isEmpty()) {
            return Optional.empty();
        }

        Instant detectedAt = telemetry.collectedAt() != null ? telemetry.collectedAt() : Instant.now();
        IncidentSeverity severity = maxSeverity(signals);
        String title = buildTitle(telemetry, signals);
        String summary = buildSummary(signals);

        Long deploymentId = deployment != null ? deployment.getId() : null;
        List<Incident> existing = incidentRepository.findOpenMatching(
                OPEN_STATUSES,
                telemetry.namespace(),
                telemetry.workloadName(),
                deploymentId
        );

        Incident incident;
        if (!existing.isEmpty()) {
            incident = existing.getFirst();
            incident.setSeverity(maxOf(incident.getSeverity(), severity));
            incident.setTitle(title);
            incident.setSummary(summary);
            incident.setDetectedAt(detectedAt);
            incident.clearTimeline();
            log.info("Updating open incident id={} with {} signals", incident.getId(), signals.size());
        } else {
            incident = new Incident(title, severity, detectedAt);
            incident.setSummary(summary);
            incident.setNamespace(telemetry.namespace());
            incident.setWorkloadName(telemetry.workloadName());
            if (deployment != null) {
                incident.setDeployment(deployment);
                incident.setCommitSha(deployment.getCommitSha());
                incident.setEnvironment(deployment.getEnvironment());
            }
            log.info("Opening incident '{}' severity={} signals={}", title, severity, signals.size());
        }

        List<IncidentTimelineEntry> timeline = timelineBuilder.build(deployment, telemetry, signals);
        for (IncidentTimelineEntry entry : timeline) {
            incident.addTimelineEntry(entry);
        }

        Instant started = timeline.stream()
                .map(IncidentTimelineEntry::getOccurredAt)
                .min(Comparator.naturalOrder())
                .orElse(detectedAt);
        incident.setStartedAt(started);

        return Optional.of(incidentRepository.save(incident));
    }

    List<DetectedSignal> collectSignals(TelemetrySnapshot telemetry) {
        List<DetectedSignal> signals = new ArrayList<>();
        Instant at = telemetry.collectedAt() != null ? telemetry.collectedAt() : Instant.now();
        ServiceMetricsSnapshot metrics = telemetry.metrics();

        if (metrics != null) {
            if (metrics.errorRatio() != null
                    && metrics.errorRatio() >= properties.getErrorRatioThreshold()) {
                signals.add(new DetectedSignal(
                        "error_ratio",
                        TimelineEntryType.METRIC,
                        TimelineSource.PROMETHEUS,
                        metrics.errorRatio() >= 0.2 ? IncidentSeverity.CRITICAL : IncidentSeverity.HIGH,
                        at,
                        "Elevated error ratio",
                        String.format(
                                Locale.ROOT,
                                "Error ratio %.4f exceeds threshold %.4f",
                                metrics.errorRatio(),
                                properties.getErrorRatioThreshold()
                        )
                ));
            } else if (metrics.errorRatePerSecond() != null
                    && metrics.errorRatePerSecond() >= properties.getErrorRateThreshold()) {
                signals.add(new DetectedSignal(
                        "error_rate",
                        TimelineEntryType.METRIC,
                        TimelineSource.PROMETHEUS,
                        IncidentSeverity.HIGH,
                        at,
                        "Elevated error rate",
                        String.format(
                                Locale.ROOT,
                                "Error rate %.4f/s exceeds threshold %.4f/s",
                                metrics.errorRatePerSecond(),
                                properties.getErrorRateThreshold()
                        )
                ));
            }

            if (metrics.availabilityRatio() != null
                    && metrics.availabilityRatio() < properties.getAvailabilityMinRatio()) {
                signals.add(new DetectedSignal(
                        "availability",
                        TimelineEntryType.METRIC,
                        TimelineSource.PROMETHEUS,
                        metrics.availabilityRatio() < 0.5 ? IncidentSeverity.CRITICAL : IncidentSeverity.HIGH,
                        at,
                        "Low service availability",
                        String.format(
                                Locale.ROOT,
                                "Availability %.4f below minimum %.4f",
                                metrics.availabilityRatio(),
                                properties.getAvailabilityMinRatio()
                        )
                ));
            }
        }

        int restarts = telemetry.totalPodRestarts();
        if (restarts >= properties.getPodRestartThreshold()) {
            signals.add(new DetectedSignal(
                    "pod_restarts",
                    TimelineEntryType.POD,
                    TimelineSource.KUBERNETES,
                    restarts >= properties.getPodRestartThreshold() * 3
                            ? IncidentSeverity.CRITICAL
                            : IncidentSeverity.HIGH,
                    at,
                    "Elevated pod restart count",
                    String.format(
                            Locale.ROOT,
                            "Total pod restarts=%d (threshold=%d)",
                            restarts,
                            properties.getPodRestartThreshold()
                    )
            ));
        }

        List<KubernetesEventSnapshot> warnings = telemetry.warningEvents();
        if (warnings.size() >= properties.getWarningEventThreshold()) {
            String reasons = warnings.stream()
                    .map(KubernetesEventSnapshot::reason)
                    .distinct()
                    .limit(5)
                    .collect(Collectors.joining(", "));
            signals.add(new DetectedSignal(
                    "k8s_warnings",
                    TimelineEntryType.KUBERNETES_EVENT,
                    TimelineSource.KUBERNETES,
                    warnings.size() >= 5 ? IncidentSeverity.HIGH : IncidentSeverity.MEDIUM,
                    at,
                    "Kubernetes warning events detected",
                    String.format(
                            Locale.ROOT,
                            "%d warning event(s); reasons: %s",
                            warnings.size(),
                            reasons.isBlank() ? "n/a" : reasons
                    )
            ));
        }

        if (properties.isDetectUnhealthyWorkload()) {
            for (WorkloadSnapshot w : telemetry.workloads()) {
                if (!w.isHealthy()) {
                    signals.add(new DetectedSignal(
                            "workload_unhealthy:" + w.name(),
                            TimelineEntryType.WORKLOAD,
                            TimelineSource.KUBERNETES,
                            w.readyReplicas() == 0 ? IncidentSeverity.CRITICAL : IncidentSeverity.HIGH,
                            at,
                            "Unhealthy workload rollout: " + w.name(),
                            String.format(
                                    Locale.ROOT,
                                    "ready=%d desired=%d unavailable=%d status=%s",
                                    w.readyReplicas(),
                                    w.desiredReplicas(),
                                    w.unavailableReplicas(),
                                    w.rolloutStatus()
                            )
                    ));
                }
            }
        }

        return signals;
    }

    private static IncidentSeverity maxSeverity(List<DetectedSignal> signals) {
        return signals.stream()
                .map(DetectedSignal::severity)
                .reduce(IncidentSeverity.INFO, IncidentDetectionService::maxOf);
    }

    private static IncidentSeverity maxOf(IncidentSeverity a, IncidentSeverity b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(IncidentSeverity s) {
        return switch (s) {
            case CRITICAL -> 5;
            case HIGH -> 4;
            case MEDIUM -> 3;
            case LOW -> 2;
            case INFO -> 1;
        };
    }

    private static String buildTitle(TelemetrySnapshot telemetry, List<DetectedSignal> signals) {
        String primary = signals.stream()
                .max(Comparator.comparingInt(s -> rank(s.severity())))
                .map(DetectedSignal::title)
                .orElse("Incident detected");
        String scope = telemetry.workloadName() != null && !telemetry.workloadName().isBlank()
                ? telemetry.workloadName()
                : telemetry.namespace() != null ? telemetry.namespace() : "cluster";
        return primary + " (" + scope + ")";
    }

    private static String buildSummary(List<DetectedSignal> signals) {
        return signals.stream()
                .map(s -> s.title() + ": " + s.detail())
                .collect(Collectors.joining("; "));
    }
}
