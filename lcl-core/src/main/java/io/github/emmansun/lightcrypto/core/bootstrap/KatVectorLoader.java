package io.github.emmansun.lightcrypto.core.bootstrap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads KAT (Known Answer Test) vectors from classpath resources.
 * <p>
 * Vectors are stored as JSON arrays under {@code /kat/} in the classpath.
 *
 * @since 1.0.0
 */
public final class KatVectorLoader {

    private static final Gson GSON = new Gson();

    private KatVectorLoader() {
    }

    /**
     * Loads encryption KAT vectors from the given classpath resource.
     *
     * @param resourcePath the classpath resource path (e.g., "/kat/encryption/aes-256-gcm.json")
     * @return list of encryption vector entries
     * @throws KatVectorException if the resource is missing or malformed
     */
    public static List<EncryptionVector> loadEncryptionVectors(String resourcePath) {
        JsonArray array = loadJsonArray(resourcePath);
        List<EncryptionVector> vectors = new ArrayList<>();
        for (JsonElement element : array) {
            var obj = element.getAsJsonObject();
            String id = obj.get("id").getAsString();
            String algorithm = obj.get("algorithm").getAsString();
            var input = obj.getAsJsonObject("input");
            var expected = obj.getAsJsonObject("expected");

            vectors.add(new EncryptionVector(
                    id,
                    algorithm,
                    input.get("keyHex").getAsString(),
                    input.get("plaintextHex").getAsString(),
                    input.get("namespace").getAsString(),
                    input.get("dekVersion").getAsInt(),
                    input.get("ivHex").getAsString(),
                    expected.get("ciphertextHex").getAsString()
            ));
        }
        return vectors;
    }

    /**
     * Loads blind index KAT vectors from the given classpath resource.
     *
     * @param resourcePath the classpath resource path
     * @return list of blind index vector entries
     * @throws KatVectorException if the resource is missing or malformed
     */
    public static List<BlindIndexVector> loadBlindIndexVectors(String resourcePath) {
        JsonArray array = loadJsonArray(resourcePath);
        List<BlindIndexVector> vectors = new ArrayList<>();
        for (JsonElement element : array) {
            var obj = element.getAsJsonObject();
            String id = obj.get("id").getAsString();
            var input = obj.getAsJsonObject("input");
            var expected = obj.getAsJsonObject("expected");

            vectors.add(new BlindIndexVector(
                    id,
                    input.get("masterHmacKeyHex").getAsString(),
                    input.get("namespace").getAsString(),
                    input.get("fieldName").getAsString(),
                    input.get("plaintext").getAsString(),
                    expected.get("blindIndexBase64url").getAsString()
            ));
        }
        return vectors;
    }

    /**
     * Loads KCV vectors from the given classpath resource.
     *
     * @param resourcePath the classpath resource path
     * @return list of KCV vector entries
     * @throws KatVectorException if the resource is missing or malformed
     */
    public static List<KcvVector> loadKcvVectors(String resourcePath) {
        JsonArray array = loadJsonArray(resourcePath);
        List<KcvVector> vectors = new ArrayList<>();
        for (JsonElement element : array) {
            var obj = element.getAsJsonObject();
            String id = obj.get("id").getAsString();
            String type = obj.get("type").getAsString();
            String algorithm = obj.has("algorithm") ? obj.get("algorithm").getAsString() : null;
            String keyHex = obj.has("keyHex") ? obj.get("keyHex").getAsString() : null;
            String hmacKeyHex = obj.has("hmacKeyHex") ? obj.get("hmacKeyHex").getAsString() : null;
            String dekHex = obj.has("dekHex") ? obj.get("dekHex").getAsString() : null;
            String expectedKcvHex = obj.has("expectedKcvHex") ? obj.get("expectedKcvHex").getAsString() : null;
            String expectedBindingHex = obj.has("expectedBindingHex") ? obj.get("expectedBindingHex").getAsString() : null;

            vectors.add(new KcvVector(id, type, algorithm, keyHex, hmacKeyHex, dekHex, expectedKcvHex, expectedBindingHex));
        }
        return vectors;
    }

    private static JsonArray loadJsonArray(String resourcePath) {
        try (InputStream is = KatVectorLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new KatVectorException("KAT vector file not found on classpath: " + resourcePath);
            }
            return JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (KatVectorException e) {
            throw e;
        } catch (IOException e) {
            throw new KatVectorException("Failed to read KAT vector file: " + resourcePath, e);
        } catch (Exception e) {
            throw new KatVectorException("Failed to parse KAT vector file: " + resourcePath, e);
        }
    }

    /**
     * An encryption KAT vector entry.
     */
    public record EncryptionVector(
            String id, String algorithm, String keyHex, String plaintextHex,
            String namespace, int dekVersion, String ivHex, String expectedCiphertextHex
    ) {
    }

    /**
     * A blind index KAT vector entry.
     */
    public record BlindIndexVector(
            String id, String masterHmacKeyHex, String namespace,
            String fieldName, String plaintext, String expectedBlindIndexBase64url
    ) {
    }

    /**
     * A KCV vector entry.
     */
    public record KcvVector(
            String id, String type, String algorithm, String keyHex,
            String hmacKeyHex, String dekHex, String expectedKcvHex, String expectedBindingHex
    ) {
    }

    /**
     * Exception thrown when KAT vector loading fails.
     */
    public static class KatVectorException extends RuntimeException {
        public KatVectorException(String message) {
            super(message);
        }

        public KatVectorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
