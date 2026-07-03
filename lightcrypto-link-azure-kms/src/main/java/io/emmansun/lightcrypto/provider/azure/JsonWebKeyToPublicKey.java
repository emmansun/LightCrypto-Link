package io.emmansun.lightcrypto.provider.azure;

import com.azure.security.keyvault.keys.models.JsonWebKey;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Utility to convert Azure Key Vault {@link JsonWebKey} to a Java {@link PublicKey}.
 */
public final class JsonWebKeyToPublicKey {

    private JsonWebKeyToPublicKey() {
    }

    /**
     * Converts a JSON Web Key (JWK) containing RSA parameters to a Java {@link PublicKey}.
     *
     * @param jwk the JSON Web Key from Azure Key Vault
     * @return the RSA public key
     * @throws IllegalArgumentException if the JWK is null, not RSA, or missing n/e
     */
    public static PublicKey convert(JsonWebKey jwk) {
        if (jwk == null) {
            throw new IllegalArgumentException("JsonWebKey must not be null");
        }
        byte[] nBytes = jwk.getN();
        byte[] eBytes = jwk.getE();
        if (nBytes == null || eBytes == null) {
            throw new IllegalArgumentException("JsonWebKey does not contain RSA public key parameters (n, e)");
        }

        try {
            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to convert JsonWebKey to RSA PublicKey: " + ex.getMessage(), ex);
        }
    }
}
