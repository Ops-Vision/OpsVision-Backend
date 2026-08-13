package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubWorkflowRunsListResponse(
        @JsonProperty("total_count") Integer totalCount,
        @JsonProperty("workflow_runs") List<GitHubWorkflowRunResponse> workflowRuns
) {
}
