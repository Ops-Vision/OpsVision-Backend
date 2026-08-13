package com.opsvision.evidence.model;

/**
 * Kind of finding (security, quality, coverage gap, etc.).
 */
public enum FindingType {
    SECURITY_VULNERABILITY,
    STATIC_ANALYSIS,
    DEPENDENCY,
    CONTAINER,
    COVERAGE,
    TEST_FAILURE,
    OTHER
}
