package com.opsvision.github.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.github.client.dto.GitHubCommitResponse;
import com.opsvision.github.client.dto.GitHubIssueCreateRequest;
import com.opsvision.github.client.dto.GitHubIssueResponse;
import com.opsvision.github.client.dto.GitHubIssueSearchResponse;
import com.opsvision.github.client.dto.GitHubPullRequestResponse;
import com.opsvision.github.client.dto.GitHubRepositoryResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunsListResponse;
import com.opsvision.github.exception.GitHubAuthenticationException;
import com.opsvision.github.exception.GitHubException;
import com.opsvision.github.exception.GitHubNotFoundException;
import com.opsvision.github.exception.GitHubRateLimitException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Spring {@link RestClient}-backed GitHub API client with structured error mapping.
 */
public class RestClientGitHubApiClient implements GitHubApiClient {

    private static final ParameterizedTypeReference<List<GitHubCommitResponse>> COMMIT_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<GitHubPullRequestResponse>> PR_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestClientGitHubApiClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public GitHubRepositoryResponse getRepository(String owner, String repo) {
        return get(
                uri -> uri.path("/repos/{owner}/{repo}").build(owner, repo),
                GitHubRepositoryResponse.class
        );
    }

    @Override
    public List<GitHubCommitResponse> listCommits(String owner, String repo, String sha, int perPage) {
        List<GitHubCommitResponse> body = getList(
                uri -> {
                    UriBuilder b = uri.path("/repos/{owner}/{repo}/commits")
                            .queryParam("per_page", clampPerPage(perPage));
                    if (sha != null && !sha.isBlank()) {
                        b = b.queryParam("sha", sha);
                    }
                    return b.build(owner, repo);
                },
                COMMIT_LIST_TYPE
        );
        return body != null ? body : List.of();
    }

    @Override
    public GitHubCommitResponse getCommit(String owner, String repo, String ref) {
        return get(
                uri -> uri.path("/repos/{owner}/{repo}/commits/{ref}").build(owner, repo, ref),
                GitHubCommitResponse.class
        );
    }

    @Override
    public List<GitHubPullRequestResponse> listPullRequests(String owner, String repo, String state, int perPage) {
        List<GitHubPullRequestResponse> body = getList(
                uri -> uri.path("/repos/{owner}/{repo}/pulls")
                        .queryParam("state", state == null || state.isBlank() ? "open" : state)
                        .queryParam("per_page", clampPerPage(perPage))
                        .build(owner, repo),
                PR_LIST_TYPE
        );
        return body != null ? body : List.of();
    }

    @Override
    public GitHubPullRequestResponse getPullRequest(String owner, String repo, int number) {
        return get(
                uri -> uri.path("/repos/{owner}/{repo}/pulls/{number}").build(owner, repo, number),
                GitHubPullRequestResponse.class
        );
    }

    @Override
    public GitHubWorkflowRunsListResponse listWorkflowRuns(
            String owner,
            String repo,
            String branch,
            String status,
            int perPage
    ) {
        return get(
                uri -> {
                    UriBuilder b = uri.path("/repos/{owner}/{repo}/actions/runs")
                            .queryParam("per_page", clampPerPage(perPage));
                    if (branch != null && !branch.isBlank()) {
                        b = b.queryParam("branch", branch);
                    }
                    if (status != null && !status.isBlank()) {
                        b = b.queryParam("status", status);
                    }
                    return b.build(owner, repo);
                },
                GitHubWorkflowRunsListResponse.class
        );
    }

    @Override
    public GitHubWorkflowRunResponse getWorkflowRun(String owner, String repo, long runId) {
        return get(
                uri -> uri.path("/repos/{owner}/{repo}/actions/runs/{runId}").build(owner, repo, runId),
                GitHubWorkflowRunResponse.class
        );
    }

    @Override
    public GitHubIssueResponse createIssue(String owner, String repo, GitHubIssueCreateRequest request) {
        try {
            ResponseEntity<GitHubIssueResponse> response = restClient.post()
                    .uri(uri -> uri.path("/repos/{owner}/{repo}/issues").build(owner, repo))
                    .body(request)
                    .retrieve()
                    .toEntity(GitHubIssueResponse.class);
            return response.getBody();
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new GitHubException("GitHub API request failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public GitHubIssueSearchResponse searchIssues(String query, int perPage) {
        return get(
                uri -> uri.path("/search/issues")
                        .queryParam("q", query)
                        .queryParam("per_page", clampPerPage(perPage))
                        .build(),
                GitHubIssueSearchResponse.class
        );
    }

    private <T> T get(Function<UriBuilder, URI> uriFunction, Class<T> responseType) {
        try {
            ResponseEntity<T> response = restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .toEntity(responseType);
            return response.getBody();
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new GitHubException("GitHub API request failed: " + ex.getMessage(), ex);
        }
    }

    private <T> T getList(Function<UriBuilder, URI> uriFunction, ParameterizedTypeReference<T> type) {
        try {
            ResponseEntity<T> response = restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .toEntity(type);
            return response.getBody();
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new GitHubException("GitHub API request failed: " + ex.getMessage(), ex);
        }
    }

    private GitHubException mapResponseException(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        int code = status.value();
        String bodyMessage = extractMessage(ex.getResponseBodyAsString());
        HttpHeaders headers = ex.getResponseHeaders() != null ? ex.getResponseHeaders() : HttpHeaders.EMPTY;

        if (code == 401) {
            return new GitHubAuthenticationException(
                    "GitHub authentication failed: " + bodyMessage,
                    code
            );
        }

        if (code == 404) {
            return new GitHubNotFoundException(
                    "GitHub resource not found: " + bodyMessage,
                    code
            );
        }

        if (code == 403 || code == 429) {
            if (isRateLimited(headers, bodyMessage, code)) {
                return new GitHubRateLimitException(
                        "GitHub API rate limit exceeded: " + bodyMessage,
                        code,
                        parseResetInstant(headers),
                        parseIntegerHeader(headers, "X-RateLimit-Remaining"),
                        parseIntegerHeader(headers, "X-RateLimit-Limit")
                );
            }
            if (code == 403) {
                // Secondary rate limit or insufficient scopes often surface as 403
                String remaining = headers.getFirst("X-RateLimit-Remaining");
                if ("0".equals(remaining)) {
                    return new GitHubRateLimitException(
                            "GitHub API rate limit exceeded: " + bodyMessage,
                            code,
                            parseResetInstant(headers),
                            0,
                            parseIntegerHeader(headers, "X-RateLimit-Limit")
                    );
                }
                return new GitHubAuthenticationException(
                        "GitHub access forbidden: " + bodyMessage,
                        code
                );
            }
            return new GitHubRateLimitException(
                    "GitHub API rate limit exceeded: " + bodyMessage,
                    code,
                    parseResetInstant(headers),
                    parseIntegerHeader(headers, "X-RateLimit-Remaining"),
                    parseIntegerHeader(headers, "X-RateLimit-Limit")
            );
        }

        return new GitHubException("GitHub API error (" + code + "): " + bodyMessage, code);
    }

    private boolean isRateLimited(HttpHeaders headers, String bodyMessage, int code) {
        if (code == 429) {
            return true;
        }
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        if ("0".equals(remaining)) {
            return true;
        }
        String lower = bodyMessage == null ? "" : bodyMessage.toLowerCase();
        return lower.contains("rate limit") || lower.contains("secondary rate limit");
    }

    private Instant parseResetInstant(HttpHeaders headers) {
        Integer epochSeconds = parseIntegerHeader(headers, "X-RateLimit-Reset");
        if (epochSeconds == null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null) {
                try {
                    long seconds = Long.parseLong(retryAfter.trim());
                    return Instant.now().plusSeconds(seconds);
                } catch (NumberFormatException ignored) {
                    // ignore non-numeric Retry-After
                }
            }
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds);
    }

    private Integer parseIntegerHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "no response body";
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        String trimmed = responseBody.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
    }

    private static int clampPerPage(int perPage) {
        if (perPage < 1) {
            return 30;
        }
        return Math.min(perPage, 100);
    }
}
