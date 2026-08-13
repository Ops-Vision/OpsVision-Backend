package com.opsvision.github.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitResponse(
        String sha,
        @JsonProperty("html_url") String htmlUrl,
        GitHubCommitDetail commit,
        GitHubUserResponse author
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubCommitDetail(
            String message,
            GitHubGitActor author,
            GitHubGitActor committer
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubGitActor(
            String name,
            String email,
            String date
    ) {
    }
}
