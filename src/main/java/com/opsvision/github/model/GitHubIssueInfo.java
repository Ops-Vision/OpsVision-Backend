package com.opsvision.github.model;

/**
 * Domain-facing GitHub issue snapshot.
 */
public record GitHubIssueInfo(
        Long id,
        Integer number,
        String title,
        String state,
        String htmlUrl,
        String body
) {
}
