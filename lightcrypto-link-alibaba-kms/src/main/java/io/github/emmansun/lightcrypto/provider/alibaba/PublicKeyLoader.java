package io.github.emmansun.lightcrypto.provider.alibaba;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utility for loading X.509 SubjectPublicKeyInfo PEM-encoded public keys.
 * <p>
 * Supports RSA and EC (SM2) key types. The key algorithm is auto-detected
 * from the X.509 encoded key material — no explicit algorithm parameter required.
 * </p>
 */
public final class PublicKeyLoader {

    private static final String PEM_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_END = "-----END PUBLIC KEY-----";

    private PublicKeyLoader() {
        // utility class
    }

    /**
     * Parse a PEM-encoded X.509 SubjectPublicKeyInfo public key.
     * The key type (RSA or EC/SM2) is auto-detected from the ASN.1 structure.
     *
     * @param pem the PEM string (with or without header/footer lines)
     * @return the parsed {@link PublicKey}
     * @throws IllegalArgumentException if the PEM is invalid or the key type is unrecognized
     */
    public static PublicKey loadFromPem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM string must not be null or blank");
        }

        try {
            String base64Content = pem
                    .replace(PEM_BEGIN, "")
                    .replace(PEM_END, "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(base64Content);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);

            // Try RSA first, then EC (SM2)
            try {
                return KeyFactory.getInstance("RSA").generatePublic(keySpec);
            } catch (Exception ignored) {
                // Not an RSA key, try EC
            }

            try {
                return KeyFactory.getInstance("EC").generatePublic(keySpec);
            } catch (Exception ignored) {
                // Not an EC key either
            }

            throw new IllegalArgumentException(
                    "Unsupported public key type: key is neither RSA nor EC (SM2)");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse public key PEM: " + e.getMessage(), e);
        }
    }
}
