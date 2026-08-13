package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of GitHub REST {@code GET /repos/{owner}/{repo}} response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepositoryResponse(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("default_branch") String defaultBranch,
        String description,
        @JsonProperty("private") Boolean privateRepository,
        GitHubUserResponse owner
) {
}
