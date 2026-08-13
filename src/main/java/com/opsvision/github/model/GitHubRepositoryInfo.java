package com.opsvision.github.model;

/**
 * Internal view of a GitHub repository (decoupled from API DTOs).
 */
public record GitHubRepositoryInfo(
        Long id,
        String owner,
        String name,
        String fullName,
        String defaultBranch,
        String htmlUrl,
        String description,
        boolean isPrivate
) {
}
