package com.opsvision.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.ai.config.AiProperties;
import com.opsvision.ai.exception.AiProviderException;
import com.opsvision.ai.model.DeploymentExplanation;
import com.opsvision.ai.model.DeploymentExplanationRequest;
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

class OpenAiCompatibleAiProviderTest {

    private AiProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private OpenAiCompatibleAiProvider provider;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai-compatible");
        properties.setApiKey("test-key");
        properties.setModel("gpt-test");
        properties.setBaseUrl("https://api.example.com/v1");

        restClientBuilder = RestClient.builder().baseUrl("https://api.example.com/v1");
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient client = restClientBuilder
                .defaultHeader("Authorization", "Bearer test-key")
                .build();
        provider = new OpenAiCompatibleAiProvider(client, properties, new ObjectMapper());
    }

    @Test
    void parsesJsonContentFromChatCompletion() {
        String llmJson = """
                {"summary":"Low risk","concerns":["none major"],"remediations":["monitor"]}
                """;
        String envelope = """
                {"choices":[{"message":{"content":%s}}]}
                """.formatted(new ObjectMapper().valueToTree(llmJson).toString());

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        DeploymentExplanation result = provider.generateDeploymentExplanation(sampleRequest());

        assertThat(result.available()).isTrue();
        assertThat(result.summary()).isEqualTo("Low risk");
        assertThat(result.concerns()).containsExactly("none major");
        assertThat(result.remediations()).containsExactly("monitor");
        assertThat(result.model()).isEqualTo("gpt-test");
        server.verify();
    }

    @Test
    void httpErrorMapsToAiProviderException() {
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("upstream down"));

        assertThatThrownBy(() -> provider.generateDeploymentExplanation(sampleRequest()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("502");
    }

    private static DeploymentExplanationRequest sampleRequest() {
        return new DeploymentExplanationRequest(
                1L, "o", "r", "sha", "main", "dev", null,
                90,
                List.of(),
                "DEPLOY",
                List.of("ok"),
                List.of(),
                List.of()
        );
    }
}
