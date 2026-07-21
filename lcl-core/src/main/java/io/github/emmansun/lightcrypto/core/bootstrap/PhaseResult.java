package io.github.emmansun.lightcrypto.core.bootstrap;

/**
 * Result of a single bootstrap phase execution.
 *
 * @param phaseName    the name of the phase
 * @param success      whether the phase passed
 * @param durationMs   execution duration in milliseconds
 * @param errorMessage error message if failed (null on success)
 * @since 1.0.0
 */
public record PhaseResult(
        String phaseName,
        boolean success,
        long durationMs,
        String errorMessage
) {

    /**
     * Creates a successful phase result.
     */
    public static PhaseResult success(String phaseName, long durationMs) {
        return new PhaseResult(phaseName, true, durationMs, null);
    }

    /**
     * Creates a failed phase result.
     */
    public static PhaseResult failure(String phaseName, long durationMs, String errorMessage) {
        return new PhaseResult(phaseName, false, durationMs, errorMessage);
    }
}
