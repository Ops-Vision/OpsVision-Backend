package com.opsvision.ai.provider;

import com.opsvision.ai.model.DeploymentExplanation;
import com.opsvision.ai.model.DeploymentExplanationRequest;

/**
 * Safe default when AI is disabled or not configured.
 */
public class NoOpAiProvider implements AiProvider {

    public static final String PROVIDER_NAME = "none";

    private final String message;

    public NoOpAiProvider() {
        this("AI explanations are disabled. Set opsvision.ai.enabled=true and configure an API key to enable.");
    }

    public NoOpAiProvider(String message) {
        this.message = message != null
                ? message
                : "AI explanations are not available";
    }

    @Override
    public DeploymentExplanation generateDeploymentExplanation(DeploymentExplanationRequest request) {
        return DeploymentExplanation.unavailable(PROVIDER_NAME, message);
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }
}
