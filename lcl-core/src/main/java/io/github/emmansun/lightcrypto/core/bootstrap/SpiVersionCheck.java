package io.github.emmansun.lightcrypto.core.bootstrap;

/**
 * BOOT-2: SPI version check.
 * <p>
 * Verifies that the configured SPI version equals the expected version (1).
 *
 * @since 1.0.0
 */
public final class SpiVersionCheck implements BootstrapCheck {

    private static final int EXPECTED_SPI_VERSION = 1;

    @Override
    public PhaseResult check(BootstrapContext context) {
        long start = System.nanoTime();
        int actual = context.spiVersion();
        long ms = (System.nanoTime() - start) / 1_000_000;

        if (actual != EXPECTED_SPI_VERSION) {
            return PhaseResult.failure("BOOT-2 SPI", ms,
                    "SPI version mismatch: expected " + EXPECTED_SPI_VERSION + ", got " + actual);
        }
        return PhaseResult.success("BOOT-2 SPI", ms);
    }
}
