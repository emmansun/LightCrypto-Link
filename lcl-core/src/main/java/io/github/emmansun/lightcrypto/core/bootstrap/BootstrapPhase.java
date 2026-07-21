package io.github.emmansun.lightcrypto.core.bootstrap;

import java.util.Objects;

/**
 * A named bootstrap phase with a check function and failure classification.
 *
 * @param name         the phase name (e.g., "BOOT-4 KAT")
 * @param check        the check function to execute
 * @param failureClass the failure classification for this phase
 * @since 1.0.0
 */
public record BootstrapPhase(
        String name,
        BootstrapCheck check,
        FailureClass failureClass
) {

    public BootstrapPhase {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(check, "check must not be null");
        Objects.requireNonNull(failureClass, "failureClass must not be null");
    }
}
