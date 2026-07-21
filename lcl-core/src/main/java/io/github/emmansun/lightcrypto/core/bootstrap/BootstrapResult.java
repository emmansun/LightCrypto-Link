package io.github.emmansun.lightcrypto.core.bootstrap;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Overall result of the bootstrap sequence.
 *
 * @param status       the overall status (READY, FAILED, or DEGRADED)
 * @param phaseResults the list of individual phase results
 * @param durationMs   total bootstrap duration in milliseconds
 * @param failedPhase  the name of the phase that caused failure (null if READY)
 * @param errorDetails error details (null if READY)
 * @param timestamp    when the bootstrap completed
 * @since 1.0.0
 */
public record BootstrapResult(
        Status status,
        List<PhaseResult> phaseResults,
        long durationMs,
        String failedPhase,
        String errorDetails,
        Instant timestamp
) {

    /**
     * Bootstrap status enumeration.
     */
    public enum Status {
        /** All phases passed successfully. */
        READY,
        /** A fatal failure occurred; startup should be aborted. */
        FAILED,
        /** Some recoverable failures were downgraded; system is operational but degraded. */
        DEGRADED
    }

    public BootstrapResult {
        phaseResults = phaseResults != null
                ? Collections.unmodifiableList(phaseResults)
                : Collections.emptyList();
    }

    /**
     * Creates a READY result.
     */
    public static BootstrapResult ready(List<PhaseResult> phaseResults, long durationMs) {
        return new BootstrapResult(Status.READY, phaseResults, durationMs, null, null, Instant.now());
    }

    /**
     * Creates a FAILED result.
     */
    public static BootstrapResult failed(List<PhaseResult> phaseResults, long durationMs,
                                         String failedPhase, String errorDetails) {
        return new BootstrapResult(Status.FAILED, phaseResults, durationMs, failedPhase, errorDetails, Instant.now());
    }

    /**
     * Creates a DEGRADED result.
     */
    public static BootstrapResult degraded(List<PhaseResult> phaseResults, long durationMs,
                                           String failedPhase, String errorDetails) {
        return new BootstrapResult(Status.DEGRADED, phaseResults, durationMs, failedPhase, errorDetails, Instant.now());
    }

    /**
     * Returns whether the bootstrap completed successfully.
     */
    public boolean isReady() {
        return status == Status.READY;
    }
}
