package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.crypto.SymmetricEncryptor;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.format.WireFormatEncoder;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KAT (Known Answer Test) runner implementing {@link BootstrapCheck}.
 * <p>
 * Executes cryptographic self-tests against known vectors to verify
 * the integrity of the crypto library at startup.
 * <p>
 * Timing budget: total ≤ 200ms, per-primitive ≤ 30ms.
 *
 * @since 1.0.0
 */
public final class KatRunner implements BootstrapCheck {

    /** Total KAT budget in milliseconds. */
    public static final long TOTAL_BUDGET_MS = 200;
    /** Per-primitive budget in milliseconds. */
    public static final long PER_PRIMITIVE_BUDGET_MS = 30;

    private static final String ENC_AES_GCM = "/kat/encryption/aes-256-gcm.json";
    private static final String ENC_AES_CBC = "/kat/encryption/aes-256-cbc.json";
    private static final String ENC_SM4_GCM = "/kat/encryption/sm4-gcm.json";
    private static final String ENC_SM4_CBC = "/kat/encryption/sm4-cbc.json";
    private static final String BLIND_INDEX = "/kat/blind-index/hmac-sha256.json";
    private static final String KCV = "/kat/kcv/kcv.json";

    private final Map<String, KatPrimitiveResult> lastResults = new LinkedHashMap<>();

    @Override
    public PhaseResult check(BootstrapContext context) {
        long startTime = System.nanoTime();
        lastResults.clear();
        List<String> failures = new ArrayList<>();

        // Encryption KATs
        runEncryptionKat("AES-256-GCM", ENC_AES_GCM, AlgorithmId.AES_256_GCM, startTime, failures);
        runEncryptionKat("AES-256-CBC", ENC_AES_CBC, AlgorithmId.AES_256_CBC, startTime, failures);
        runEncryptionKat("SM4-GCM", ENC_SM4_GCM, AlgorithmId.SM4_GCM, startTime, failures);
        runEncryptionKat("SM4-CBC", ENC_SM4_CBC, AlgorithmId.SM4_CBC, startTime, failures);

        // Blind index KAT
        runBlindIndexKat(startTime, failures);

        // KCV KAT
        runKcvKat(startTime, failures);

        long totalMs = (System.nanoTime() - startTime) / 1_000_000;

        if (!failures.isEmpty()) {
            return PhaseResult.failure("BOOT-4 KAT", totalMs, String.join("; ", failures));
        }

        // Total budget check — warning only, the BootstrapEngine enforces overall timeout
        if (totalMs > TOTAL_BUDGET_MS) {
            return new PhaseResult("BOOT-4 KAT", true, totalMs,
                    "KAT exceeded total budget: " + totalMs + "ms > " + TOTAL_BUDGET_MS + "ms (advisory)");
        }

        return PhaseResult.success("BOOT-4 KAT", totalMs);
    }

    /**
     * Returns the results from the last KAT run (for diagnostics endpoint).
     */
    public Map<String, KatPrimitiveResult> getLastResults() {
        return Map.copyOf(lastResults);
    }

    private void runEncryptionKat(String name, String resourcePath, AlgorithmId algorithm,
                                  long globalStart, List<String> failures) {
        long primStart = System.nanoTime();
        try {
            List<KatVectorLoader.EncryptionVector> vectors = KatVectorLoader.loadEncryptionVectors(resourcePath);
            SymmetricEncryptor encryptor = CryptoCodec.getEncryptor(algorithm);

            // Test first vector only for speed (deterministic)
            KatVectorLoader.EncryptionVector v = vectors.get(0);
            byte[] key = HexFormat.of().parseHex(v.keyHex());
            byte[] plaintext = HexFormat.of().parseHex(v.plaintextHex());
            byte[] iv = HexFormat.of().parseHex(v.ivHex());
            byte[] expectedCt = HexFormat.of().parseHex(v.expectedCiphertextHex());

            byte[] aad = WireFormatEncoder.buildAad(algorithm, v.namespace(), v.dekVersion());
            byte[] actualCt = encryptor.encrypt(key, iv, plaintext, aad);

            long primMs = (System.nanoTime() - primStart) / 1_000_000;

            if (!HexFormat.of().formatHex(actualCt).equals(v.expectedCiphertextHex())) {
                failures.add(name + ": ciphertext mismatch (vector " + v.id() + ")");
                lastResults.put(name, new KatPrimitiveResult(name, false, primMs, "ciphertext mismatch"));
            } else if (primMs > PER_PRIMITIVE_BUDGET_MS) {
                // Advisory: per-primitive budget exceeded (not fatal, environment-dependent)
                lastResults.put(name, new KatPrimitiveResult(name, true, primMs,
                        "exceeded per-primitive budget " + primMs + "ms (advisory)"));
            } else {
                lastResults.put(name, new KatPrimitiveResult(name, true, primMs, null));
            }
        } catch (Exception e) {
            long primMs = (System.nanoTime() - primStart) / 1_000_000;
            failures.add(name + ": " + e.getMessage());
            lastResults.put(name, new KatPrimitiveResult(name, false, primMs, e.getMessage()));
        }
    }

    private void runBlindIndexKat(long globalStart, List<String> failures) {
        String name = "HMAC-SHA-256";
        long primStart = System.nanoTime();
        try {
            List<KatVectorLoader.BlindIndexVector> vectors = KatVectorLoader.loadBlindIndexVectors(BLIND_INDEX);
            KatVectorLoader.BlindIndexVector v = vectors.get(0);

            byte[] masterKey = HexFormat.of().parseHex(v.masterHmacKeyHex());
            BlindIndexEngine engine = new BlindIndexEngine(masterKey);
            Namespace ns = Namespace.parse(v.namespace());
            String actual = engine.computeBlindIndex(ns, v.fieldName(), v.plaintext());

            long primMs = (System.nanoTime() - primStart) / 1_000_000;

            if (!actual.equals(v.expectedBlindIndexBase64url())) {
                failures.add(name + ": blind index mismatch (vector " + v.id() + ")");
                lastResults.put(name, new KatPrimitiveResult(name, false, primMs, "blind index mismatch"));
            } else if (primMs > PER_PRIMITIVE_BUDGET_MS) {
                lastResults.put(name, new KatPrimitiveResult(name, true, primMs,
                        "exceeded per-primitive budget (advisory)"));
            } else {
                lastResults.put(name, new KatPrimitiveResult(name, true, primMs, null));
            }
        } catch (Exception e) {
            long primMs = (System.nanoTime() - primStart) / 1_000_000;
            failures.add(name + ": " + e.getMessage());
            lastResults.put(name, new KatPrimitiveResult(name, false, primMs, e.getMessage()));
        }
    }

    private void runKcvKat(long globalStart, List<String> failures) {
        String name = "KCV";
        long primStart = System.nanoTime();
        try {
            List<KatVectorLoader.KcvVector> vectors = KatVectorLoader.loadKcvVectors(KCV);
            List<String> kcvFailures = new ArrayList<>();

            for (KatVectorLoader.KcvVector v : vectors) {
                switch (v.type()) {
                    case "DEK_KCV" -> {
                        byte[] key = HexFormat.of().parseHex(v.keyHex());
                        AlgorithmId alg = AlgorithmId.valueOf(v.algorithm());
                        String actual = KeyCheckValue.computeDekKcv(key, alg);
                        if (!actual.equals(v.expectedKcvHex())) {
                            kcvFailures.add(v.id() + ": KCV mismatch");
                        }
                    }
                    case "HMAC_KCV" -> {
                        byte[] key = HexFormat.of().parseHex(v.keyHex());
                        String actual = KeyCheckValue.computeHmacKcv(key);
                        if (!actual.equals(v.expectedKcvHex())) {
                            kcvFailures.add(v.id() + ": HMAC KCV mismatch");
                        }
                    }
                    case "BINDING" -> {
                        byte[] hmacKey = HexFormat.of().parseHex(v.hmacKeyHex());
                        byte[] dek = HexFormat.of().parseHex(v.dekHex());
                        String actual = KeyCheckValue.computeBinding(hmacKey, dek);
                        if (!actual.equals(v.expectedBindingHex())) {
                            kcvFailures.add(v.id() + ": binding mismatch");
                        }
                    }
                    default -> { /* skip unknown types */ }
                }
            }

            long primMs = (System.nanoTime() - primStart) / 1_000_000;

            if (!kcvFailures.isEmpty()) {
                failures.addAll(kcvFailures);
                lastResults.put(name, new KatPrimitiveResult(name, false, primMs, String.join("; ", kcvFailures)));
            } else if (primMs > PER_PRIMITIVE_BUDGET_MS) {
                lastResults.put(name, new KatPrimitiveResult(name, true, primMs,
                        "exceeded per-primitive budget (advisory)"));
            } else {
                lastResults.put(name, new KatPrimitiveResult(name, true, primMs, null));
            }
        } catch (Exception e) {
            long primMs = (System.nanoTime() - primStart) / 1_000_000;
            failures.add(name + ": " + e.getMessage());
            lastResults.put(name, new KatPrimitiveResult(name, false, primMs, e.getMessage()));
        }
    }

    /**
     * Result of a single KAT primitive execution.
     */
    public record KatPrimitiveResult(
            String algorithm,
            boolean passed,
            long durationMs,
            String error
    ) {
    }
}
