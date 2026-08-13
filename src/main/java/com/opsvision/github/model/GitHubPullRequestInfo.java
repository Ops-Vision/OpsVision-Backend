package com.opsvision.github.model;

import java.time.Instant;

/**
 * Internal view of a GitHub pull request.
 */
public record GitHubPullRequestInfo(
        long id,
        int number,
        String title,
        String state,
        String htmlUrl,
        String authorLogin,
        String headRef,
        String headSha,
        String baseRef,
        Instant createdAt,
        Instant mergedAt
) {
}
