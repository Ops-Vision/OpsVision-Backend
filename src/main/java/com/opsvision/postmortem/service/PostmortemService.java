package com.opsvision.postmortem.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.model.ProbableCause;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.repository.IncidentRepository;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.postmortem.model.PostmortemResult;
import com.opsvision.postmortem.model.PostmortemResult.TimelineHighlight;
import com.opsvision.recovery.model.RecoveryAction;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
import com.opsvision.recovery.service.RecoveryRecommendationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds a blameless postmortem from deterministic incident, RCA, and recovery data.
 * Does not call an LLM and does not invent metrics or findings.
 */
@Service
public class PostmortemService {

    private static final int MAX_TIMELINE_HIGHLIGHTS = 15;
    private static final int MAX_DETAIL_LEN = 280;

    private final IncidentRepository incidentRepository;
    private final RootCauseAnalysisService rootCauseAnalysisService;
    private final RecoveryRecommendationService recoveryRecommendationService;

    public PostmortemService(
            IncidentRepository incidentRepository,
            RootCauseAnalysisService rootCauseAnalysisService,
            RecoveryRecommendationService recoveryRecommendationService
    ) {
        this.incidentRepository = incidentRepository;
        this.rootCauseAnalysisService = rootCauseAnalysisService;
        this.recoveryRecommendationService = recoveryRecommendationService;
    }

    @Transactional(readOnly = true)
    public PostmortemResult generate(Long incidentId) {
        Incident incident = incidentRepository.findByIdWithTimeline(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
        RootCauseAnalysisResult rca = rootCauseAnalysisService.analyzeIncident(incident);
        RecoveryRecommendationResult recovery = recoveryRecommendationService.recommendFrom(incident, rca);
        return generateFrom(incident, rca, recovery);
    }

    /**
     * Shared entry for callers that already hold RCA/recovery (tests, future integrations).
     */
    public PostmortemResult generateFrom(
            Incident incident,
            RootCauseAnalysisResult rca,
            RecoveryRecommendationResult recovery
    ) {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(rca, "rca");
        Objects.requireNonNull(recovery, "recovery");

        Instant generatedAt = Instant.now();
        ProbableCause top = rca.probableCauses().isEmpty() ? null : rca.probableCauses().getFirst();
        String topCategory = top != null ? top.category() : "INSUFFICIENT_DATA";
        double topConfidence = top != null ? top.confidence() : 0.0;

        List<TimelineHighlight> highlights = buildTimelineHighlights(incident);
        List<String> contributing = buildContributingFactors(rca, top);
        List<String> wentWell = buildWhatWentWell(incident, rca, recovery);
        List<String> wentWrong = buildWhatWentWrong(incident, top, recovery);
        List<String> actions = buildActionItems(incident, top, recovery);
        List<String> limitations = new ArrayList<>(rca.limitations());
        limitations.add("Postmortem is generated from structured OpsVision data only; "
                + "human review is required before publishing");

        Long durationMinutes = computeDurationMinutes(incident);
        String impact = buildImpactSummary(incident, durationMinutes);
        String executive = buildExecutiveSummary(incident, top, recovery, durationMinutes);
        String rootCauseSummary = top != null
                ? top.cause()
                : "Insufficient correlated signals to attribute a specific root cause";
        String title = buildTitle(incident);

        String markdown = buildMarkdown(
                incident,
                title,
                executive,
                impact,
                durationMinutes,
                rootCauseSummary,
                topCategory,
                topConfidence,
                contributing,
                highlights,
                wentWell,
                wentWrong,
                actions,
                recovery,
                limitations,
                rca
        );

        Deployment deployment = incident.getDeployment();
        Long deploymentId = deployment != null ? deployment.getId() : null;
        String commit = incident.getCommitSha();
        if (!StringUtils.hasText(commit) && deployment != null) {
            commit = deployment.getCommitSha();
        }

        return new PostmortemResult(
                incident.getId() != null ? incident.getId() : -1L,
                generatedAt,
                PostmortemResult.METHOD,
                title,
                executive,
                impact,
                incident.getSeverity() != null ? incident.getSeverity().name() : null,
                incident.getStatus() != null ? incident.getStatus().name() : null,
                incident.getEnvironment(),
                incident.getNamespace(),
                incident.getWorkloadName(),
                commit,
                deploymentId,
                incident.getDetectedAt(),
                incident.getStartedAt(),
                incident.getResolvedAt(),
                durationMinutes,
                rootCauseSummary,
                topCategory,
                topConfidence,
                contributing,
                highlights,
                wentWell,
                wentWrong,
                actions,
                recovery.action(),
                recovery.reason(),
                recovery.targetVersion(),
                recovery.requiresHumanApproval(),
                limitations,
                markdown
        );
    }

    private static String buildTitle(Incident incident) {
        String severity = incident.getSeverity() != null ? incident.getSeverity().name() : "UNKNOWN";
        String base = StringUtils.hasText(incident.getTitle()) ? incident.getTitle() : "Untitled incident";
        return "Postmortem: [" + severity + "] " + base;
    }

    private static String buildExecutiveSummary(
            Incident incident,
            ProbableCause top,
            RecoveryRecommendationResult recovery,
            Long durationMinutes
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Incident '")
                .append(nullToNa(incident.getTitle()))
                .append("' (")
                .append(incident.getSeverity())
                .append(", ")
                .append(incident.getStatus())
                .append(")");
        if (durationMinutes != null) {
            sb.append(" lasted approximately ").append(durationMinutes).append(" minute(s)");
        }
        sb.append(". ");
        if (top != null) {
            sb.append("Primary hypothesis: ").append(top.cause())
                    .append(" (").append(top.category())
                    .append(", confidence=").append(String.format(Locale.ROOT, "%.2f", top.confidence()))
                    .append("). ");
        }
        if (recovery != null && recovery.action() != null) {
            sb.append("Recommended recovery posture: ").append(recovery.action()).append(".");
        }
        return sb.toString();
    }

    private static String buildImpactSummary(Incident incident, Long durationMinutes) {
        List<String> parts = new ArrayList<>();
        parts.add("Severity " + incident.getSeverity());
        if (StringUtils.hasText(incident.getEnvironment())) {
            parts.add("environment " + incident.getEnvironment());
        }
        if (StringUtils.hasText(incident.getNamespace())) {
            parts.add("namespace " + incident.getNamespace());
        }
        if (StringUtils.hasText(incident.getWorkloadName())) {
            parts.add("workload " + incident.getWorkloadName());
        }
        if (durationMinutes != null) {
            parts.add("approx. duration " + durationMinutes + "m");
        } else if (incident.getDetectedAt() != null) {
            parts.add("detected at " + incident.getDetectedAt());
        }
        if (StringUtils.hasText(incident.getSummary())) {
            return String.join("; ", parts) + ". " + incident.getSummary();
        }
        return String.join("; ", parts) + ".";
    }

    private static Long computeDurationMinutes(Incident incident) {
        Instant start = incident.getStartedAt() != null ? incident.getStartedAt() : incident.getDetectedAt();
        Instant end = incident.getResolvedAt();
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        return Duration.between(start, end).toMinutes();
    }

    private static List<TimelineHighlight> buildTimelineHighlights(Incident incident) {
        List<IncidentTimelineEntry> entries = incident.getTimelineEntries();
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .limit(MAX_TIMELINE_HIGHLIGHTS)
                .map(e -> new TimelineHighlight(
                        e.getOccurredAt(),
                        e.getEntryType() != null ? e.getEntryType().name() : "UNKNOWN",
                        e.getTitle() != null ? e.getTitle() : "",
                        truncate(e.getDetail(), MAX_DETAIL_LEN)
                ))
                .toList();
    }

    private static List<String> buildContributingFactors(RootCauseAnalysisResult rca, ProbableCause top) {
        List<String> factors = new ArrayList<>();
        if (top != null) {
            factors.add(top.category() + ": " + top.cause());
            top.evidence().stream().limit(5).forEach(factors::add);
        }
        rca.probableCauses().stream()
                .skip(1)
                .limit(3)
                .forEach(c -> factors.add("Alternate: " + c.category() + " — " + c.cause()));
        return factors;
    }

    private static List<String> buildWhatWentWell(
            Incident incident,
            RootCauseAnalysisResult rca,
            RecoveryRecommendationResult recovery
    ) {
        List<String> items = new ArrayList<>();
        if (incident.getTimelineEntries() != null && !incident.getTimelineEntries().isEmpty()) {
            items.add("Telemetry and deployment signals were correlated into a structured timeline");
        }
        if (!rca.probableCauses().isEmpty()
                && !"INSUFFICIENT_DATA".equals(rca.probableCauses().getFirst().category())) {
            items.add("Deterministic RCA produced ranked hypotheses with supporting evidence");
        }
        if (incident.getDeployment() != null) {
            items.add("Incident was linked to a concrete deployment for release correlation");
        }
        if (incident.getGithubIssueNumber() != null) {
            items.add("GitHub issue #" + incident.getGithubIssueNumber()
                    + " is linked for tracking and follow-up");
        }
        if (recovery.action() != null && recovery.action() != RecoveryAction.NO_ACTION) {
            items.add("A recovery recommendation was available without automatic destructive execution");
        }
        if (items.isEmpty()) {
            items.add("Incident record exists and can be enriched with additional telemetry");
        }
        return items;
    }

    private static List<String> buildWhatWentWrong(
            Incident incident,
            ProbableCause top,
            RecoveryRecommendationResult recovery
    ) {
        List<String> items = new ArrayList<>();
        if (top != null) {
            items.add(top.cause());
        }
        if (incident.getDeployment() == null) {
            items.add("No deployment was linked, limiting release-based correlation");
        }
        if (incident.getTimelineEntries() == null || incident.getTimelineEntries().isEmpty()) {
            items.add("Timeline was empty or sparse at generation time");
        }
        if (recovery.action() == RecoveryAction.ROLLBACK && recovery.targetVersion() == null) {
            items.add("Rollback was indicated but a prior stable version could not be resolved");
        }
        if (items.isEmpty()) {
            items.add("Customer-facing or operational impact still requires human validation");
        }
        return items;
    }

    private static List<String> buildActionItems(
            Incident incident,
            ProbableCause top,
            RecoveryRecommendationResult recovery
    ) {
        List<String> items = new ArrayList<>();
        String category = top != null ? top.category() : "INSUFFICIENT_DATA";

        if (recovery.action() == RecoveryAction.ROLLBACK) {
            items.add("If still impacting production, prepare human-approved rollback"
                    + (StringUtils.hasText(recovery.targetVersion())
                    ? " to " + shortSha(recovery.targetVersion())
                    : " to the last known-good version"));
        } else if (recovery.action() == RecoveryAction.RESTART) {
            items.add("Restart affected pods/workload after confirming probes and resource limits");
        } else if (recovery.action() == RecoveryAction.SCALE_UP) {
            items.add("Review capacity and scale replicas/nodes if scheduling pressure is confirmed");
        } else if (recovery.action() == RecoveryAction.INVESTIGATE) {
            items.add("Continue investigation with owners before applying destructive recovery");
        }

        switch (category) {
            case "DEPLOYMENT_REGRESSION" -> items.add(
                    "Add or tighten pre-deploy checks and canary/error-budget gates for similar changes"
            );
            case "WORKLOAD_ROLLOUT", "POD_INSTABILITY" -> items.add(
                    "Review readiness/liveness probes, resource requests, and rollout strategy"
            );
            case "KUBERNETES_EVENTS" -> items.add(
                    "Audit cluster capacity, quotas, and recent infrastructure changes"
            );
            case "METRIC_DEGRADATION" -> items.add(
                    "Trace dependency health and traffic patterns for the degraded SLIs"
            );
            case "SECURITY_FINDINGS" -> items.add(
                    "Triage HIGH/CRITICAL findings on the deployed commit and track remediation SLAs"
            );
            default -> items.add("Collect richer telemetry and re-run RCA before closing the incident");
        }

        items.add("Capture customer impact and communication timeline with on-call stakeholders");
        items.add("Schedule a short blameless review and assign owners to each action item");

        if (incident.getGithubIssueNumber() == null) {
            items.add("Open or link a tracking issue for durable follow-up");
        }
        return items.stream().distinct().toList();
    }

    private static String buildMarkdown(
            Incident incident,
            String title,
            String executive,
            String impact,
            Long durationMinutes,
            String rootCauseSummary,
            String topCategory,
            double topConfidence,
            List<String> contributing,
            List<TimelineHighlight> highlights,
            List<String> wentWell,
            List<String> wentWrong,
            List<String> actions,
            RecoveryRecommendationResult recovery,
            List<String> limitations,
            RootCauseAnalysisResult rca
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("_Generated by OpsVision (").append(PostmortemResult.METHOD)
                .append("). Blameless draft — human review required._\n\n");

        sb.append("## Executive summary\n\n").append(executive).append("\n\n");

        sb.append("## Impact\n\n").append(impact).append("\n");
        if (durationMinutes != null) {
            sb.append("- **Duration (minutes):** ").append(durationMinutes).append('\n');
        }
        if (incident.getDetectedAt() != null) {
            sb.append("- **Detected at:** ").append(incident.getDetectedAt()).append('\n');
        }
        if (incident.getResolvedAt() != null) {
            sb.append("- **Resolved at:** ").append(incident.getResolvedAt()).append('\n');
        }
        sb.append('\n');

        sb.append("## Root cause\n\n");
        sb.append("- **Summary:** ").append(rootCauseSummary).append('\n');
        sb.append("- **Category:** ").append(topCategory).append('\n');
        sb.append("- **Confidence:** ").append(String.format(Locale.ROOT, "%.2f", topConfidence)).append('\n');
        if (rca.summary() != null && !rca.summary().isBlank()) {
            sb.append("- **RCA summary:** ").append(rca.summary()).append('\n');
        }
        sb.append('\n');

        if (!contributing.isEmpty()) {
            sb.append("## Contributing factors\n\n");
            contributing.forEach(f -> sb.append("- ").append(f).append('\n'));
            sb.append('\n');
        }

        if (!highlights.isEmpty()) {
            sb.append("## Timeline highlights\n\n");
            for (TimelineHighlight h : highlights) {
                sb.append("- ");
                if (h.occurredAt() != null) {
                    sb.append(h.occurredAt()).append(' ');
                }
                sb.append("**").append(h.title()).append("**");
                if (StringUtils.hasText(h.detail())) {
                    sb.append(" — ").append(h.detail());
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        sb.append("## What went well\n\n");
        wentWell.forEach(i -> sb.append("- ").append(i).append('\n'));
        sb.append('\n');

        sb.append("## What went wrong\n\n");
        wentWrong.forEach(i -> sb.append("- ").append(i).append('\n'));
        sb.append('\n');

        sb.append("## Recommended recovery (not executed)\n\n");
        sb.append("- **Action:** ").append(recovery.action()).append('\n');
        sb.append("- **Reason:** ").append(recovery.reason()).append('\n');
        if (StringUtils.hasText(recovery.targetVersion())) {
            sb.append("- **Target version:** `").append(recovery.targetVersion()).append("`\n");
        }
        sb.append("- **Requires human approval:** ").append(recovery.requiresHumanApproval()).append('\n');
        sb.append('\n');

        sb.append("## Action items\n\n");
        actions.forEach(i -> sb.append("- [ ] ").append(i).append('\n'));
        sb.append('\n');

        if (!limitations.isEmpty()) {
            sb.append("## Limitations\n\n");
            limitations.forEach(l -> sb.append("- ").append(l).append('\n'));
            sb.append('\n');
        }

        sb.append("---\n");
        sb.append("opsvision-incident-id: ").append(incident.getId()).append('\n');
        return sb.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String shortSha(String sha) {
        if (sha == null) {
            return "unknown";
        }
        return sha.length() <= 8 ? sha : sha.substring(0, 8);
    }

    private static String nullToNa(String v) {
        return v == null || v.isBlank() ? "n/a" : v;
    }
}
