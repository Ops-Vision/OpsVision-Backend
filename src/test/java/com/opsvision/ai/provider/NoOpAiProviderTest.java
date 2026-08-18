package com.opsvision.ai.provider;

import com.opsvision.ai.model.DeploymentExplanation;
import com.opsvision.ai.model.DeploymentExplanationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpAiProviderTest {

    @Test
    void returnsUnavailableExplanationWithoutCallingExternalSystems() {
        NoOpAiProvider provider = new NoOpAiProvider();
        DeploymentExplanationRequest request = new DeploymentExplanationRequest(
                1L, "o", "r", "sha", "main", "staging", "CI",
                80,
                List.of(),
                "DEPLOY",
                List.of("ok"),
                List.of(),
                List.of()
        );

        DeploymentExplanation result = provider.generateDeploymentExplanation(request);

        assertThat(result.available()).isFalse();
        assertThat(result.provider()).isEqualTo(NoOpAiProvider.PROVIDER_NAME);
        assertThat(result.summary()).containsIgnoringCase("disabled");
        assertThat(result.concerns()).isEmpty();
        assertThat(result.remediations()).isEmpty();
    }
}
