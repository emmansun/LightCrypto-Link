package io.github.emmansun.lightcrypto.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.crypto.SymmetricEncryptor;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.format.WireFormatEncoder;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates all golden test vectors in the vectors/ directory.
 * This test ensures cross-language correctness of the Wire Format V1 implementation.
 */
class VectorSuiteTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final Gson GSON = new Gson();
    private static Path vectorsRoot;

    @BeforeAll
    static void locateVectors() {
        Path moduleDir = Path.of("").toAbsolutePath();
        vectorsRoot = moduleDir.getParent().resolve("vectors");
        assertThat(vectorsRoot).exists();
    }

    @Test
    void manifestIntegrity() throws Exception {
        String manifestContent = Files.readString(vectorsRoot.resolve("manifest.json"));
        JsonObject manifest = JsonParser.parseString(manifestContent).getAsJsonObject();

        assertThat(manifest.get("vectorSuiteVersion").getAsString()).isEqualTo("1.0.0");
        assertThat(manifest.get("wireFormatVersion").getAsInt()).isEqualTo(1);

        JsonArray files = manifest.getAsJsonArray("files");
        assertThat(files.size()).isGreaterThanOrEqualTo(7);

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (JsonElement elem : files) {
            JsonObject fileEntry = elem.getAsJsonObject();
            String path = fileEntry.get("path").getAsString();
            String expectedHash = fileEntry.get("sha256").getAsString();

            Path filePath = vectorsRoot.resolve(path);
            assertThat(filePath).exists();

            byte[] content = Files.readAllBytes(filePath);
            String actualHash = HEX.formatHex(sha256.digest(content));
            assertThat(actualHash).as("SHA-256 of " + path).isEqualTo(expectedHash);
        }
    }

    @Test
    void encryptionVectors() throws Exception {
        String[] files = {"encryption/aes-256-gcm.json", "encryption/aes-256-cbc.json",
                "encryption/sm4-gcm.json", "encryption/sm4-cbc.json"};

        int totalVectors = 0;
        for (String file : files) {
            JsonArray vectors = readVectorFile(file);
            assertThat(vectors.size()).as("vectors in " + file).isGreaterThanOrEqualTo(5);

            for (JsonElement elem : vectors) {
                JsonObject vector = elem.getAsJsonObject();
                String id = vector.get("id").getAsString();
                String algName = vector.get("algorithm").getAsString();
                AlgorithmId alg = AlgorithmId.valueOf(algName);

                JsonObject input = vector.getAsJsonObject("input");
                byte[] key = HEX.parseHex(input.get("keyHex").getAsString());
                byte[] plaintext = HEX.parseHex(input.get("plaintextHex").getAsString());
                String namespace = input.get("namespace").getAsString();
                int dekVersion = input.get("dekVersion").getAsInt();
                byte[] iv = HEX.parseHex(input.get("ivHex").getAsString());

                JsonObject expected = vector.getAsJsonObject("expected");
                String expectedWireHex = expected.get("wireFormatHex").getAsString();
                String expectedCtHex = expected.get("ciphertextHex").getAsString();

                // Verify encryption with fixed IV produces expected wire format
                byte[] aad = alg.isGcm()
                        ? WireFormatEncoder.buildAad(alg, namespace, dekVersion)
                        : new byte[0];
                SymmetricEncryptor encryptor = CryptoCodec.getEncryptor(alg);
                byte[] ct = encryptor.encrypt(key, iv, plaintext, aad);
                byte[] blob = WireFormatEncoder.encode(alg, namespace, dekVersion, iv, ct);

                assertThat(HEX.formatHex(blob)).as(id + " wireFormat").isEqualTo(expectedWireHex);
                assertThat(HEX.formatHex(ct)).as(id + " ciphertext").isEqualTo(expectedCtHex);

                // Verify decryption of the wire format blob
                byte[] decrypted = CryptoCodec.decrypt(key,
                        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(blob));
                assertThat(decrypted).as(id + " decrypt").isEqualTo(plaintext);

                totalVectors++;
            }
        }
        assertThat(totalVectors).isGreaterThanOrEqualTo(20);
    }

    @Test
    void blindIndexVectors() throws Exception {
        JsonArray vectors = readVectorFile("blind-index/hmac-sha256.json");
        assertThat(vectors.size()).isGreaterThanOrEqualTo(5);

        for (JsonElement elem : vectors) {
            JsonObject vector = elem.getAsJsonObject();
            String id = vector.get("id").getAsString();

            JsonObject input = vector.getAsJsonObject("input");
            byte[] masterKey = HEX.parseHex(input.get("masterHmacKeyHex").getAsString());
            String namespace = input.get("namespace").getAsString();
            String fieldName = input.get("fieldName").getAsString();
            String plaintext = input.get("plaintext").getAsString();

            JsonObject expected = vector.getAsJsonObject("expected");
            String expectedBlindIndex = expected.get("blindIndexBase64url").getAsString();

            BlindIndexEngine engine = new BlindIndexEngine(masterKey);
            Namespace ns = Namespace.parse(namespace);
            String actual = engine.computeBlindIndex(ns, fieldName, plaintext);

            assertThat(actual).as(id + " blindIndex").isEqualTo(expectedBlindIndex);
        }
    }

    @Test
    void kcvVectors() throws Exception {
        JsonArray vectors = readVectorFile("kcv/kcv.json");
        assertThat(vectors.size()).isGreaterThanOrEqualTo(4);

        for (JsonElement elem : vectors) {
            JsonObject vector = elem.getAsJsonObject();
            String id = vector.get("id").getAsString();
            String type = vector.get("type").getAsString();

            switch (type) {
                case "DEK_KCV" -> {
                    String algName = vector.get("algorithm").getAsString();
                    AlgorithmId alg = AlgorithmId.valueOf(algName);
                    byte[] key = HEX.parseHex(vector.get("keyHex").getAsString());
                    String expectedKcv = vector.get("expectedKcvHex").getAsString();

                    String actual = KeyCheckValue.computeDekKcv(key, alg);
                    assertThat(actual).as(id + " DEK KCV").isEqualTo(expectedKcv);
                }
                case "HMAC_KCV" -> {
                    byte[] key = HEX.parseHex(vector.get("keyHex").getAsString());
                    String expectedKcv = vector.get("expectedKcvHex").getAsString();

                    String actual = KeyCheckValue.computeHmacKcv(key);
                    assertThat(actual).as(id + " HMAC KCV").isEqualTo(expectedKcv);
                }
                case "BINDING" -> {
                    byte[] hmacKey = HEX.parseHex(vector.get("hmacKeyHex").getAsString());
                    byte[] dek = HEX.parseHex(vector.get("dekHex").getAsString());
                    String expectedBinding = vector.get("expectedBindingHex").getAsString();

                    String actual = KeyCheckValue.computeBinding(hmacKey, dek);
                    assertThat(actual).as(id + " binding").isEqualTo(expectedBinding);
                }
            }
        }
    }

    @Test
    void roundtripVectors() throws Exception {
        JsonArray vectors = readVectorFile("roundtrip/roundtrip.json");
        assertThat(vectors.size()).isGreaterThanOrEqualTo(4);

        for (JsonElement elem : vectors) {
            JsonObject vector = elem.getAsJsonObject();
            String id = vector.get("id").getAsString();

            JsonObject input = vector.getAsJsonObject("input");
            byte[] key = HEX.parseHex(input.get("keyHex").getAsString());
            byte[] plaintext = HEX.parseHex(input.get("plaintextHex").getAsString());

            JsonObject expected = vector.getAsJsonObject("expected");
            String blob = expected.get("wireFormatBase64url").getAsString();
            boolean expectedOk = expected.get("roundtripOk").getAsBoolean();

            byte[] decrypted = CryptoCodec.decrypt(key, blob);
            boolean actualOk = java.util.Arrays.equals(plaintext, decrypted);

            assertThat(actualOk).as(id + " roundtrip").isEqualTo(expectedOk).isTrue();
        }
    }

    private JsonArray readVectorFile(String relativePath) throws Exception {
        Path filePath = vectorsRoot.resolve(relativePath);
        assertThat(filePath).as("vector file " + relativePath).exists();
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return JsonParser.parseString(content).getAsJsonArray();
    }
}
