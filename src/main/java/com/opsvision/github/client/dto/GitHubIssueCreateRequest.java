package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Body for {@code POST /repos/{owner}/{repo}/issues}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitHubIssueCreateRequest(
        String title,
        String body,
        List<String> labels,
        List<String> assignees
) {
    public GitHubIssueCreateRequest(String title, String body, List<String> labels) {
        this(title, body, labels, null);
    }
}
