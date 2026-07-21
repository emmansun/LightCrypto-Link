package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.spi.VaultStore;

/**
 * BOOT-8: Vault reachability check.
 * <p>
 * Verifies that the VaultStore is accessible by calling {@code loadAll()}.
 * Classified as RECOVERABLE — vault may not be ready at startup.
 *
 * @since 1.0.0
 */
public final class VaultReachabilityCheck implements BootstrapCheck {

    @Override
    public PhaseResult check(BootstrapContext context) {
        long start = System.nanoTime();

        if (context.vaultStore().isEmpty()) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            // No vault configured — skip with advisory
            return PhaseResult.failure("BOOT-8 Vault", ms, "No VaultStore configured (skipped)");
        }

        try {
            VaultStore vaultStore = context.vaultStore().get();
            vaultStore.loadAll();
            long ms = (System.nanoTime() - start) / 1_000_000;
            return PhaseResult.success("BOOT-8 Vault", ms);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return PhaseResult.failure("BOOT-8 Vault", ms, "Vault unreachable: " + e.getMessage());
        }
    }
}
