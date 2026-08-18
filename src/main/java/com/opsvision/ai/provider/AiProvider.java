package com.opsvision.ai.provider;

import com.opsvision.ai.model.DeploymentExplanation;
import com.opsvision.ai.model.DeploymentExplanationRequest;

/**
 * Pluggable LLM boundary for deployment risk explanations.
 * Implementations must not calculate scores or override policy decisions.
 */
public interface AiProvider {

    /**
     * Generate a concise risk explanation from structured analysis context only.
     */
    DeploymentExplanation generateDeploymentExplanation(DeploymentExplanationRequest request);

    /**
     * Logical provider name (e.g. openai-compatible, none).
     */
    String name();
}
