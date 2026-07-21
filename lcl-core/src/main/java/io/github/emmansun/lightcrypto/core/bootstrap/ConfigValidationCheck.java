package io.github.emmansun.lightcrypto.core.bootstrap;

/**
 * BOOT-1: Configuration validation check.
 * <p>
 * Verifies that the runtime configuration is valid (spiVersion ≥ 1, timeout > 0).
 *
 * @since 1.0.0
 */
public final class ConfigValidationCheck implements BootstrapCheck {

    @Override
    public PhaseResult check(BootstrapContext context) {
        long start = System.nanoTime();
        try {
            if (context.spiVersion() < 1) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                return PhaseResult.failure("BOOT-1 Config", ms,
                        "spiVersion must be >= 1, got " + context.spiVersion());
            }
            if (context.bootstrapTimeout().isNegative() || context.bootstrapTimeout().isZero()) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                return PhaseResult.failure("BOOT-1 Config", ms,
                        "bootstrapTimeout must be positive");
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            return PhaseResult.success("BOOT-1 Config", ms);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return PhaseResult.failure("BOOT-1 Config", ms, e.getMessage());
        }
    }
}
