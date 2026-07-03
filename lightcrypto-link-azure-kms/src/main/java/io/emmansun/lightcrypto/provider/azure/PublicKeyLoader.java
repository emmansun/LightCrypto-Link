package io.emmansun.lightcrypto.provider.azure;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utility to parse PEM-encoded X.509 SubjectPublicKeyInfo public keys.
 */
public final class PublicKeyLoader {

    private static final String PEM_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_END = "-----END PUBLIC KEY-----";

    private PublicKeyLoader() {
    }

    /**
     * Parse a PEM-encoded X.509 SubjectPublicKeyInfo public key.
     *
     * @param pem       the PEM string (with or without header/footer lines)
     * @param algorithm the key algorithm: {@code "RSA"} or {@code "SM2"}
     * @return the parsed {@link PublicKey}
     * @throws IllegalArgumentException if the PEM is invalid or the algorithm is unsupported
     */
    public static PublicKey loadFromPem(String pem, String algorithm) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM string must not be null or blank");
        }
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Algorithm must not be null or blank");
        }

        String keyFactoryAlgorithm;
        String normalized = algorithm.trim().toUpperCase();
        switch (normalized) {
            case "RSA" -> keyFactoryAlgorithm = "RSA";
            case "SM2" -> keyFactoryAlgorithm = "EC";
            default -> throw new IllegalArgumentException(
                    "Unsupported algorithm for public key loading: '" + algorithm
                            + "'. Supported: RSA, SM2");
        }

        try {
            String base64Content = pem
                    .replace(PEM_BEGIN, "")
                    .replace(PEM_END, "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(base64Content);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(keyFactoryAlgorithm);
            return keyFactory.generatePublic(keySpec);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse " + algorithm + " public key PEM: " + e.getMessage(), e);
        }
    }
}
