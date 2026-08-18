package com.opsvision.ai.model;

import java.util.List;
import java.util.Objects;

/**
 * Structured context passed to an {@link com.opsvision.ai.provider.AiProvider}.
 * Contains only deterministic analysis inputs — the provider must not invent facts.
 */
public record DeploymentExplanationRequest(
        Long deploymentId,
        String owner,
        String repository,
        String commitSha,
        String branch,
        String environment,
        String workflowName,
        int confidenceScore,
        List<FactorContext> factors,
        String policyDecision,
        List<String> policyReasons,
        List<EvidenceContext> evidence,
        List<FindingContext> findings
) {
    public DeploymentExplanationRequest {
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(policyReasons, "policyReasons");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(findings, "findings");
        factors = List.copyOf(factors);
        policyReasons = List.copyOf(policyReasons);
        evidence = List.copyOf(evidence);
        findings = List.copyOf(findings);
    }

    public record FactorContext(String name, int score, int maxScore, String reason) {
    }

    public record EvidenceContext(String type, String status, String source, String summary, String metricValue) {
    }

    public record FindingContext(String type, String severity, String title, String ruleId, String filePath) {
    }
}
