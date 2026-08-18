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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible chat completions client (works with OpenAI and many proxies).
 */
public class OpenAiCompatibleAiProvider implements AiProvider {

    public static final String PROVIDER_NAME = "openai-compatible";

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiProvider.class);

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

    public OpenAiCompatibleAiProvider(RestClient restClient, AiProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public DeploymentExplanation generateDeploymentExplanation(DeploymentExplanationRequest request) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", properties.getModel());
            body.put("temperature", properties.getTemperature());
            body.put("max_tokens", properties.getMaxTokens());

            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", SYSTEM_PROMPT);

            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", objectMapper.writeValueAsString(request));

            String raw = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseCompletion(raw);
        } catch (RestClientResponseException ex) {
            throw new AiProviderException(
                    "AI provider HTTP " + ex.getStatusCode().value() + ": " + shorten(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (RestClientException ex) {
            throw new AiProviderException("AI provider request failed: " + ex.getMessage(), ex);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException("AI provider failed: " + ex.getMessage(), ex);
        }
    }

    private DeploymentExplanation parseCompletion(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new AiProviderException("AI provider returned an empty response");
        }
        JsonNode root = objectMapper.readTree(raw);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new AiProviderException("AI provider response missing message content");
        }
        String content = contentNode.asText("").trim();
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
                    properties.getModel(),
                    true
            );
        } catch (Exception parseEx) {
            log.warn("AI response was not valid JSON; using plain-text fallback");
            return new DeploymentExplanation(
                    content,
                    List.of(),
                    List.of(),
                    PROVIDER_NAME,
                    properties.getModel(),
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
