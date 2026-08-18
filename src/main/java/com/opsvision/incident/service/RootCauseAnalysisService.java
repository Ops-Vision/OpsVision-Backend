package com.opsvision.incident.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.repository.FindingRepository;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.model.ProbableCause;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.model.TimelineSource;
import com.opsvision.incident.repository.IncidentRepository;
import com.opsvision.incident.exception.IncidentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Deterministic root-cause analysis by correlating incident timeline, linked deployment,
 * and pre-deploy findings. Does not call an LLM and does not invent missing signals.
 */
@Service
public class RootCauseAnalysisService {

    static final String METHOD = "deterministic-correlation";

    /** Window after deployment in which metric/pod signals strongly implicate the release. */
    private static final Duration DEPLOY_CORRELATION_WINDOW = Duration.ofMinutes(30);

    private final IncidentRepository incidentRepository;
    private final FindingRepository findingRepository;

    public RootCauseAnalysisService(
            IncidentRepository incidentRepository,
            FindingRepository findingRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional(readOnly = true)
    public RootCauseAnalysisResult analyze(Long incidentId) {
        Incident incident = incidentRepository.findByIdWithTimeline(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
        return analyzeIncident(incident);
    }

    /**
     * Analyze a loaded incident graph (timeline + optional deployment). Public for recovery and tests.
     */
    public RootCauseAnalysisResult analyzeIncident(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        Instant analyzedAt = Instant.now();
        List<IncidentTimelineEntry> timeline = incident.getTimelineEntries() != null
                ? List.copyOf(incident.getTimelineEntries())
                : List.of();

        List<String> limitations = new ArrayList<>();
        List<ProbableCause> causes = new ArrayList<>();

        Instant deployAt = findDeploymentTime(incident, timeline);
        Deployment deployment = incident.getDeployment();
        boolean hasDeployLink = deployment != null || hasTimelineType(timeline, TimelineEntryType.DEPLOYMENT);

        if (!hasDeployLink) {
            limitations.add("No linked deployment; temporal correlation to a release is limited");
        }
        if (timeline.isEmpty()) {
            limitations.add("Incident timeline is empty; RCA based only on incident metadata");
        }

        // --- Hypothesis: recent deployment introduced elevated errors / instability ---
        List<IncidentTimelineEntry> postDeploySymptoms = filterSymptomsAfter(timeline, deployAt);
        boolean hasMetricSymptom = postDeploySymptoms.stream()
                .anyMatch(e -> e.getEntryType() == TimelineEntryType.METRIC
                        || signalKeyStartsWith(e, "error_")
                        || signalKeyStartsWith(e, "availability"));
        boolean hasPodSymptom = postDeploySymptoms.stream()
                .anyMatch(e -> e.getEntryType() == TimelineEntryType.POD
                        || signalKeyStartsWith(e, "pod_"));
        boolean hasWorkloadSymptom = postDeploySymptoms.stream()
                .anyMatch(e -> e.getEntryType() == TimelineEntryType.WORKLOAD
                        || signalKeyStartsWith(e, "workload_"));

        if (hasDeployLink && (hasMetricSymptom || hasPodSymptom || hasWorkloadSymptom)) {
            List<String> evidence = new ArrayList<>();
            if (deployment != null) {
                evidence.add(String.format(
                        Locale.ROOT,
                        "Linked deployment id=%d commit=%s environment=%s",
                        deployment.getId() != null ? deployment.getId() : -1L,
                        nullToNa(deployment.getCommitSha()),
                        nullToNa(deployment.getEnvironment())
                ));
            }
            if (deployAt != null) {
                evidence.add("Deployment observed at " + deployAt);
            } else {
                evidence.add("Deployment is linked but exact deploy timestamp is unknown");
            }
            for (IncidentTimelineEntry e : postDeploySymptoms) {
                if (isSymptom(e)) {
                    evidence.add(formatEntry(e));
                }
            }
            double confidence = 0.55;
            if (deployAt != null && hasMetricSymptom) {
                confidence += 0.20;
            }
            if (hasPodSymptom) {
                confidence += 0.10;
            }
            if (hasWorkloadSymptom) {
                confidence += 0.08;
            }
            if (hasMetricSymptom && hasPodSymptom) {
                confidence += 0.05;
            }
            if (deployAt == null) {
                confidence -= 0.10;
            }
            String cause = hasMetricSymptom
                    ? "Recent deployment likely introduced elevated error rate or reduced availability"
                    : "Recent deployment likely contributed to workload/pod instability";
            causes.add(new ProbableCause(
                    cause,
                    confidence,
                    "DEPLOYMENT_REGRESSION",
                    evidence
            ));
        }

        // --- Hypothesis: Kubernetes rollout / unhealthy workload ---
        List<IncidentTimelineEntry> workloadEntries = timeline.stream()
                .filter(e -> e.getEntryType() == TimelineEntryType.WORKLOAD
                        || signalKeyStartsWith(e, "workload_"))
                .toList();
        if (!workloadEntries.isEmpty()) {
            List<String> evidence = workloadEntries.stream()
                    .map(this::formatEntry)
                    .collect(Collectors.toCollection(ArrayList::new));
            boolean zeroReady = workloadEntries.stream()
                    .anyMatch(e -> e.getDetail() != null
                            && e.getDetail().toLowerCase(Locale.ROOT).contains("ready=0"));
            double confidence = zeroReady ? 0.88 : 0.72;
            causes.add(new ProbableCause(
                    zeroReady
                            ? "Workload rollout left zero ready replicas"
                            : "Kubernetes workload rollout is unhealthy (unavailable replicas)",
                    confidence,
                    "WORKLOAD_ROLLOUT",
                    evidence
            ));
        }

        // --- Hypothesis: pod crash / restart loop ---
        List<IncidentTimelineEntry> podEntries = timeline.stream()
                .filter(e -> e.getEntryType() == TimelineEntryType.POD
                        || signalKeyStartsWith(e, "pod_"))
                .toList();
        List<IncidentTimelineEntry> crashishEvents = timeline.stream()
                .filter(e -> e.getEntryType() == TimelineEntryType.KUBERNETES_EVENT
                        || signalKeyStartsWith(e, "k8s_"))
                .filter(e -> mentionsCrash(e))
                .toList();
        if (!podEntries.isEmpty() || !crashishEvents.isEmpty()) {
            List<String> evidence = new ArrayList<>();
            podEntries.forEach(e -> evidence.add(formatEntry(e)));
            crashishEvents.forEach(e -> evidence.add(formatEntry(e)));
            boolean crashLoop = crashishEvents.stream().anyMatch(this::mentionsCrash);
            double confidence = crashLoop ? 0.84 : (!podEntries.isEmpty() ? 0.70 : 0.60);
            causes.add(new ProbableCause(
                    crashLoop
                            ? "Pods are crash-looping or repeatedly restarting"
                            : "Elevated pod restart count indicates runtime instability",
                    confidence,
                    "POD_INSTABILITY",
                    evidence
            ));
        }

        // --- Hypothesis: pure metric degradation without deploy / k8s structure ---
        List<IncidentTimelineEntry> metricEntries = timeline.stream()
                .filter(e -> e.getEntryType() == TimelineEntryType.METRIC
                        || signalKeyStartsWith(e, "error_")
                        || signalKeyStartsWith(e, "availability"))
                .toList();
        if (!metricEntries.isEmpty() && !hasDeployLink && workloadEntries.isEmpty() && podEntries.isEmpty()) {
            List<String> evidence = metricEntries.stream()
                    .map(this::formatEntry)
                    .collect(Collectors.toCollection(ArrayList::new));
            causes.add(new ProbableCause(
                    "Service metrics show degradation without a correlated deployment in this incident",
                    0.48,
                    "METRIC_DEGRADATION",
                    evidence
            ));
            limitations.add("Metrics-only signal; upstream dependency or external traffic shift cannot be ruled out");
        } else if (!metricEntries.isEmpty() && hasDeployLink
                && causes.stream().noneMatch(c -> "DEPLOYMENT_REGRESSION".equals(c.category()))) {
            // Metrics exist with deploy but earlier block didn't fire (edge) — still note metrics
            List<String> evidence = metricEntries.stream()
                    .map(this::formatEntry)
                    .collect(Collectors.toCollection(ArrayList::new));
            causes.add(new ProbableCause(
                    "Prometheus metrics indicate service degradation during the incident window",
                    0.50,
                    "METRIC_DEGRADATION",
                    evidence
            ));
        }

        // --- Hypothesis: K8s warning events (generic) ---
        List<IncidentTimelineEntry> warningEntries = timeline.stream()
                .filter(e -> e.getEntryType() == TimelineEntryType.KUBERNETES_EVENT
                        || signalKeyStartsWith(e, "k8s_"))
                .filter(e -> !mentionsCrash(e))
                .toList();
        if (!warningEntries.isEmpty() && crashishEvents.isEmpty()) {
            List<String> evidence = warningEntries.stream()
                    .map(this::formatEntry)
                    .collect(Collectors.toCollection(ArrayList::new));
            causes.add(new ProbableCause(
                    "Kubernetes warning events indicate cluster or scheduling pressure",
                    0.52,
                    "KUBERNETES_EVENTS",
                    evidence
            ));
        }

        // --- Hypothesis: pre-deploy critical/high security findings (contributing factor) ---
        if (deployment != null && deployment.getId() != null) {
            List<Finding> findings = findingRepository.findByDeploymentId(deployment.getId());
            List<Finding> severe = findings.stream()
                    .filter(f -> f.getSeverity() == FindingSeverity.CRITICAL
                            || f.getSeverity() == FindingSeverity.HIGH)
                    .toList();
            if (!severe.isEmpty()) {
                List<String> evidence = severe.stream()
                        .limit(8)
                        .map(f -> String.format(
                                Locale.ROOT,
                                "%s %s: %s",
                                f.getSeverity(),
                                f.getFindingType() != null ? f.getFindingType() : "FINDING",
                                f.getTitle() != null ? f.getTitle() : "untitled"
                        ))
                        .collect(Collectors.toCollection(ArrayList::new));
                evidence.add(0, "Findings associated with linked deployment (pre-runtime evidence)");
                boolean anyCritical = severe.stream()
                        .anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL);
                causes.add(new ProbableCause(
                        anyCritical
                                ? "Critical security findings on the deployed commit may have contributed to runtime risk"
                                : "High-severity security findings on the deployed commit are a contributing risk factor",
                        anyCritical ? 0.45 : 0.35,
                        "SECURITY_FINDINGS",
                        evidence
                ));
                limitations.add(
                        "Security findings are pre-deploy evidence; they do not prove runtime exploitation"
                );
            }
        } else if (deployment == null) {
            // already noted
        } else {
            limitations.add("Deployment id not persisted; findings could not be loaded");
        }

        // Fallback when nothing matched
        if (causes.isEmpty()) {
            List<String> evidence = new ArrayList<>();
            evidence.add("Incident title: " + nullToNa(incident.getTitle()));
            if (incident.getSummary() != null && !incident.getSummary().isBlank()) {
                evidence.add("Summary: " + incident.getSummary());
            }
            evidence.add("Severity: " + incident.getSeverity());
            evidence.add("Status: " + incident.getStatus());
            causes.add(new ProbableCause(
                    "Insufficient correlated signals to attribute a specific root cause",
                    0.20,
                    "INSUFFICIENT_DATA",
                    evidence
            ));
            limitations.add("Collect richer timeline signals or link a deployment and re-run RCA");
        }

        causes = causes.stream()
                .sorted(Comparator.comparingDouble(ProbableCause::confidence).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        String summary = buildSummary(incident, causes);

        return new RootCauseAnalysisResult(
                incident.getId() != null ? incident.getId() : -1L,
                analyzedAt,
                METHOD,
                summary,
                causes,
                limitations
        );
    }

    private static String buildSummary(Incident incident, List<ProbableCause> causes) {
        ProbableCause top = causes.getFirst();
        return String.format(
                Locale.ROOT,
                "RCA for incident '%s': top hypothesis '%s' (confidence=%.2f, category=%s) among %d candidate(s).",
                nullToNa(incident.getTitle()),
                top.cause(),
                top.confidence(),
                top.category(),
                causes.size()
        );
    }

    private Instant findDeploymentTime(Incident incident, List<IncidentTimelineEntry> timeline) {
        Instant fromTimeline = timeline.stream()
                .filter(e -> e.getEntryType() == TimelineEntryType.DEPLOYMENT
                        || e.getSource() == TimelineSource.DEPLOYMENT)
                .map(IncidentTimelineEntry::getOccurredAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (fromTimeline != null) {
            return fromTimeline;
        }
        Deployment d = incident.getDeployment();
        if (d != null) {
            if (d.getDeployedAt() != null) {
                return d.getDeployedAt();
            }
            return d.getCreatedAt();
        }
        return null;
    }

    private List<IncidentTimelineEntry> filterSymptomsAfter(
            List<IncidentTimelineEntry> timeline,
            Instant deployAt
    ) {
        return timeline.stream()
                .filter(this::isSymptom)
                .filter(e -> {
                    if (deployAt == null) {
                        return true;
                    }
                    Instant at = e.getOccurredAt();
                    if (at == null) {
                        return true;
                    }
                    // Include signals at/after deploy, and slightly before (clock skew) within 1 minute
                    if (at.isBefore(deployAt.minus(Duration.ofMinutes(1)))) {
                        return false;
                    }
                    // Prefer within correlation window; still include later symptoms with same incident
                    return !at.isAfter(deployAt.plus(DEPLOY_CORRELATION_WINDOW))
                            || at.isAfter(deployAt);
                })
                .toList();
    }

    private boolean isSymptom(IncidentTimelineEntry e) {
        TimelineEntryType t = e.getEntryType();
        return t == TimelineEntryType.METRIC
                || t == TimelineEntryType.POD
                || t == TimelineEntryType.WORKLOAD
                || t == TimelineEntryType.KUBERNETES_EVENT
                || t == TimelineEntryType.SIGNAL
                || signalKeyStartsWith(e, "error_")
                || signalKeyStartsWith(e, "availability")
                || signalKeyStartsWith(e, "pod_")
                || signalKeyStartsWith(e, "workload_")
                || signalKeyStartsWith(e, "k8s_");
    }

    private static boolean hasTimelineType(List<IncidentTimelineEntry> timeline, TimelineEntryType type) {
        return timeline.stream().anyMatch(e -> e.getEntryType() == type);
    }

    private static boolean signalKeyStartsWith(IncidentTimelineEntry e, String prefix) {
        String key = e.getSignalKey();
        return key != null && key.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private boolean mentionsCrash(IncidentTimelineEntry e) {
        String blob = ((e.getTitle() != null ? e.getTitle() : "") + " "
                + (e.getDetail() != null ? e.getDetail() : "") + " "
                + (e.getSignalKey() != null ? e.getSignalKey() : "")).toLowerCase(Locale.ROOT);
        return blob.contains("crashloop")
                || blob.contains("crash loop")
                || blob.contains("oomkilled")
                || blob.contains("oom killed")
                || blob.contains("backoff")
                || blob.contains("back-off");
    }

    private String formatEntry(IncidentTimelineEntry e) {
        String when = e.getOccurredAt() != null ? e.getOccurredAt().toString() : "unknown-time";
        String detail = e.getDetail() != null && !e.getDetail().isBlank()
                ? e.getDetail()
                : "";
        if (detail.isBlank()) {
            return when + " [" + e.getEntryType() + "] " + e.getTitle();
        }
        return when + " [" + e.getEntryType() + "] " + e.getTitle() + " — " + detail;
    }

    private static String nullToNa(String v) {
        return v == null || v.isBlank() ? "n/a" : v;
    }
}
