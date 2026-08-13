package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubWorkflowRunResponse(
        Long id,
        String name,
        @JsonProperty("display_title") String displayTitle,
        String status,
        String conclusion,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("head_branch") String headBranch,
        @JsonProperty("head_sha") String headSha,
        @JsonProperty("event") String event,
        @JsonProperty("run_number") Integer runNumber,
        @JsonProperty("run_attempt") Integer runAttempt,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("run_started_at") String runStartedAt,
        GitHubUserResponse actor
) {
}
