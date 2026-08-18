package com.opsvision.scoring.service;

import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.scoring.config.ScoringProperties;
import com.opsvision.scoring.model.ConfidenceScoreResult;
import com.opsvision.scoring.model.ScoreFactor;
import com.opsvision.scoring.model.ScoringEvidenceItem;
import com.opsvision.scoring.model.ScoringFindingItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Deterministic Deployment Confidence Score engine (0–100).
 * <p>
 * Score is the sum of weighted factor contributions derived only from normalized
 * evidence and findings — never from an LLM.
 */
@Service
public class DeploymentConfidenceScorer {

    public static final String FACTOR_BUILD = "build";
    public static final String FACTOR_TESTS = "tests";
    public static final String FACTOR_COVERAGE = "coverage";
    public static final String FACTOR_STATIC_ANALYSIS = "static_analysis";
    public static final String FACTOR_SECURITY = "security";
    public static final String FACTOR_WORKFLOW = "workflow";

    private static final EnumSet<EvidenceType> SECURITY_TYPES = EnumSet.of(
            EvidenceType.DEPENDENCY_SCAN,
            EvidenceType.CONTAINER_SCAN
    );

    private final ScoringProperties properties;

    public DeploymentConfidenceScorer(ScoringProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Compute confidence from a bag of evidence items (findings nested and/or top-level).
     */
    public ConfidenceScoreResult score(Collection<ScoringEvidenceItem> evidence) {
        return score(evidence, List.of());
    }

    /**
     * Compute confidence from evidence plus additional findings not nested under evidence.
     */
    public ConfidenceScoreResult score(
            Collection<ScoringEvidenceItem> evidence,
            Collection<ScoringFindingItem> additionalFindings
    ) {
        List<ScoringEvidenceItem> items = evidence == null
                ? List.of()
                : evidence.stream().filter(Objects::nonNull).toList();
        List<ScoringFindingItem> extraFindings = additionalFindings == null
                ? List.of()
                : additionalFindings.stream().filter(Objects::nonNull).toList();

        Map<EvidenceType, List<ScoringEvidenceItem>> byType = groupByType(items);

        List<ScoreFactor> factors = new ArrayList<>();
        factors.add(scoreStatusFactor(
                FACTOR_BUILD,
                properties.getBuildMax(),
                pickBest(byType.get(EvidenceType.BUILD)),
                "Build"
        ));
        factors.add(scoreStatusFactor(
                FACTOR_TESTS,
                properties.getTestsMax(),
                pickBest(byType.get(EvidenceType.TEST)),
                "Tests"
        ));
        factors.add(scoreCoverage(byType.get(EvidenceType.CODE_COVERAGE)));
        factors.add(scoreStaticAnalysis(
                byType.get(EvidenceType.STATIC_ANALYSIS),
                collectStaticFindings(byType.get(EvidenceType.STATIC_ANALYSIS), extraFindings)
        ));
        factors.add(scoreSecurity(
                Stream.concat(
                        stream(byType.get(EvidenceType.DEPENDENCY_SCAN)),
                        stream(byType.get(EvidenceType.CONTAINER_SCAN))
                ).toList(),
                collectSecurityFindings(items, extraFindings)
        ));
        factors.add(scoreStatusFactor(
                FACTOR_WORKFLOW,
                properties.getWorkflowMax(),
                pickBest(byType.get(EvidenceType.WORKFLOW)),
                "Workflow"
        ));

        int rawTotal = factors.stream().mapToInt(ScoreFactor::score).sum();
        int maxTotal = properties.totalMaxPoints();
        int normalized = normalizeToHundred(rawTotal, maxTotal);

        return new ConfidenceScoreResult(normalized, factors);
    }

    private ScoreFactor scoreStatusFactor(
            String name,
            int maxScore,
            ScoringEvidenceItem item,
            String label
    ) {
        if (maxScore <= 0) {
            return new ScoreFactor(name, 0, 0, label + " factor disabled");
        }
        if (item == null) {
            int credit = missingCredit(maxScore);
            return new ScoreFactor(
                    name,
                    credit,
                    maxScore,
                    credit == 0
                            ? label + " evidence missing"
                            : label + " evidence missing; partial credit applied"
            );
        }

        EvidenceStatus status = item.status();
        return switch (status) {
            case PASSED -> new ScoreFactor(name, maxScore, maxScore, label + " passed");
            case FAILED -> new ScoreFactor(name, 0, maxScore, label + " failed");
            case WARNING -> new ScoreFactor(
                    name,
                    half(maxScore),
                    maxScore,
                    label + " completed with warnings"
            );
            case SKIPPED -> new ScoreFactor(
                    name,
                    missingCredit(maxScore),
                    maxScore,
                    label + " was skipped"
            );
            case UNKNOWN -> new ScoreFactor(
                    name,
                    missingCredit(maxScore),
                    maxScore,
                    label + " status unknown"
            );
        };
    }

    private ScoreFactor scoreCoverage(List<ScoringEvidenceItem> coverageItems) {
        int max = properties.getCoverageMax();
        if (max <= 0) {
            return new ScoreFactor(FACTOR_COVERAGE, 0, 0, "Coverage factor disabled");
        }
        ScoringEvidenceItem item = pickBestCoverage(coverageItems);
        if (item == null) {
            int credit = missingCredit(max);
            return new ScoreFactor(
                    FACTOR_COVERAGE,
                    credit,
                    max,
                    credit == 0 ? "Code coverage evidence missing" : "Code coverage missing; partial credit applied"
            );
        }
        if (item.status() == EvidenceStatus.FAILED && item.metricValue() == null) {
            return new ScoreFactor(FACTOR_COVERAGE, 0, max, "Code coverage reported as failed");
        }

        BigDecimal metric = item.metricValue();
        if (metric == null) {
            // Fall back to status when no numeric metric
            return scoreStatusFactor(FACTOR_COVERAGE, max, item, "Code coverage");
        }

        double pct = metric.doubleValue();
        double full = properties.getCoverageFullThreshold();
        double zero = properties.getCoverageZeroThreshold();
        if (zero > full) {
            double tmp = zero;
            zero = full;
            full = tmp;
        }

        int points;
        String reason;
        if (pct >= full) {
            points = max;
            reason = String.format(Locale.ROOT, "Code coverage %.1f%% meets full threshold (%.0f%%)", pct, full);
        } else if (pct <= zero) {
            points = 0;
            reason = String.format(Locale.ROOT, "Code coverage %.1f%% is at or below minimum threshold (%.0f%%)", pct, zero);
        } else {
            double ratio = (pct - zero) / (full - zero);
            points = (int) Math.round(ratio * max);
            points = clamp(points, 0, max);
            reason = String.format(Locale.ROOT, "Code coverage %.1f%% awards partial credit", pct);
        }
        return new ScoreFactor(FACTOR_COVERAGE, points, max, reason);
    }

    private ScoreFactor scoreStaticAnalysis(
            List<ScoringEvidenceItem> items,
            List<ScoringFindingItem> findings
    ) {
        int max = properties.getStaticAnalysisMax();
        if (max <= 0) {
            return new ScoreFactor(FACTOR_STATIC_ANALYSIS, 0, 0, "Static analysis factor disabled");
        }
        if (items == null || items.isEmpty()) {
            int credit = missingCredit(max);
            return new ScoreFactor(
                    FACTOR_STATIC_ANALYSIS,
                    credit,
                    max,
                    credit == 0
                            ? "Static analysis evidence missing"
                            : "Static analysis missing; partial credit applied"
            );
        }

        ScoringEvidenceItem best = pickBest(items);
        if (best != null && best.status() == EvidenceStatus.FAILED && findings.isEmpty()) {
            return new ScoreFactor(FACTOR_STATIC_ANALYSIS, 0, max, "Static analysis failed");
        }

        int penalty = penaltyForFindings(
                findings,
                properties.getStaticCriticalPenalty(),
                properties.getStaticHighPenalty(),
                properties.getStaticMediumPenalty(),
                properties.getStaticLowPenalty()
        );
        int points = clamp(max - penalty, 0, max);
        String reason = findings.isEmpty()
                ? "Static analysis reported no findings"
                : String.format(Locale.ROOT, "Static analysis: %s", summarizeSeverities(findings));
        return new ScoreFactor(FACTOR_STATIC_ANALYSIS, points, max, reason);
    }

    private ScoreFactor scoreSecurity(
            List<ScoringEvidenceItem> securityEvidence,
            List<ScoringFindingItem> findings
    ) {
        int max = properties.getSecurityMax();
        if (max <= 0) {
            return new ScoreFactor(FACTOR_SECURITY, 0, 0, "Security factor disabled");
        }
        if (securityEvidence.isEmpty()) {
            int credit = missingCredit(max);
            return new ScoreFactor(
                    FACTOR_SECURITY,
                    credit,
                    max,
                    credit == 0
                            ? "Security scan evidence missing"
                            : "Security scan missing; partial credit applied"
            );
        }

        boolean anyFailedWithoutFindings = securityEvidence.stream()
                .anyMatch(e -> e.status() == EvidenceStatus.FAILED)
                && findings.isEmpty();
        if (anyFailedWithoutFindings) {
            return new ScoreFactor(FACTOR_SECURITY, 0, max, "Security scan failed");
        }

        int penalty = penaltyForFindings(
                findings,
                properties.getSecurityCriticalPenalty(),
                properties.getSecurityHighPenalty(),
                properties.getSecurityMediumPenalty(),
                properties.getSecurityLowPenalty()
        );
        int points = clamp(max - penalty, 0, max);
        String reason;
        if (findings.isEmpty()) {
            reason = "No security vulnerabilities detected";
        } else {
            long critical = countSeverity(findings, FindingSeverity.CRITICAL);
            long high = countSeverity(findings, FindingSeverity.HIGH);
            if (critical > 0) {
                reason = String.format(Locale.ROOT, "%d critical security finding(s) detected", critical);
            } else if (high > 0) {
                reason = String.format(Locale.ROOT, "%d high severity vulnerabilit%s detected",
                        high, high == 1 ? "y" : "ies");
            } else {
                reason = String.format(Locale.ROOT, "Security findings: %s", summarizeSeverities(findings));
            }
        }
        return new ScoreFactor(FACTOR_SECURITY, points, max, reason);
    }

    private static int penaltyForFindings(
            List<ScoringFindingItem> findings,
            int criticalPenalty,
            int highPenalty,
            int mediumPenalty,
            int lowPenalty
    ) {
        int penalty = 0;
        for (ScoringFindingItem f : findings) {
            FindingSeverity severity = f.severity() != null ? f.severity() : FindingSeverity.UNKNOWN;
            penalty += switch (severity) {
                case CRITICAL -> criticalPenalty;
                case HIGH -> highPenalty;
                case MEDIUM -> mediumPenalty;
                case LOW -> lowPenalty;
                case INFO, UNKNOWN -> 0;
            };
        }
        return penalty;
    }

    private static String summarizeSeverities(List<ScoringFindingItem> findings) {
        long c = countSeverity(findings, FindingSeverity.CRITICAL);
        long h = countSeverity(findings, FindingSeverity.HIGH);
        long m = countSeverity(findings, FindingSeverity.MEDIUM);
        long l = countSeverity(findings, FindingSeverity.LOW);
        return String.format(Locale.ROOT, "critical=%d, high=%d, medium=%d, low=%d", c, h, m, l);
    }

    private static long countSeverity(List<ScoringFindingItem> findings, FindingSeverity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    private List<ScoringFindingItem> collectStaticFindings(
            List<ScoringEvidenceItem> typedEvidence,
            List<ScoringFindingItem> extra
    ) {
        List<ScoringFindingItem> nested = stream(typedEvidence)
                .flatMap(e -> e.findings().stream())
                .toList();
        List<ScoringFindingItem> typedExtra = extra.stream()
                .filter(f -> f.type() != null && (
                        f.type().name().contains("STATIC")
                                || f.type().name().contains("SAST")
                                || f.type().name().contains("CODE_SMELL")
                                || f.type().name().contains("SECRET")
                ))
                .toList();
        if (typedExtra.isEmpty()) {
            return nested;
        }
        List<ScoringFindingItem> combined = new ArrayList<>(nested);
        combined.addAll(typedExtra);
        return List.copyOf(combined);
    }

    private List<ScoringFindingItem> collectSecurityFindings(
            List<ScoringEvidenceItem> allEvidence,
            List<ScoringFindingItem> extra
    ) {
        List<ScoringFindingItem> nested = allEvidence.stream()
                .filter(e -> SECURITY_TYPES.contains(e.type()))
                .flatMap(e -> e.findings().stream())
                .toList();
        List<ScoringFindingItem> fromExtra = extra.stream()
                .filter(f -> {
                    if (f.type() == null) {
                        return true;
                    }
                    String name = f.type().name();
                    if (name.contains("STATIC") || name.contains("SAST")
                            || name.contains("CODE_SMELL") || name.contains("SECRET")) {
                        return false;
                    }
                    return name.contains("DEPENDENCY")
                            || name.contains("CONTAINER")
                            || name.contains("SECURITY")
                            || name.contains("VULNERABILITY");
                })
                .toList();
        if (fromExtra.isEmpty()) {
            return nested;
        }
        List<ScoringFindingItem> combined = new ArrayList<>(nested);
        combined.addAll(fromExtra);
        return List.copyOf(combined);
    }

    private int missingCredit(int maxScore) {
        double fraction = properties.getMissingEvidenceCredit();
        if (fraction <= 0) {
            return 0;
        }
        if (fraction >= 1) {
            return maxScore;
        }
        return clamp((int) Math.round(maxScore * fraction), 0, maxScore);
    }

    private static int normalizeToHundred(int rawTotal, int maxTotal) {
        if (maxTotal <= 0) {
            return 0;
        }
        if (maxTotal == 100) {
            return clamp(rawTotal, 0, 100);
        }
        int scaled = BigDecimal.valueOf(rawTotal)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxTotal), 0, RoundingMode.HALF_UP)
                .intValue();
        return clamp(scaled, 0, 100);
    }

    private static int half(int maxScore) {
        return maxScore / 2;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Map<EvidenceType, List<ScoringEvidenceItem>> groupByType(List<ScoringEvidenceItem> items) {
        Map<EvidenceType, List<ScoringEvidenceItem>> map = new EnumMap<>(EvidenceType.class);
        for (ScoringEvidenceItem item : items) {
            map.computeIfAbsent(item.type(), t -> new ArrayList<>()).add(item);
        }
        return map;
    }

    private static Stream<ScoringEvidenceItem> stream(List<ScoringEvidenceItem> items) {
        return items == null ? Stream.empty() : items.stream();
    }

    /**
     * Prefer PASSED over WARNING over others; stable first match within same rank.
     */
    private static ScoringEvidenceItem pickBest(List<ScoringEvidenceItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .min((a, b) -> Integer.compare(statusRank(a.status()), statusRank(b.status())))
                .orElse(items.getFirst());
    }

    private static ScoringEvidenceItem pickBestCoverage(List<ScoringEvidenceItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .filter(i -> i.metricValue() != null)
                .max((a, b) -> a.metricValue().compareTo(b.metricValue()))
                .orElse(pickBest(items));
    }

    private static int statusRank(EvidenceStatus status) {
        if (status == null) {
            return 99;
        }
        return switch (status) {
            case PASSED -> 0;
            case WARNING -> 1;
            case SKIPPED -> 2;
            case UNKNOWN -> 3;
            case FAILED -> 4;
        };
    }
}
