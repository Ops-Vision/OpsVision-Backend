package com.opsvision.recovery.model;

/**
 * Recommended recovery action. Recommendation only — never auto-executed in Step 13.
 */
public enum RecoveryAction {
    ROLLBACK,
    RESTART,
    SCALE_UP,
    INVESTIGATE,
    NO_ACTION
}
