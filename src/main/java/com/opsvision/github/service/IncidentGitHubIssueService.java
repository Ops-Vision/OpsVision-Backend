package com.opsvision.github.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.github.client.GitHubApiClient;
import com.opsvision.github.client.dto.GitHubIssueCreateRequest;
import com.opsvision.github.client.dto.GitHubIssueResponse;
import com.opsvision.github.client.dto.GitHubIssueSearchResponse;
import com.opsvision.github.config.GitHubProperties;
import com.opsvision.github.dto.IncidentGitHubIssueResponse;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.entity.IncidentTimelineEntry;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.model.ProbableCause;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.repository.IncidentRepository;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
import com.opsvision.recovery.service.RecoveryRecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Creates GitHub issues for incidents and prevents duplicates.
 */
@Service
public class IncidentGitHubIssueService {

    public static final String MARKER_PREFIX = "opsvision-incident-id:";
    public static final String DEFAULT_LABEL = "opsvision-incident";

    private static final Logger log = LoggerFactory.getLogger(IncidentGitHubIssueService.class);

    private final IncidentRepository incidentRepository;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubProperties gitHubProperties;
    private final RootCauseAnalysisService rootCauseAnalysisService;
    private final RecoveryRecommendationService recoveryRecommendationService;

    public IncidentGitHubIssueService(
            IncidentRepository incidentRepository,
            GitHubApiClient gitHubApiClient,
            GitHubProperties gitHubProperties,
            RootCauseAnalysisService rootCauseAnalysisService,
            RecoveryRecommendationService recoveryRecommendationService
    ) {
        this.incidentRepository = incidentRepository;
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubProperties = gitHubProperties;
        this.rootCauseAnalysisService = rootCauseAnalysisService;
        this.recoveryRecommendationService = recoveryRecommendationService;
    }

    /**
     * Create a GitHub issue for the incident, or return the already-linked issue.
     */
    @Transactional
    public IncidentGitHubIssueResponse createOrGetIssue(Long incidentId) {
        Incident incident = incidentRepository.findByIdWithTimeline(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        if (incident.getGithubIssueNumber() != null && StringUtils.hasText(incident.getGithubIssueUrl())) {
            return toResponse(incident, false, true);
        }

        OwnerRepo ownerRepo = resolveOwnerRepo(incident);

        Optional<GitHubIssueResponse> remoteDuplicate = findRemoteDuplicate(ownerRepo, incidentId);
        if (remoteDuplicate.isPresent()) {
            GitHubIssueResponse existing = remoteDuplicate.get();
            linkIssue(incident, existing);
            incidentRepository.save(incident);
            log.info("Linked existing GitHub issue #{} to incident {}", existing.number(), incidentId);
            return toResponse(incident, false, true);
        }

        RootCauseAnalysisResult rca = rootCauseAnalysisService.analyzeIncident(incident);
        RecoveryRecommendationResult recovery = recoveryRecommendationService.recommendFrom(incident, rca);

        String title = buildTitle(incident);
        String body = buildBody(incident, rca, recovery);
        List<String> labels = List.of(DEFAULT_LABEL, "severity:" + incident.getSeverity().name().toLowerCase(Locale.ROOT));

        GitHubIssueResponse created = gitHubApiClient.createIssue(
                ownerRepo.owner(),
                ownerRepo.repo(),
                new GitHubIssueCreateRequest(title, body, labels)
        );

        linkIssue(incident, created);
        incidentRepository.save(incident);
        log.info("Created GitHub issue #{} for incident {}", created.number(), incidentId);
        return toResponse(incident, true, false);
    }

    private Optional<GitHubIssueResponse> findRemoteDuplicate(OwnerRepo ownerRepo, Long incidentId) {
        String marker = marker(incidentId);
        // Prefer repo-scoped search; fall back to listing is not available — use search API
        String query = String.format(
                Locale.ROOT,
                "repo:%s/%s in:body \"%s\"",
                ownerRepo.owner(),
                ownerRepo.repo(),
                marker
        );
        try {
            GitHubIssueSearchResponse search = gitHubApiClient.searchIssues(query, 5);
            if (search == null || search.items() == null || search.items().isEmpty()) {
                return Optional.empty();
            }
            return search.items().stream()
                    .filter(i -> i.body() != null && i.body().contains(marker))
                    .findFirst();
        } catch (Exception ex) {
            log.warn("GitHub issue search failed for incident {}; proceeding to create: {}",
                    incidentId, ex.getMessage());
            return Optional.empty();
        }
    }

    private static void linkIssue(Incident incident, GitHubIssueResponse issue) {
        incident.setGithubIssueNumber(issue.number() != null ? issue.number().longValue() : null);
        incident.setGithubIssueUrl(issue.htmlUrl());
        incident.setGithubIssueCreatedAt(Instant.now());
    }

    private OwnerRepo resolveOwnerRepo(Incident incident) {
        Deployment deployment = incident.getDeployment();
        if (deployment != null && deployment.getRepository() != null) {
            ProjectRepository repo = deployment.getRepository();
            if (StringUtils.hasText(repo.getOwner()) && StringUtils.hasText(repo.getName())) {
                return new OwnerRepo(repo.getOwner(), repo.getName());
            }
        }
        return new OwnerRepo(gitHubProperties.requireOwner(), gitHubProperties.requireRepository());
    }

    static String marker(Long incidentId) {
        return MARKER_PREFIX + " " + incidentId;
    }

    static String buildTitle(Incident incident) {
        String severity = incident.getSeverity() != null ? incident.getSeverity().name() : "UNKNOWN";
        String base = incident.getTitle() != null ? incident.getTitle() : "OpsVision incident";
        String title = "[OpsVision][" + severity + "] " + base;
        if (title.length() > 240) {
            return title.substring(0, 237) + "...";
        }
        return title;
    }

    static String buildBody(
            Incident incident,
            RootCauseAnalysisResult rca,
            RecoveryRecommendationResult recovery
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Incident summary\n\n");
        sb.append("- **OpsVision incident id:** ").append(incident.getId()).append('\n');
        sb.append("- **Status:** ").append(incident.getStatus()).append('\n');
        sb.append("- **Severity:** ").append(incident.getSeverity()).append('\n');
        if (incident.getDetectedAt() != null) {
            sb.append("- **Detected at:** ").append(incident.getDetectedAt()).append('\n');
        }
        if (StringUtils.hasText(incident.getEnvironment())) {
            sb.append("- **Environment:** ").append(incident.getEnvironment()).append('\n');
        }
        if (StringUtils.hasText(incident.getNamespace())) {
            sb.append("- **Namespace:** ").append(incident.getNamespace()).append('\n');
        }
        if (StringUtils.hasText(incident.getWorkloadName())) {
            sb.append("- **Workload:** ").append(incident.getWorkloadName()).append('\n');
        }
        if (StringUtils.hasText(incident.getSummary())) {
            sb.append("\n").append(incident.getSummary()).append("\n");
        }

        sb.append("\n## Affected deployment\n\n");
        Deployment deployment = incident.getDeployment();
        if (deployment != null) {
            sb.append("- **Deployment id:** ").append(deployment.getId()).append('\n');
            if (StringUtils.hasText(deployment.getCommitSha())) {
                sb.append("- **Commit:** `").append(deployment.getCommitSha()).append("`\n");
            }
            if (StringUtils.hasText(deployment.getBranch())) {
                sb.append("- **Branch:** ").append(deployment.getBranch()).append('\n');
            }
            if (StringUtils.hasText(deployment.getEnvironment())) {
                sb.append("- **Environment:** ").append(deployment.getEnvironment()).append('\n');
            }
            if (deployment.getRepository() != null) {
                sb.append("- **Repository:** ").append(deployment.getRepository().getFullName()).append('\n');
                if (StringUtils.hasText(deployment.getRepository().getUrl())) {
                    sb.append("- **Repo URL:** ").append(deployment.getRepository().getUrl()).append('\n');
                }
            }
        } else {
            sb.append("_No deployment linked._\n");
            if (StringUtils.hasText(incident.getCommitSha())) {
                sb.append("- **Commit:** `").append(incident.getCommitSha()).append("`\n");
            }
        }

        sb.append("\n## Probable root cause\n\n");
        if (rca != null && !rca.probableCauses().isEmpty()) {
            ProbableCause top = rca.probableCauses().getFirst();
            sb.append("- **Cause:** ").append(top.cause()).append('\n');
            sb.append("- **Category:** ").append(top.category()).append('\n');
            sb.append("- **Confidence:** ").append(top.confidence()).append('\n');
            if (!top.evidence().isEmpty()) {
                sb.append("\n### Supporting evidence\n\n");
                for (String e : top.evidence().stream().limit(8).toList()) {
                    sb.append("- ").append(e).append('\n');
                }
            }
            if (rca.probableCauses().size() > 1) {
                sb.append("\n### Other hypotheses\n\n");
                rca.probableCauses().stream().skip(1).limit(3).forEach(c ->
                        sb.append("- (").append(c.confidence()).append(") ")
                                .append(c.category()).append(": ").append(c.cause()).append('\n')
                );
            }
        } else {
            sb.append("_No RCA hypotheses available._\n");
        }

        sb.append("\n## Recommended action\n\n");
        if (recovery != null) {
            sb.append("- **Action:** ").append(recovery.action()).append('\n');
            sb.append("- **Reason:** ").append(recovery.reason()).append('\n');
            if (StringUtils.hasText(recovery.targetVersion())) {
                sb.append("- **Target version:** `").append(recovery.targetVersion()).append("`\n");
            }
            sb.append("- **Requires human approval:** ").append(recovery.requiresHumanApproval()).append('\n');
            sb.append("- **Execution mode:** ").append(recovery.executionMode()).append('\n');
        } else {
            sb.append("_No recovery recommendation available._\n");
        }

        List<IncidentTimelineEntry> timeline = incident.getTimelineEntries();
        if (timeline != null && !timeline.isEmpty()) {
            sb.append("\n## Timeline (excerpt)\n\n");
            timeline.stream().limit(12).forEach(e -> {
                sb.append("- ");
                if (e.getOccurredAt() != null) {
                    sb.append(e.getOccurredAt()).append(' ');
                }
                sb.append("**").append(e.getTitle() != null ? e.getTitle() : e.getEntryType()).append("**");
                if (StringUtils.hasText(e.getDetail())) {
                    String detail = e.getDetail();
                    if (detail.length() > 200) {
                        detail = detail.substring(0, 197) + "...";
                    }
                    sb.append(" — ").append(detail);
                }
                sb.append('\n');
            });
        }

        sb.append("\n---\n");
        sb.append("_Generated by OpsVision. Do not remove the marker line below (used for duplicate prevention)._\n\n");
        sb.append(marker(incident.getId())).append('\n');
        return sb.toString();
    }

    private static IncidentGitHubIssueResponse toResponse(
            Incident incident,
            boolean created,
            boolean duplicatePrevented
    ) {
        return new IncidentGitHubIssueResponse(
                incident.getId(),
                incident.getGithubIssueNumber() != null ? incident.getGithubIssueNumber().intValue() : null,
                incident.getGithubIssueUrl(),
                buildTitle(incident),
                created ? "open" : "linked",
                created,
                duplicatePrevented,
                incident.getGithubIssueCreatedAt() != null
                        ? incident.getGithubIssueCreatedAt()
                        : Instant.now()
        );
    }

    private record OwnerRepo(String owner, String repo) {
    }
}
