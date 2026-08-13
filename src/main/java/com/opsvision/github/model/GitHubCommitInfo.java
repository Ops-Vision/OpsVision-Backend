package com.opsvision.github.model;

import java.time.Instant;

/**
 * Internal view of a GitHub commit.
 */
public record GitHubCommitInfo(
        String sha,
        String message,
        String htmlUrl,
        String authorLogin,
        String authorName,
        Instant authoredAt
) {
}
