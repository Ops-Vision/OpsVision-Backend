package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestResponse(
        Long id,
        Integer number,
        String title,
        String state,
        @JsonProperty("html_url") String htmlUrl,
        GitHubUserResponse user,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("merged_at") String mergedAt,
        HeadRef head,
        BaseRef base
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeadRef(
            String ref,
            String sha
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaseRef(
            String ref,
            String sha
    ) {
    }
}
