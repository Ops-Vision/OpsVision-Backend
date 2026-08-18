package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Subset of {@code GET /search/issues} response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueSearchResponse(
        @JsonProperty("total_count") Integer totalCount,
        @JsonProperty("incomplete_results") Boolean incompleteResults,
        List<GitHubIssueResponse> items
) {
}
