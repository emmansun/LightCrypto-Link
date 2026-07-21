package io.github.emmansun.lightcrypto.observability;

/**
 * Four-state health model for LCL components.
 * <p>
 * Overall health is computed as the worst state across all registered components.
 *
 * @since 1.0.0
 */
public enum LclHealthStatus {

    /** Initialization in progress. */
    STARTING,

    /** Fully operational. */
    READY,

    /** One or more non-critical components unavailable. */
    DEGRADED,

    /** Fatal error — crypto operations cannot proceed. */
    FAILED;

    /**
     * Returns the worse of two statuses.
     * Severity order: FAILED > DEGRADED > STARTING > READY.
     */
    public static LclHealthStatus worst(LclHealthStatus a, LclHealthStatus b) {
        return severity(a) >= severity(b) ? a : b;
    }

    private static int severity(LclHealthStatus status) {
        return switch (status) {
            case READY -> 0;
            case STARTING -> 1;
            case DEGRADED -> 2;
            case FAILED -> 3;
        };
    }
}
