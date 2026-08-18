package com.opsvision.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opsvision.ai.config.AiProperties;
import com.opsvision.ai.exception.AiProviderException;
import com.opsvision.ai.model.DeploymentExplanation;
import com.opsvision.ai.model.DeploymentExplanationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * First-class Ollama provider using the native {@code POST /api/chat} API (non-streaming).
 */
public class OllamaAiProvider implements AiProvider {

    public static final String PROVIDER_NAME = "ollama";

    private static final Logger log = LoggerFactory.getLogger(OllamaAiProvider.class);

    private static final String SYSTEM_PROMPT = """
            You are a deployment risk analyst for OpsVision.
            Explain deployment risk using ONLY the structured JSON context provided by the user.
            Rules:
            - Do NOT invent metrics, findings, scores, or CI results that are not in the context.
            - Do NOT calculate or change the confidence score or policy decision.
            - Do NOT recommend destructive recovery actions (no automatic rollback execution).
            - Be concise and practical.
            Respond with a single JSON object only (no markdown fences) using this shape:
            {
              "summary": "one short paragraph",
              "concerns": ["bullet", "..."],
              "remediations": ["bullet", "..."]
            }
            If evidence is sparse, say what is unknown rather than guessing.
            """;

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public OllamaAiProvider(RestClient restClient, AiProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public DeploymentExplanation generateDeploymentExplanation(DeploymentExplanationRequest request) {
        String model = properties.resolveOllamaModel();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);

            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", SYSTEM_PROMPT);

            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", objectMapper.writeValueAsString(request));

            ObjectNode options = body.putObject("options");
            options.put("temperature", properties.getTemperature());
            options.put("num_predict", properties.getMaxTokens());

            String raw = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseChatResponse(raw, model);
        } catch (RestClientResponseException ex) {
            throw new AiProviderException(
                    "Ollama HTTP " + ex.getStatusCode().value() + ": " + shorten(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (ResourceAccessException ex) {
            throw new AiProviderException(
                    "Ollama is unavailable or timed out at " + properties.resolveOllamaBaseUrl()
                            + ": " + ex.getMessage(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new AiProviderException("Ollama request failed: " + ex.getMessage(), ex);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException("Ollama provider failed: " + ex.getMessage(), ex);
        }
    }

    private DeploymentExplanation parseChatResponse(String raw, String model) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new AiProviderException("Ollama returned an empty response");
        }
        JsonNode root = objectMapper.readTree(raw);
        JsonNode contentNode = root.path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new AiProviderException("Ollama response missing message content");
        }
        String content = contentNode.asText("").trim();
        if (content.isEmpty()) {
            throw new AiProviderException("Ollama returned empty message content");
        }
        String jsonPayload = extractJsonObject(content);
        try {
            JsonNode parsed = objectMapper.readTree(jsonPayload);
            String summary = textOrDefault(parsed.path("summary"), content);
            List<String> concerns = stringList(parsed.path("concerns"));
            List<String> remediations = stringList(parsed.path("remediations"));
            return new DeploymentExplanation(
                    summary,
                    concerns,
                    remediations,
                    PROVIDER_NAME,
                    model,
                    true
            );
        } catch (Exception parseEx) {
            log.warn("Ollama response was not valid JSON; using plain-text fallback");
            return new DeploymentExplanation(
                    content,
                    List.of(),
                    List.of(),
                    PROVIDER_NAME,
                    model,
                    true
            );
        }
    }

    private static String extractJsonObject(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    String v = item.asText(null);
                    if (v != null && !v.isBlank()) {
                        out.add(v.trim());
                    }
                }
            }
        }
        return out;
    }

    private static String textOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String v = node.asText("").trim();
        return v.isEmpty() ? fallback : v;
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        String t = body.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }
}
