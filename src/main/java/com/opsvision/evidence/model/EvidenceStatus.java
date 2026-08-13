package com.opsvision.evidence.model;

/**
 * Outcome of a single evidence item (build, test run, scan, etc.).
 */
public enum EvidenceStatus {
    PASSED,
    FAILED,
    WARNING,
    SKIPPED,
    UNKNOWN
}
