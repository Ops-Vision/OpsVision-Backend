package com.opsvision.recovery.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.repository.DeploymentRepository;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.model.IncidentStatus;
import com.opsvision.incident.model.ProbableCause;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.model.TimelineEntryType;
import com.opsvision.incident.repository.IncidentRepository;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.recovery.model.RecoveryAction;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps deterministic RCA + incident context into a recovery <em>recommendation</em>.
 * Never executes rollback/restart/scale — execution is a separate future concern.
 */
@Service
public class RecoveryRecommendationService {

    private static final double HIGH_CONFIDENCE = 0.70;

    private final IncidentRepository incidentRepository;
    private final RootCauseAnalysisService rootCauseAnalysisService;
    private final DeploymentRepository deploymentRepository;

    public RecoveryRecommendationService(
            IncidentRepository incidentRepository,
            RootCauseAnalysisService rootCauseAnalysisService,
            DeploymentRepository deploymentRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.rootCauseAnalysisService = rootCauseAnalysisService;
        this.deploymentRepository = deploymentRepository;
    }

    @Transactional(readOnly = true)
    public RecoveryRecommendationResult recommend(Long incidentId) {
        Incident incident = incidentRepository.findByIdWithTimeline(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
        RootCauseAnalysisResult rca = rootCauseAnalysisService.analyzeIncident(incident);
        return recommendFrom(incident, rca);
    }

    /**
     * Package-visible for unit tests without full Spring wiring.
     */
    RecoveryRecommendationResult recommendFrom(Incident incident, RootCauseAnalysisResult rca) {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(rca, "rca");
        Instant now = Instant.now();
        List<String> notes = new ArrayList<>();
        notes.add("Recommendation only — no recovery action is executed by the API");
        notes.add("Human approval is required before any destructive change");

        if (incident.getStatus() == IncidentStatus.RESOLVED
                || incident.getStatus() == IncidentStatus.CLOSED) {
            return new RecoveryRecommendationResult(
                    incidentIdOrMinusOne(incident),
                    now,
                    RecoveryAction.NO_ACTION,
                    "Incident is " + incident.getStatus() + "; no recovery action recommended",
                    null,
                    null,
                    currentCommit(incident),
                    topCategory(rca),
                    topConfidence(rca),
                    false,
                    RecoveryRecommendationResult.EXECUTION_MODE_RECOMMENDATION_ONLY,
                    List.of("status=" + incident.getStatus()),
                    notes
            );
        }

        ProbableCause top = rca.probableCauses().isEmpty()
                ? null
                : rca.probableCauses().getFirst();
        String category = top != null ? top.category() : "INSUFFICIENT_DATA";
        double confidence = top != null ? top.confidence() : 0.0;
        List<String> evidence = new ArrayList<>();
        if (top != null) {
            evidence.add("Top RCA: " + top.cause() + " (" + category + ", confidence=" + confidence + ")");
            evidence.addAll(top.evidence().stream().limit(6).toList());
        }

        Optional<Deployment> previous = findPreviousDeployment(incident);
        String targetVersion = previous.map(Deployment::getCommitSha).orElse(null);
        Long targetDeploymentId = previous.map(Deployment::getId).orElse(null);
        if (previous.isEmpty() && incident.getDeployment() != null) {
            notes.add("No prior deployment found for same repository/environment; rollback target unknown");
        }

        boolean zeroReady = hasZeroReadyWorkload(incident);
        boolean crashLoop = mentionsCrash(incident);
        boolean highLoadScheduling = hasSchedulingPressure(incident);

        RecoveryAction action;
        String reason;
        boolean requiresApproval = true;

        switch (category) {
            case "DEPLOYMENT_REGRESSION" -> {
                if (confidence >= HIGH_CONFIDENCE && incident.getDeployment() != null) {
                    action = RecoveryAction.ROLLBACK;
                    reason = buildRollbackReason(incident, targetVersion, top);
                    if (targetVersion == null) {
                        notes.add("Prefer rollback once a stable prior version is identified");
                    }
                } else if (incident.getDeployment() != null) {
                    action = RecoveryAction.INVESTIGATE;
                    reason = "Deployment-linked symptoms are present but confidence is moderate; "
                            + "investigate before rolling back";
                } else {
                    action = RecoveryAction.INVESTIGATE;
                    reason = "Symptoms resemble a release regression but no deployment is linked";
                }
            }
            case "WORKLOAD_ROLLOUT" -> {
                if (zeroReady && incident.getDeployment() != null && confidence >= 0.75) {
                    action = RecoveryAction.ROLLBACK;
                    reason = "Workload has zero ready replicas after a linked deployment; "
                            + "rollback to the previous stable version is recommended"
                            + (targetVersion != null ? " (target " + shortSha(targetVersion) + ")" : "");
                } else if (zeroReady) {
                    action = RecoveryAction.RESTART;
                    reason = "Workload has zero ready replicas; restart the rollout/pods and re-check readiness";
                } else {
                    action = RecoveryAction.RESTART;
                    reason = "Kubernetes workload rollout is unhealthy; restart or re-roll the workload";
                }
            }
            case "POD_INSTABILITY" -> {
                if (crashLoop && incident.getDeployment() != null && confidence >= HIGH_CONFIDENCE) {
                    action = RecoveryAction.ROLLBACK;
                    reason = "Pods are crash-looping after a linked deployment; rollback is safer than restart loops"
                            + (targetVersion != null ? " to " + shortSha(targetVersion) : "");
                } else {
                    action = RecoveryAction.RESTART;
                    reason = crashLoop
                            ? "Pods appear to be crash-looping; restart affected pods and inspect logs/probes"
                            : "Elevated pod restarts indicate runtime instability; restart affected pods";
                }
            }
            case "KUBERNETES_EVENTS" -> {
                if (highLoadScheduling) {
                    action = RecoveryAction.SCALE_UP;
                    reason = "Kubernetes events suggest scheduling or capacity pressure; "
                            + "scale up replicas or node capacity after confirming resource requests";
                } else {
                    action = RecoveryAction.INVESTIGATE;
                    reason = "Kubernetes warning events need operator investigation before automated recovery";
                }
            }
            case "METRIC_DEGRADATION" -> {
                action = RecoveryAction.INVESTIGATE;
                reason = "Service metrics degraded without a strong deploy/workload root cause; "
                        + "investigate dependencies, traffic, and configuration";
            }
            case "SECURITY_FINDINGS" -> {
                action = RecoveryAction.INVESTIGATE;
                reason = "Security findings are a contributing factor; investigate exposure and "
                        + "consider rollback only after confirming runtime impact";
            }
            case "INSUFFICIENT_DATA" -> {
                action = RecoveryAction.INVESTIGATE;
                reason = "Insufficient correlated signals for an automated recovery choice; gather more telemetry";
            }
            default -> {
                action = RecoveryAction.INVESTIGATE;
                reason = "No specific recovery mapping for RCA category " + category;
            }
        }

        if (action == RecoveryAction.ROLLBACK && targetVersion == null) {
            // Still recommend rollback conceptually but note missing target
            evidence.add("Rollback target version could not be resolved from deployment history");
        }
        if (action == RecoveryAction.NO_ACTION) {
            requiresApproval = false;
        }

        return new RecoveryRecommendationResult(
                incidentIdOrMinusOne(incident),
                now,
                action,
                reason,
                targetVersion,
                targetDeploymentId,
                currentCommit(incident),
                category,
                confidence,
                requiresApproval,
                RecoveryRecommendationResult.EXECUTION_MODE_RECOMMENDATION_ONLY,
                evidence,
                notes
        );
    }

    private Optional<Deployment> findPreviousDeployment(Incident incident) {
        Deployment current = incident.getDeployment();
        if (current == null || current.getRepository() == null || current.getRepository().getId() == null) {
            return Optional.empty();
        }
        Long repoId = current.getRepository().getId();
        String env = current.getEnvironment() != null
                ? current.getEnvironment()
                : incident.getEnvironment();
        List<Deployment> history = deploymentRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId);
        return history.stream()
                .filter(d -> env == null || env.equalsIgnoreCase(d.getEnvironment()))
                .filter(d -> current.getId() == null || !current.getId().equals(d.getId()))
                .filter(d -> isStrictlyBefore(d, current))
                .max(Comparator.comparing(
                        d -> d.getDeployedAt() != null ? d.getDeployedAt() : d.getCreatedAt(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ));
    }

    private static boolean isStrictlyBefore(Deployment candidate, Deployment current) {
        Instant cAt = current.getDeployedAt() != null ? current.getDeployedAt() : current.getCreatedAt();
        Instant pAt = candidate.getDeployedAt() != null ? candidate.getDeployedAt() : candidate.getCreatedAt();
        if (cAt != null && pAt != null) {
            return pAt.isBefore(cAt);
        }
        if (current.getId() != null && candidate.getId() != null) {
            return candidate.getId() < current.getId();
        }
        return true;
    }

    private static String buildRollbackReason(Incident incident, String targetVersion, ProbableCause top) {
        StringBuilder sb = new StringBuilder();
        sb.append("Error rate or instability increased after the linked deployment");
        if (incident.getCommitSha() != null) {
            sb.append(" (").append(shortSha(incident.getCommitSha())).append(")");
        }
        if (targetVersion != null) {
            sb.append("; previous version ").append(shortSha(targetVersion)).append(" is the suggested rollback target");
        } else {
            sb.append("; roll back to the last known-good version once identified");
        }
        if (top != null && top.cause() != null) {
            sb.append(". RCA: ").append(top.cause());
        }
        return sb.toString();
    }

    private static boolean hasZeroReadyWorkload(Incident incident) {
        return incident.getTimelineEntries().stream()
                .anyMatch(e -> {
                    String d = e.getDetail() != null ? e.getDetail().toLowerCase(Locale.ROOT) : "";
                    String t = e.getTitle() != null ? e.getTitle().toLowerCase(Locale.ROOT) : "";
                    return d.contains("ready=0") || t.contains("zero ready");
                });
    }

    private static boolean mentionsCrash(Incident incident) {
        return incident.getTimelineEntries().stream()
                .anyMatch(e -> {
                    String blob = ((e.getTitle() != null ? e.getTitle() : "") + " "
                            + (e.getDetail() != null ? e.getDetail() : "")).toLowerCase(Locale.ROOT);
                    return blob.contains("crashloop") || blob.contains("crash loop")
                            || blob.contains("backoff") || blob.contains("oomkilled");
                });
    }

    private static boolean hasSchedulingPressure(Incident incident) {
        return incident.getTimelineEntries().stream()
                .filter(e -> e.getEntryType() == TimelineEntryType.KUBERNETES_EVENT)
                .anyMatch(e -> {
                    String blob = ((e.getTitle() != null ? e.getTitle() : "") + " "
                            + (e.getDetail() != null ? e.getDetail() : "")).toLowerCase(Locale.ROOT);
                    return blob.contains("failedscheduling")
                            || blob.contains("insufficient")
                            || blob.contains("unschedu")
                            || blob.contains("evict");
                });
    }

    private static String currentCommit(Incident incident) {
        if (incident.getCommitSha() != null) {
            return incident.getCommitSha();
        }
        if (incident.getDeployment() != null) {
            return incident.getDeployment().getCommitSha();
        }
        return null;
    }

    private static String topCategory(RootCauseAnalysisResult rca) {
        if (rca.probableCauses().isEmpty()) {
            return "INSUFFICIENT_DATA";
        }
        return rca.probableCauses().getFirst().category();
    }

    private static double topConfidence(RootCauseAnalysisResult rca) {
        if (rca.probableCauses().isEmpty()) {
            return 0.0;
        }
        return rca.probableCauses().getFirst().confidence();
    }

    private static long incidentIdOrMinusOne(Incident incident) {
        return incident.getId() != null ? incident.getId() : -1L;
    }

    private static String shortSha(String sha) {
        if (sha == null) {
            return "unknown";
        }
        return sha.length() <= 8 ? sha : sha.substring(0, 8);
    }
}
