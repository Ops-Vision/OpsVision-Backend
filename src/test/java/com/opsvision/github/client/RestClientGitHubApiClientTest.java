package com.opsvision.github.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.github.client.dto.GitHubCommitResponse;
import com.opsvision.github.client.dto.GitHubRepositoryResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunsListResponse;
import com.opsvision.github.exception.GitHubAuthenticationException;
import com.opsvision.github.exception.GitHubNotFoundException;
import com.opsvision.github.exception.GitHubRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientGitHubApiClientTest {

    private static final String BASE = "https://api.github.com";

    private MockRestServiceServer server;
    private RestClientGitHubApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("Authorization", "Bearer test-token")
                .build();
        client = new RestClientGitHubApiClient(restClient, new ObjectMapper());
    }

    @Test
    void getRepository_mapsSuccessfulResponse() {
        String body = """
                {
                  "id": 42,
                  "name": "opsvision-backend",
                  "full_name": "acme/opsvision-backend",
                  "html_url": "https://github.com/acme/opsvision-backend",
                  "default_branch": "main",
                  "description": "demo",
                  "private": false,
                  "owner": { "id": 1, "login": "acme", "html_url": "https://github.com/acme", "type": "Organization" }
                }
                """;

        server.expect(requestTo(BASE + "/repos/acme/opsvision-backend"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        GitHubRepositoryResponse repo = client.getRepository("acme", "opsvision-backend");

        assertThat(repo.id()).isEqualTo(42L);
        assertThat(repo.fullName()).isEqualTo("acme/opsvision-backend");
        assertThat(repo.defaultBranch()).isEqualTo("main");
        assertThat(repo.privateRepository()).isFalse();
        assertThat(repo.owner().login()).isEqualTo("acme");
        server.verify();
    }

    @Test
    void listCommits_returnsParsedList() {
        String body = """
                [
                  {
                    "sha": "abc123",
                    "html_url": "https://github.com/acme/r/commit/abc123",
                    "commit": {
                      "message": "feat: hello",
                      "author": { "name": "Dev", "email": "d@ex.com", "date": "2024-01-02T03:04:05Z" }
                    },
                    "author": { "id": 9, "login": "dev", "html_url": "https://github.com/dev", "type": "User" }
                  }
                ]
                """;

        server.expect(requestTo(BASE + "/repos/acme/r/commits?per_page=10&sha=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<GitHubCommitResponse> commits = client.listCommits("acme", "r", "main", 10);

        assertThat(commits).hasSize(1);
        assertThat(commits.getFirst().sha()).isEqualTo("abc123");
        assertThat(commits.getFirst().commit().message()).isEqualTo("feat: hello");
        server.verify();
    }

    @Test
    void listWorkflowRuns_returnsRunsWithStatusAndConclusion() {
        String body = """
                {
                  "total_count": 1,
                  "workflow_runs": [
                    {
                      "id": 99,
                      "name": "CI",
                      "display_title": "CI",
                      "status": "completed",
                      "conclusion": "success",
                      "html_url": "https://github.com/acme/r/actions/runs/99",
                      "head_branch": "main",
                      "head_sha": "deadbeef",
                      "event": "push",
                      "run_number": 7,
                      "run_attempt": 1,
                      "created_at": "2024-06-01T10:00:00Z",
                      "updated_at": "2024-06-01T10:05:00Z",
                      "run_started_at": "2024-06-01T10:00:01Z"
                    }
                  ]
                }
                """;

        server.expect(requestTo(BASE + "/repos/acme/r/actions/runs?per_page=5&branch=main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        GitHubWorkflowRunsListResponse response = client.listWorkflowRuns("acme", "r", "main", null, 5);

        assertThat(response.totalCount()).isEqualTo(1);
        GitHubWorkflowRunResponse run = response.workflowRuns().getFirst();
        assertThat(run.id()).isEqualTo(99L);
        assertThat(run.status()).isEqualTo("completed");
        assertThat(run.conclusion()).isEqualTo("success");
        assertThat(run.headSha()).isEqualTo("deadbeef");
        server.verify();
    }

    @Test
    void getRepository_throwsAuthenticationExceptionOn401() {
        server.expect(requestTo(BASE + "/repos/acme/r"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Bad credentials\"}"));

        assertThatThrownBy(() -> client.getRepository("acme", "r"))
                .isInstanceOf(GitHubAuthenticationException.class)
                .hasMessageContaining("Bad credentials")
                .extracting(ex -> ((GitHubAuthenticationException) ex).getStatusCode())
                .isEqualTo(401);
        server.verify();
    }

    @Test
    void getRepository_throwsNotFoundOn404() {
        server.expect(requestTo(BASE + "/repos/acme/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> client.getRepository("acme", "missing"))
                .isInstanceOf(GitHubNotFoundException.class)
                .hasMessageContaining("Not Found");
        server.verify();
    }

    @Test
    void getRepository_throwsRateLimitOn403WithRemainingZero() {
        server.expect(requestTo(BASE + "/repos/acme/r"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Limit", "60")
                        .header("X-RateLimit-Reset", "1700000000")
                        .body("{\"message\":\"API rate limit exceeded\"}"));

        assertThatThrownBy(() -> client.getRepository("acme", "r"))
                .isInstanceOf(GitHubRateLimitException.class)
                .satisfies(ex -> {
                    GitHubRateLimitException rate = (GitHubRateLimitException) ex;
                    assertThat(rate.getRemaining()).contains(0);
                    assertThat(rate.getLimit()).contains(60);
                    assertThat(rate.getResetAt()).isPresent();
                });
        server.verify();
    }

    @Test
    void getWorkflowRun_throwsRateLimitOn429() {
        server.expect(requestTo(BASE + "/repos/acme/r/actions/runs/1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Retry-After", "30")
                        .body("{\"message\":\"You have exceeded a secondary rate limit\"}"));

        assertThatThrownBy(() -> client.getWorkflowRun("acme", "r", 1L))
                .isInstanceOf(GitHubRateLimitException.class)
                .hasMessageContaining("rate limit");
        server.verify();
    }
}
