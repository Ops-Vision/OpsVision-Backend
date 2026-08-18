package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of GitHub issue REST response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueResponse(
        Long id,
        Integer number,
        String title,
        String state,
        @JsonProperty("html_url") String htmlUrl,
        String body,
        @JsonProperty("created_at") String createdAt
) {
}
