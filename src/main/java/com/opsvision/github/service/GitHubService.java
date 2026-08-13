package com.opsvision.github.service;

import com.opsvision.github.client.GitHubApiClient;
import com.opsvision.github.client.dto.GitHubWorkflowRunsListResponse;
import com.opsvision.github.config.GitHubProperties;
import com.opsvision.github.mapper.GitHubMapper;
import com.opsvision.github.model.GitHubCommitInfo;
import com.opsvision.github.model.GitHubPullRequestInfo;
import com.opsvision.github.model.GitHubRepositoryInfo;
import com.opsvision.github.model.GitHubWorkflowRunInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Application-facing GitHub integration service.
 * Resolves default owner/repo from configuration when not provided.
 */
@Service
public class GitHubService {

    private static final int DEFAULT_PAGE_SIZE = 30;

    private final GitHubApiClient apiClient;
    private final GitHubMapper mapper;
    private final GitHubProperties properties;

    public GitHubService(GitHubApiClient apiClient, GitHubMapper mapper, GitHubProperties properties) {
        this.apiClient = apiClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    public GitHubRepositoryInfo getConfiguredRepository() {
        return getRepository(properties.requireOwner(), properties.requireRepository());
    }

    public GitHubRepositoryInfo getRepository(String owner, String repo) {
        requireOwnerRepo(owner, repo);
        return mapper.toRepositoryInfo(apiClient.getRepository(owner, repo));
    }

    public List<GitHubCommitInfo> listCommits(String owner, String repo, String branchOrSha, int perPage) {
        requireOwnerRepo(owner, repo);
        return apiClient.listCommits(owner, repo, branchOrSha, perPage > 0 ? perPage : DEFAULT_PAGE_SIZE)
                .stream()
                .map(mapper::toCommitInfo)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<GitHubCommitInfo> listConfiguredCommits(String branchOrSha, int perPage) {
        return listCommits(properties.requireOwner(), properties.requireRepository(), branchOrSha, perPage);
    }

    public GitHubCommitInfo getCommit(String owner, String repo, String ref) {
        requireOwnerRepo(owner, repo);
        if (!StringUtils.hasText(ref)) {
            throw new IllegalArgumentException("commit ref must not be blank");
        }
        return mapper.toCommitInfo(apiClient.getCommit(owner, repo, ref));
    }

    public List<GitHubPullRequestInfo> listPullRequests(String owner, String repo, String state, int perPage) {
        requireOwnerRepo(owner, repo);
        return apiClient.listPullRequests(owner, repo, state, perPage > 0 ? perPage : DEFAULT_PAGE_SIZE)
                .stream()
                .map(mapper::toPullRequestInfo)
                .filter(Objects::nonNull)
                .toList();
    }

    public GitHubPullRequestInfo getPullRequest(String owner, String repo, int number) {
        requireOwnerRepo(owner, repo);
        if (number <= 0) {
            throw new IllegalArgumentException("pull request number must be positive");
        }
        return mapper.toPullRequestInfo(apiClient.getPullRequest(owner, repo, number));
    }

    public List<GitHubWorkflowRunInfo> listWorkflowRuns(
            String owner,
            String repo,
            String branch,
            String status,
            int perPage
    ) {
        requireOwnerRepo(owner, repo);
        GitHubWorkflowRunsListResponse response = apiClient.listWorkflowRuns(
                owner,
                repo,
                branch,
                status,
                perPage > 0 ? perPage : DEFAULT_PAGE_SIZE
        );
        if (response == null || response.workflowRuns() == null) {
            return List.of();
        }
        return response.workflowRuns().stream()
                .map(mapper::toWorkflowRunInfo)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<GitHubWorkflowRunInfo> listConfiguredWorkflowRuns(String branch, String status, int perPage) {
        return listWorkflowRuns(
                properties.requireOwner(),
                properties.requireRepository(),
                branch,
                status,
                perPage
        );
    }

    public GitHubWorkflowRunInfo getWorkflowRun(String owner, String repo, long runId) {
        requireOwnerRepo(owner, repo);
        if (runId <= 0) {
            throw new IllegalArgumentException("workflow run id must be positive");
        }
        return mapper.toWorkflowRunInfo(apiClient.getWorkflowRun(owner, repo, runId));
    }

    /**
     * Convenience: latest workflow run for a branch (by GitHub default ordering — most recent first).
     */
    public GitHubWorkflowRunInfo findLatestWorkflowRun(String owner, String repo, String branch) {
        List<GitHubWorkflowRunInfo> runs = listWorkflowRuns(owner, repo, branch, null, 1);
        return runs.isEmpty() ? null : runs.getFirst();
    }

    private static void requireOwnerRepo(String owner, String repo) {
        if (!StringUtils.hasText(owner)) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        if (!StringUtils.hasText(repo)) {
            throw new IllegalArgumentException("repository must not be blank");
        }
    }
}
