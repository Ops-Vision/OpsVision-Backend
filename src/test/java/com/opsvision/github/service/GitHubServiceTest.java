package com.opsvision.github.service;

import com.opsvision.github.client.GitHubApiClient;
import com.opsvision.github.client.dto.GitHubCommitResponse;
import com.opsvision.github.client.dto.GitHubPullRequestResponse;
import com.opsvision.github.client.dto.GitHubRepositoryResponse;
import com.opsvision.github.client.dto.GitHubUserResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunResponse;
import com.opsvision.github.client.dto.GitHubWorkflowRunsListResponse;
import com.opsvision.github.config.GitHubProperties;
import com.opsvision.github.mapper.GitHubMapper;
import com.opsvision.github.model.GitHubCommitInfo;
import com.opsvision.github.model.GitHubPullRequestInfo;
import com.opsvision.github.model.GitHubRepositoryInfo;
import com.opsvision.github.model.GitHubWorkflowRunInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private GitHubApiClient apiClient;

    private GitHubProperties properties;
    private GitHubService service;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setOwner("acme");
        properties.setRepository("opsvision-backend");
        service = new GitHubService(apiClient, new GitHubMapper(), properties);
    }

    @Test
    void getConfiguredRepository_usesPropertiesAndMapsDomainModel() {
        when(apiClient.getRepository("acme", "opsvision-backend")).thenReturn(
                new GitHubRepositoryResponse(
                        1L,
                        "opsvision-backend",
                        "acme/opsvision-backend",
                        "https://github.com/acme/opsvision-backend",
                        "main",
                        "desc",
                        false,
                        new GitHubUserResponse(10L, "acme", "https://github.com/acme", "Organization")
                )
        );

        GitHubRepositoryInfo info = service.getConfiguredRepository();

        assertThat(info.owner()).isEqualTo("acme");
        assertThat(info.name()).isEqualTo("opsvision-backend");
        assertThat(info.fullName()).isEqualTo("acme/opsvision-backend");
        assertThat(info.defaultBranch()).isEqualTo("main");
        assertThat(info.isPrivate()).isFalse();
        verify(apiClient).getRepository("acme", "opsvision-backend");
    }

    @Test
    void listCommits_mapsCommitDetails() {
        when(apiClient.listCommits("acme", "opsvision-backend", "main", 5)).thenReturn(List.of(
                new GitHubCommitResponse(
                        "sha1",
                        "https://github.com/acme/opsvision-backend/commit/sha1",
                        new GitHubCommitResponse.GitHubCommitDetail(
                                "fix bug",
                                new GitHubCommitResponse.GitHubGitActor(
                                        "Ada",
                                        "ada@ex.com",
                                        "2024-03-01T12:00:00Z"
                                ),
                                null
                        ),
                        new GitHubUserResponse(2L, "ada", "https://github.com/ada", "User")
                )
        ));

        List<GitHubCommitInfo> commits = service.listCommits("acme", "opsvision-backend", "main", 5);

        assertThat(commits).hasSize(1);
        GitHubCommitInfo commit = commits.getFirst();
        assertThat(commit.sha()).isEqualTo("sha1");
        assertThat(commit.message()).isEqualTo("fix bug");
        assertThat(commit.authorLogin()).isEqualTo("ada");
        assertThat(commit.authorName()).isEqualTo("Ada");
        assertThat(commit.authoredAt()).isNotNull();
    }

    @Test
    void getPullRequest_mapsHeadAndBase() {
        when(apiClient.getPullRequest("acme", "opsvision-backend", 3)).thenReturn(
                new GitHubPullRequestResponse(
                        100L,
                        3,
                        "Add feature",
                        "open",
                        "https://github.com/acme/opsvision-backend/pull/3",
                        new GitHubUserResponse(2L, "bob", "https://github.com/bob", "User"),
                        "2024-04-01T08:00:00Z",
                        "2024-04-01T09:00:00Z",
                        null,
                        new GitHubPullRequestResponse.HeadRef("feature", "headsha"),
                        new GitHubPullRequestResponse.BaseRef("main", "basesha")
                )
        );

        GitHubPullRequestInfo pr = service.getPullRequest("acme", "opsvision-backend", 3);

        assertThat(pr.number()).isEqualTo(3);
        assertThat(pr.title()).isEqualTo("Add feature");
        assertThat(pr.headRef()).isEqualTo("feature");
        assertThat(pr.headSha()).isEqualTo("headsha");
        assertThat(pr.baseRef()).isEqualTo("main");
        assertThat(pr.authorLogin()).isEqualTo("bob");
    }

    @Test
    void listWorkflowRuns_mapsStatusAndConclusion() {
        when(apiClient.listWorkflowRuns(eq("acme"), eq("opsvision-backend"), eq("main"), isNull(), eq(10)))
                .thenReturn(new GitHubWorkflowRunsListResponse(
                        1,
                        List.of(new GitHubWorkflowRunResponse(
                                55L,
                                "Build",
                                "Build #55",
                                "completed",
                                "failure",
                                "https://github.com/acme/opsvision-backend/actions/runs/55",
                                "main",
                                "cafebabe",
                                "push",
                                55,
                                1,
                                "2024-05-01T00:00:00Z",
                                "2024-05-01T00:10:00Z",
                                "2024-05-01T00:00:01Z",
                                null
                        ))
                ));

        List<GitHubWorkflowRunInfo> runs = service.listWorkflowRuns("acme", "opsvision-backend", "main", null, 10);

        assertThat(runs).hasSize(1);
        GitHubWorkflowRunInfo run = runs.getFirst();
        assertThat(run.id()).isEqualTo(55L);
        assertThat(run.status()).isEqualTo("completed");
        assertThat(run.conclusion()).isEqualTo("failure");
        assertThat(run.isCompleted()).isTrue();
        assertThat(run.isSuccessful()).isFalse();
        assertThat(run.headSha()).isEqualTo("cafebabe");
    }

    @Test
    void findLatestWorkflowRun_returnsFirstRunOrNull() {
        when(apiClient.listWorkflowRuns("acme", "opsvision-backend", "main", null, 1))
                .thenReturn(new GitHubWorkflowRunsListResponse(0, List.of()));

        assertThat(service.findLatestWorkflowRun("acme", "opsvision-backend", "main")).isNull();
    }

    @Test
    void getRepository_rejectsBlankOwner() {
        assertThatThrownBy(() -> service.getRepository(" ", "repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void getConfiguredRepository_failsWhenOwnerNotConfigured() {
        properties.setOwner("");
        assertThatThrownBy(() -> service.getConfiguredRepository())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner");
    }
}
