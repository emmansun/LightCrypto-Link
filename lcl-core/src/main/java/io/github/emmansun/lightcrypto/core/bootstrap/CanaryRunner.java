package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.format.WireFormatDecoder;
import io.github.emmansun.lightcrypto.core.format.WireFormatEncoder;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Canary self-test implementing {@link BootstrapCheck}.
 * <p>
 * Performs encrypt/decrypt roundtrip using an embedded canary DEK and namespace
 * to verify the end-to-end encryption path is functional.
 * <p>
 * Tests all configured algorithms and verifies Wire Format V1 metadata roundtrip.
 *
 * @since 1.0.0
 */
public final class CanaryRunner implements BootstrapCheck {

    /** Fixed canary plaintext. */
    private static final byte[] CANARY_PLAINTEXT = "LCL-CANARY-2026".getBytes(StandardCharsets.UTF_8);

    /** Dedicated canary namespace (not user data). */
    private static final Namespace CANARY_NAMESPACE = Namespace.parse("lcl.diagnostics.canary#selftest");

    /** Embedded canary DEK (32 bytes for AES-256, first 16 used for SM4). */
    private static final byte[] CANARY_DEK_AES = HexUtil.parseHex(
            "4c434c2d43414e4152592d44454b2d3031323334353637383961626364656637");
    private static final byte[] CANARY_DEK_SM4 = Arrays.copyOf(CANARY_DEK_AES, 16);

    private static final int CANARY_DEK_VERSION = 1;

    @Override
    public PhaseResult check(BootstrapContext context) {
        long startTime = System.nanoTime();
        List<String> failures = new ArrayList<>();

        // Multi-algorithm canary encrypt/decrypt
        for (AlgorithmId algorithm : AlgorithmId.values()) {
            try {
                byte[] dek = algorithm.keyLength() == 16 ? CANARY_DEK_SM4 : CANARY_DEK_AES;
                String blob = CryptoCodec.encrypt(dek, CANARY_PLAINTEXT, algorithm, CANARY_NAMESPACE, CANARY_DEK_VERSION);
                byte[] decrypted = CryptoCodec.decrypt(dek, blob);

                if (!Arrays.equals(CANARY_PLAINTEXT, decrypted)) {
                    failures.add(algorithm.name() + ": decrypt mismatch");
                }
            } catch (Exception e) {
                failures.add(algorithm.name() + ": " + e.getMessage());
            }
        }

        // Metadata roundtrip canary (Wire Format V1 encode → decode → verify)
        try {
            byte[] dek = CANARY_DEK_AES;
            String blob = CryptoCodec.encrypt(dek, CANARY_PLAINTEXT, AlgorithmId.AES_256_GCM, CANARY_NAMESPACE, CANARY_DEK_VERSION);

            // Decode the base64url blob to binary
            byte[] binary = Base64.getUrlDecoder().decode(blob);

            // Decode wire format
            WireFormatDecoder.DecodedBlob decoded = WireFormatDecoder.decode(binary);

            // Verify metadata fields
            if (decoded.version() != WireFormatEncoder.VERSION) {
                failures.add("metadata: version mismatch");
            }
            if (decoded.algorithm() != AlgorithmId.AES_256_GCM) {
                failures.add("metadata: algorithm mismatch");
            }
            if (!decoded.namespace().equals(CANARY_NAMESPACE.canonical())) {
                failures.add("metadata: namespace mismatch");
            }
            if (decoded.dekVersion() != CANARY_DEK_VERSION) {
                failures.add("metadata: dekVersion mismatch");
            }

            // Re-encode and verify binary matches
            byte[] reEncoded = WireFormatEncoder.encode(decoded.algorithm(), decoded.namespace(),
                    decoded.dekVersion(), decoded.iv(), decoded.aadExt(), decoded.ciphertext());
            if (!Arrays.equals(binary, reEncoded)) {
                failures.add("metadata: re-encode mismatch");
            }
        } catch (Exception e) {
            failures.add("metadata: " + e.getMessage());
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        if (!failures.isEmpty()) {
            return PhaseResult.failure("BOOT-10 Canary", durationMs, String.join("; ", failures));
        }
        return PhaseResult.success("BOOT-10 Canary", durationMs);
    }

    /**
     * Minimal hex parsing utility (avoids additional imports).
     */
    private static final class HexUtil {
        static byte[] parseHex(String hex) {
            return java.util.HexFormat.of().parseHex(hex);
        }
    }
}
