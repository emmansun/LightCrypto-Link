package io.github.emmansun.lightcrypto.core.bootstrap;

/**
 * Functional interface for a bootstrap phase check.
 * <p>
 * Implementations perform a specific validation or self-test during the
 * bootstrap sequence and return a {@link PhaseResult} indicating success or failure.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface BootstrapCheck {

    /**
     * Executes the bootstrap check.
     *
     * @param context the bootstrap context carrying all required dependencies
     * @return the phase result indicating success or failure with details
     */
    PhaseResult check(BootstrapContext context);
}
