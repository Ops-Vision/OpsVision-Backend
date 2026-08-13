package com.opsvision.evidence.model;

/**
 * Normalized categories of CI/CD and security evidence attached to a deployment.
 */
public enum EvidenceType {
    BUILD,
    TEST,
    CODE_COVERAGE,
    STATIC_ANALYSIS,
    DEPENDENCY_SCAN,
    CONTAINER_SCAN,
    WORKFLOW,
    OTHER
}
