package com.opsvision.github.model;

import java.time.Instant;

/**
 * Internal view of a GitHub Actions workflow run, including status/conclusion.
 */
public record GitHubWorkflowRunInfo(
        long id,
        String name,
        String displayTitle,
        String status,
        String conclusion,
        String htmlUrl,
        String headBranch,
        String headSha,
        String event,
        Integer runNumber,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt
) {
    /**
     * @return true when the run finished successfully ({@code conclusion == success}).
     */
    public boolean isSuccessful() {
        return "success".equalsIgnoreCase(conclusion);
    }

    /**
     * @return true when GitHub reports the run as completed.
     */
    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }
}
