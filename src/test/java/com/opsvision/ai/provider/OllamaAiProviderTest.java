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

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaAiProviderTest {

    private AiProperties properties;
    private MockRestServiceServer server;
    private OllamaAiProvider provider;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("ollama");
        properties.getOllama().setBaseUrl("http://localhost:11434");
        properties.getOllama().setModel("llama3.2");

        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://localhost:11434");
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient client = restClientBuilder.build();
        provider = new OllamaAiProvider(client, properties, new ObjectMapper());
    }

    @Test
    void parsesJsonContentFromOllamaChat() {
        String llmJson = """
                {"summary":"Moderate risk","concerns":["coverage gap"],"remediations":["add tests"]}
                """;
        String envelope = """
                {"model":"llama3.2","message":{"role":"assistant","content":%s},"done":true}
                """.formatted(new ObjectMapper().valueToTree(llmJson).toString());

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("llama3.2"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        DeploymentExplanation result = provider.generateDeploymentExplanation(sampleRequest());

        assertThat(result.available()).isTrue();
        assertThat(result.provider()).isEqualTo("ollama");
        assertThat(result.summary()).isEqualTo("Moderate risk");
        assertThat(result.concerns()).containsExactly("coverage gap");
        assertThat(result.remediations()).containsExactly("add tests");
        assertThat(result.model()).isEqualTo("llama3.2");
        server.verify();
    }

    @Test
    void plainTextFallbackWhenContentIsNotJson() {
        String envelope = """
                {"message":{"role":"assistant","content":"Looks acceptable overall."},"done":true}
                """;

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        DeploymentExplanation result = provider.generateDeploymentExplanation(sampleRequest());

        assertThat(result.summary()).isEqualTo("Looks acceptable overall.");
        assertThat(result.concerns()).isEmpty();
        assertThat(result.available()).isTrue();
    }

    @Test
    void httpErrorMapsToAiProviderException() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("model not found"));

        assertThatThrownBy(() -> provider.generateDeploymentExplanation(sampleRequest()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("404");
    }

    @Test
    void connectionFailureMapsToUnavailableMessage() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> provider.generateDeploymentExplanation(sampleRequest()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Ollama is unavailable");
    }

    @Test
    void emptyMessageContentFails() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withSuccess("{\"message\":{\"content\":\"\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.generateDeploymentExplanation(sampleRequest()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("empty");
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
