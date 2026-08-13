package com.opsvision.github.mapper;

import com.opsvision.github.client.dto.GitHubCommitResponse;
import com.opsvision.github.client.dto.GitHubPullRequestResponse;
import com.opsvision.github.client.dto.GitHubRepositoryResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunResponse;
import com.opsvision.github.model.GitHubCommitInfo;
import com.opsvision.github.model.GitHubPullRequestInfo;
import com.opsvision.github.model.GitHubRepositoryInfo;
import com.opsvision.github.model.GitHubWorkflowRunInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Maps GitHub API DTOs to internal domain models.
 */
@Component
public class GitHubMapper {

    public GitHubRepositoryInfo toRepositoryInfo(GitHubRepositoryResponse response) {
        if (response == null) {
            return null;
        }
        String ownerLogin = response.owner() != null ? response.owner().login() : null;
        return new GitHubRepositoryInfo(
                response.id(),
                ownerLogin,
                response.name(),
                response.fullName(),
                response.defaultBranch(),
                response.htmlUrl(),
                response.description(),
                Boolean.TRUE.equals(response.privateRepository())
        );
    }

    public GitHubCommitInfo toCommitInfo(GitHubCommitResponse response) {
        if (response == null) {
            return null;
        }
        String message = null;
        String authorName = null;
        Instant authoredAt = null;
        if (response.commit() != null) {
            message = response.commit().message();
            if (response.commit().author() != null) {
                authorName = response.commit().author().name();
                authoredAt = parseInstant(response.commit().author().date());
            }
        }
        String authorLogin = response.author() != null ? response.author().login() : null;
        return new GitHubCommitInfo(
                response.sha(),
                message,
                response.htmlUrl(),
                authorLogin,
                authorName,
                authoredAt
        );
    }

    public GitHubPullRequestInfo toPullRequestInfo(GitHubPullRequestResponse response) {
        if (response == null) {
            return null;
        }
        String authorLogin = response.user() != null ? response.user().login() : null;
        String headRef = response.head() != null ? response.head().ref() : null;
        String headSha = response.head() != null ? response.head().sha() : null;
        String baseRef = response.base() != null ? response.base().ref() : null;
        return new GitHubPullRequestInfo(
                response.id() != null ? response.id() : 0L,
                response.number() != null ? response.number() : 0,
                response.title(),
                response.state(),
                response.htmlUrl(),
                authorLogin,
                headRef,
                headSha,
                baseRef,
                parseInstant(response.createdAt()),
                parseInstant(response.mergedAt())
        );
    }

    public GitHubWorkflowRunInfo toWorkflowRunInfo(GitHubWorkflowRunResponse response) {
        if (response == null) {
            return null;
        }
        return new GitHubWorkflowRunInfo(
                response.id() != null ? response.id() : 0L,
                response.name(),
                response.displayTitle(),
                response.status(),
                response.conclusion(),
                response.htmlUrl(),
                response.headBranch(),
                response.headSha(),
                response.event(),
                response.runNumber(),
                parseInstant(response.createdAt()),
                parseInstant(response.updatedAt()),
                parseInstant(response.runStartedAt())
        );
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
