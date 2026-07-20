package io.github.emmansun.lightcrypto.core;

import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.crypto.SymmetricEncryptor;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.format.WireFormatEncoder;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Generates golden test vectors for the Vector Suite.
 * Run this test to regenerate vectors/ JSON files when the crypto implementation changes.
 *
 * <p>All vectors use FIXED keys and IVs for deterministic, cross-language verification.
 * Files are written to the repository-root vectors/ directory.
 */
class VectorGeneratorTest {

    // Fixed test keys (DO NOT use in production)
    private static final byte[] AES_256_KEY = HexFormat.of().parseHex(
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    private static final byte[] SM4_KEY = HexFormat.of().parseHex(
            "000102030405060708090a0b0c0d0e0f");
    private static final byte[] HMAC_KEY = HexFormat.of().parseHex(
            "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f");
    private static final byte[] GCM_IV = HexFormat.of().parseHex("000102030405060708090a0b");
    private static final byte[] CBC_IV = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f");

    private static final HexFormat HEX = HexFormat.of();

    /** Resolves the vectors/ directory at the repository root (parent of lcl-core module). */
    private static Path vectorsRoot() {
        // During test execution, working dir is the module dir (lcl-core/)
        Path moduleDir = Path.of("").toAbsolutePath();
        Path repoRoot = moduleDir.getParent();
        return repoRoot.resolve("vectors");
    }

    @Test
    void generateAllVectors() throws Exception {
        Path root = vectorsRoot();
        Files.createDirectories(root.resolve("encryption"));
        Files.createDirectories(root.resolve("blind-index"));
        Files.createDirectories(root.resolve("kcv"));
        Files.createDirectories(root.resolve("roundtrip"));

        String aesGcm = generateEncryptionVectors(AlgorithmId.AES_256_GCM, AES_256_KEY, GCM_IV, "enc-aes256gcm");
        String aesCbc = generateEncryptionVectors(AlgorithmId.AES_256_CBC, AES_256_KEY, CBC_IV, "enc-aes256cbc");
        String sm4Gcm = generateEncryptionVectors(AlgorithmId.SM4_GCM, SM4_KEY, GCM_IV, "enc-sm4gcm");
        String sm4Cbc = generateEncryptionVectors(AlgorithmId.SM4_CBC, SM4_KEY, CBC_IV, "enc-sm4cbc");

        Files.writeString(root.resolve("encryption/aes-256-gcm.json"), aesGcm);
        Files.writeString(root.resolve("encryption/aes-256-cbc.json"), aesCbc);
        Files.writeString(root.resolve("encryption/sm4-gcm.json"), sm4Gcm);
        Files.writeString(root.resolve("encryption/sm4-cbc.json"), sm4Cbc);

        String blindIndex = generateBlindIndexVectors();
        Files.writeString(root.resolve("blind-index/hmac-sha256.json"), blindIndex);

        String kcv = generateKcvVectors();
        Files.writeString(root.resolve("kcv/kcv.json"), kcv);

        String roundtrip = generateRoundtripVectors();
        Files.writeString(root.resolve("roundtrip/roundtrip.json"), roundtrip);

        // Generate manifest with SHA-256 hashes
        String manifest = generateManifest(root);
        Files.writeString(root.resolve("manifest.json"), manifest);

        System.out.println("Vector suite generated at: " + root);
    }

    private String generateEncryptionVectors(AlgorithmId alg, byte[] key, byte[] iv, String idPrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        SymmetricEncryptor enc = CryptoCodec.getEncryptor(alg);

        for (int i = 0; i < 5; i++) {
            byte[] plaintext = ("test-plaintext-" + alg.name() + "-" + i).getBytes(StandardCharsets.UTF_8);
            String ns = "default.default.User#field" + i;
            int dekVersion = 1;

            byte[] aad = alg.isGcm() ? WireFormatEncoder.buildAad(alg, ns, dekVersion) : new byte[0];
            byte[] ct = enc.encrypt(key, iv, plaintext, aad);
            byte[] blob = WireFormatEncoder.encode(alg, ns, dekVersion, iv, ct);

            if (i > 0) sb.append(",\n");
            sb.append("  {\n");
            sb.append(String.format("    \"id\": \"%s-%03d\",\n", idPrefix, i + 1));
            sb.append(String.format("    \"algorithm\": \"%s\",\n", alg.name()));
            sb.append(String.format("    \"algorithmId\": %d,\n", alg.id() & 0xFF));
            sb.append("    \"input\": {\n");
            sb.append(String.format("      \"keyHex\": \"%s\",\n", HEX.formatHex(key)));
            sb.append(String.format("      \"plaintextHex\": \"%s\",\n", HEX.formatHex(plaintext)));
            sb.append(String.format("      \"namespace\": \"%s\",\n", ns));
            sb.append(String.format("      \"dekVersion\": %d,\n", dekVersion));
            sb.append(String.format("      \"ivHex\": \"%s\"\n", HEX.formatHex(iv)));
            sb.append("    },\n");
            sb.append("    \"expected\": {\n");
            sb.append(String.format("      \"wireFormatHex\": \"%s\",\n", HEX.formatHex(blob)));
            sb.append(String.format("      \"ciphertextHex\": \"%s\"\n", HEX.formatHex(ct)));
            sb.append("    }\n");
            sb.append("  }");
        }

        sb.append("\n]\n");
        return sb.toString();
    }

    private String generateBlindIndexVectors() throws IOException {
        BlindIndexEngine engine = new BlindIndexEngine(HMAC_KEY);
        StringBuilder sb = new StringBuilder("[\n");

        String[][] cases = {
                {"default.default.User#phone", "phone", "13800138000", "trim+lowercase"},
                {"default.default.User#email", "email", "  Test@Example.COM  ", "trim+lowercase"},
                {"tenantA.app.User#ssn", "ssn", "123-45-6789", "trim+lowercase"},
                {"tenantB.app.User#ssn", "ssn", "123-45-6789", "trim+lowercase"},
                {"default.default.Order#total", "total", "99.99", "trim+lowercase"},
        };

        for (int i = 0; i < cases.length; i++) {
            Namespace ns = Namespace.parse(cases[i][0]);
            String index = engine.computeBlindIndex(ns, cases[i][1], cases[i][2]);
            byte[] derivedKey = computeDerivedKey(HMAC_KEY, ns);

            if (i > 0) sb.append(",\n");
            sb.append("  {\n");
            sb.append(String.format("    \"id\": \"bi-%03d\",\n", i + 1));
            sb.append("    \"input\": {\n");
            sb.append(String.format("      \"masterHmacKeyHex\": \"%s\",\n", HEX.formatHex(HMAC_KEY)));
            sb.append(String.format("      \"namespace\": \"%s\",\n", ns.canonical()));
            sb.append(String.format("      \"fieldName\": \"%s\",\n", cases[i][1]));
            sb.append(String.format("      \"plaintext\": \"%s\",\n", cases[i][2]));
            sb.append(String.format("      \"normalization\": \"%s\"\n", cases[i][3]));
            sb.append("    },\n");
            sb.append("    \"expected\": {\n");
            sb.append(String.format("      \"derivedHmacKeyHex\": \"%s\",\n", HEX.formatHex(derivedKey)));
            sb.append(String.format("      \"blindIndexBase64url\": \"%s\"\n", index));
            sb.append("    }\n");
            sb.append("  }");
        }

        sb.append("\n]\n");
        return sb.toString();
    }

    private String generateKcvVectors() {
        StringBuilder sb = new StringBuilder("[\n");

        String dekKcvAesGcm = KeyCheckValue.computeDekKcv(AES_256_KEY, AlgorithmId.AES_256_GCM);
        String dekKcvAesCbc = KeyCheckValue.computeDekKcv(AES_256_KEY, AlgorithmId.AES_256_CBC);
        String dekKcvSm4Gcm = KeyCheckValue.computeDekKcv(SM4_KEY, AlgorithmId.SM4_GCM);
        String dekKcvSm4Cbc = KeyCheckValue.computeDekKcv(SM4_KEY, AlgorithmId.SM4_CBC);
        String hmacKcv = KeyCheckValue.computeHmacKcv(HMAC_KEY);
        String binding = KeyCheckValue.computeBinding(HMAC_KEY, AES_256_KEY);

        sb.append("  {\n");
        sb.append("    \"id\": \"kcv-aes256gcm-dek\",\n");
        sb.append("    \"type\": \"DEK_KCV\",\n");
        sb.append(String.format("    \"algorithm\": \"AES_256_GCM\",\n"));
        sb.append(String.format("    \"keyHex\": \"%s\",\n", HEX.formatHex(AES_256_KEY)));
        sb.append(String.format("    \"expectedKcvHex\": \"%s\"\n", dekKcvAesGcm));
        sb.append("  },\n");

        sb.append("  {\n");
        sb.append("    \"id\": \"kcv-aes256cbc-dek\",\n");
        sb.append("    \"type\": \"DEK_KCV\",\n");
        sb.append(String.format("    \"algorithm\": \"AES_256_CBC\",\n"));
        sb.append(String.format("    \"keyHex\": \"%s\",\n", HEX.formatHex(AES_256_KEY)));
        sb.append(String.format("    \"expectedKcvHex\": \"%s\"\n", dekKcvAesCbc));
        sb.append("  },\n");

        sb.append("  {\n");
        sb.append("    \"id\": \"kcv-sm4gcm-dek\",\n");
        sb.append("    \"type\": \"DEK_KCV\",\n");
        sb.append(String.format("    \"algorithm\": \"SM4_GCM\",\n"));
        sb.append(String.format("    \"keyHex\": \"%s\",\n", HEX.formatHex(SM4_KEY)));
        sb.append(String.format("    \"expectedKcvHex\": \"%s\"\n", dekKcvSm4Gcm));
        sb.append("  },\n");

        sb.append("  {\n");
        sb.append("    \"id\": \"kcv-sm4cbc-dek\",\n");
        sb.append("    \"type\": \"DEK_KCV\",\n");
        sb.append(String.format("    \"algorithm\": \"SM4_CBC\",\n"));
        sb.append(String.format("    \"keyHex\": \"%s\",\n", HEX.formatHex(SM4_KEY)));
        sb.append(String.format("    \"expectedKcvHex\": \"%s\"\n", dekKcvSm4Cbc));
        sb.append("  },\n");

        sb.append("  {\n");
        sb.append("    \"id\": \"kcv-hmac\",\n");
        sb.append("    \"type\": \"HMAC_KCV\",\n");
        sb.append(String.format("    \"keyHex\": \"%s\",\n", HEX.formatHex(HMAC_KEY)));
        sb.append(String.format("    \"expectedKcvHex\": \"%s\"\n", hmacKcv));
        sb.append("  },\n");

        sb.append("  {\n");
        sb.append("    \"id\": \"kcv-binding\",\n");
        sb.append("    \"type\": \"BINDING\",\n");
        sb.append(String.format("    \"hmacKeyHex\": \"%s\",\n", HEX.formatHex(HMAC_KEY)));
        sb.append(String.format("    \"dekHex\": \"%s\",\n", HEX.formatHex(AES_256_KEY)));
        sb.append(String.format("    \"expectedBindingHex\": \"%s\"\n", binding));
        sb.append("  }");

        sb.append("\n]\n");
        return sb.toString();
    }

    private String generateRoundtripVectors() {
        StringBuilder sb = new StringBuilder("[\n");
        AlgorithmId[] algs = AlgorithmId.values();

        for (int i = 0; i < algs.length; i++) {
            AlgorithmId alg = algs[i];
            byte[] key = alg.keyLength() == 32 ? AES_256_KEY : SM4_KEY;
            byte[] plaintext = ("roundtrip-" + alg.name()).getBytes(StandardCharsets.UTF_8);
            Namespace ns = Namespace.parse("default.default.Test#" + alg.name());

            String blob = CryptoCodec.encrypt(key, plaintext, alg, ns, 1);
            byte[] decrypted = CryptoCodec.decrypt(key, blob);
            boolean ok = java.util.Arrays.equals(plaintext, decrypted);

            if (i > 0) sb.append(",\n");
            sb.append("  {\n");
            sb.append(String.format("    \"id\": \"rt-%s\",\n", alg.name().toLowerCase().replace('_', '-')));
            sb.append(String.format("    \"algorithm\": \"%s\",\n", alg.name()));
            sb.append(String.format("    \"algorithmId\": %d,\n", alg.id() & 0xFF));
            sb.append("    \"input\": {\n");
            sb.append(String.format("      \"keyHex\": \"%s\",\n", HEX.formatHex(key)));
            sb.append(String.format("      \"plaintextHex\": \"%s\",\n", HEX.formatHex(plaintext)));
            sb.append(String.format("      \"namespace\": \"%s\",\n", ns.canonical()));
            sb.append("      \"dekVersion\": 1\n");
            sb.append("    },\n");
            sb.append("    \"expected\": {\n");
            sb.append(String.format("      \"wireFormatBase64url\": \"%s\",\n", blob));
            sb.append(String.format("      \"roundtripOk\": %s\n", ok));
            sb.append("    }\n");
            sb.append("  }");
        }

        sb.append("\n]\n");
        return sb.toString();
    }

    private String generateManifest(Path root) throws Exception {
        String[] files = {
                "encryption/aes-256-gcm.json",
                "encryption/aes-256-cbc.json",
                "encryption/sm4-gcm.json",
                "encryption/sm4-cbc.json",
                "blind-index/hmac-sha256.json",
                "kcv/kcv.json",
                "roundtrip/roundtrip.json"
        };

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"vectorSuiteVersion\": \"1.0.0\",\n");
        sb.append("  \"wireFormatVersion\": 1,\n");
        sb.append("  \"interopVersion\": 1,\n");
        sb.append(String.format("  \"generatedAt\": \"%s\",\n", Instant.now().toString()));
        sb.append("  \"files\": [\n");

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < files.length; i++) {
            byte[] content = Files.readAllBytes(root.resolve(files[i]));
            String hash = HEX.formatHex(sha256.digest(content));
            sb.append(String.format("    {\"path\": \"%s\", \"sha256\": \"%s\"}", files[i], hash));
            if (i < files.length - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Replicates HKDF derivation for vector generation (same as BlindIndexEngine.deriveKey). */
    private static byte[] computeDerivedKey(byte[] masterKey, Namespace namespace) {
        try {
            byte[] salt = MessageDigest.getInstance("SHA-256")
                    .digest(namespace.canonicalBytes());
            byte[] info = "lcl-blind-index-v1".getBytes(StandardCharsets.UTF_8);

            HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
            hkdf.init(new HKDFParameters(masterKey, salt, info));

            byte[] derivedKey = new byte[32];
            hkdf.generateBytes(derivedKey, 0, 32);
            return derivedKey;
        } catch (Exception e) {
            throw new IllegalStateException("HKDF key derivation failed", e);
        }
    }
}
