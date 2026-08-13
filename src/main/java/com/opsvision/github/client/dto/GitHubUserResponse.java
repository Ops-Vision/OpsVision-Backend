package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUserResponse(
        Long id,
        String login,
        @JsonProperty("html_url") String htmlUrl,
        String type
) {
}
