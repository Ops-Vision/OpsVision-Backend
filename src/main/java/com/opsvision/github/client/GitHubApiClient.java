package com.opsvision.github.client;

import com.opsvision.github.client.dto.GitHubCommitResponse;
import com.opsvision.github.client.dto.GitHubPullRequestResponse;
import com.opsvision.github.client.dto.GitHubRepositoryResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunsListResponse;

import java.util.List;

/**
 * Low-level GitHub REST API client. Keeps HTTP/transport details out of domain services.
 */
public interface GitHubApiClient {

    GitHubRepositoryResponse getRepository(String owner, String repo);

    List<GitHubCommitResponse> listCommits(String owner, String repo, String sha, int perPage);

    GitHubCommitResponse getCommit(String owner, String repo, String ref);

    List<GitHubPullRequestResponse> listPullRequests(String owner, String repo, String state, int perPage);

    GitHubPullRequestResponse getPullRequest(String owner, String repo, int number);

    GitHubWorkflowRunsListResponse listWorkflowRuns(
            String owner,
            String repo,
            String branch,
            String status,
            int perPage
    );

    GitHubWorkflowRunResponse getWorkflowRun(String owner, String repo, long runId);
}
