package com.opsvision.github.mapper;

import com.opsvision.github.client.dto.GitHubWorkflowRunResponse;
import com.opsvision.github.model.GitHubWorkflowRunInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubMapperTest {

    private final GitHubMapper mapper = new GitHubMapper();

    @Test
    void toWorkflowRunInfo_parsesTimestamps() {
        GitHubWorkflowRunResponse response = new GitHubWorkflowRunResponse(
                1L,
                "CI",
                "CI title",
                "completed",
                "success",
                "https://example.com",
                "main",
                "abc",
                "push",
                1,
                1,
                "2024-01-01T00:00:00Z",
                "2024-01-01T00:05:00Z",
                "2024-01-01T00:00:10Z",
                null
        );

        GitHubWorkflowRunInfo info = mapper.toWorkflowRunInfo(response);

        assertThat(info.createdAt()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(info.updatedAt()).isEqualTo("2024-01-01T00:05:00Z");
        assertThat(info.startedAt()).isEqualTo("2024-01-01T00:00:10Z");
        assertThat(info.isSuccessful()).isTrue();
    }

    @Test
    void toWorkflowRunInfo_returnsNullForNullInput() {
        assertThat(mapper.toWorkflowRunInfo(null)).isNull();
    }
}
