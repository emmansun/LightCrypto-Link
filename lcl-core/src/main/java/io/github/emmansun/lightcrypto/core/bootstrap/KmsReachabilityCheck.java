package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * BOOT-9: KMS reachability check.
 * <p>
 * Verifies that the CmkProvider can wrap and unwrap a canary key.
 * Classified as RECOVERABLE — KMS may not be ready at startup.
 *
 * @since 1.0.0
 */
public final class KmsReachabilityCheck implements BootstrapCheck {

    private static final int CANARY_KEY_LENGTH = 32;

    @Override
    public PhaseResult check(BootstrapContext context) {
        long start = System.nanoTime();

        try {
            CmkProvider cmkProvider = context.cmkProvider();

            // Generate a canary key
            byte[] canaryKey = new byte[CANARY_KEY_LENGTH];
            new SecureRandom().nextBytes(canaryKey);

            // Wrap
            WrappedKey wrapped = cmkProvider.wrap(canaryKey);
            if (wrapped == null) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                return PhaseResult.failure("BOOT-9 KMS", ms, "CmkProvider.wrap() returned null");
            }

            // Unwrap
            byte[] unwrapped = cmkProvider.unwrap(wrapped);
            if (!Arrays.equals(canaryKey, unwrapped)) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                return PhaseResult.failure("BOOT-9 KMS", ms, "KMS wrap/unwrap roundtrip mismatch");
            }

            long ms = (System.nanoTime() - start) / 1_000_000;
            return PhaseResult.success("BOOT-9 KMS", ms);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return PhaseResult.failure("BOOT-9 KMS", ms, "KMS unreachable: " + e.getMessage());
        }
    }
}
