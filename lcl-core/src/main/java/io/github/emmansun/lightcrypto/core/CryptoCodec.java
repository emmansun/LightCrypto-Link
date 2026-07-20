package io.github.emmansun.lightcrypto.core;

import io.github.emmansun.lightcrypto.core.crypto.AesCbcEncryptor;
import io.github.emmansun.lightcrypto.core.crypto.AesGcmEncryptor;
import io.github.emmansun.lightcrypto.core.crypto.Sm4CbcEncryptor;
import io.github.emmansun.lightcrypto.core.crypto.Sm4GcmEncryptor;
import io.github.emmansun.lightcrypto.core.crypto.SymmetricEncryptor;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.format.WireFormatDecoder;
import io.github.emmansun.lightcrypto.core.format.WireFormatEncoder;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;

import java.security.SecureRandom;
import java.util.Map;

/**
 * Stateless, purely functional cryptographic codec for Wire Format V1.
 *
 * <p>All methods accept explicit parameters and return results without side effects.
 * No references to key vaults, databases, or configuration objects are held.
 *
 * <p>Usage:
 * <pre>{@code
 * String blob = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, namespace, 1);
 * byte[] plain = CryptoCodec.decrypt(dek, blob);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class CryptoCodec {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<AlgorithmId, SymmetricEncryptor> ENCRYPTORS = Map.of(
            AlgorithmId.AES_256_GCM, new AesGcmEncryptor(),
            AlgorithmId.AES_256_CBC, new AesCbcEncryptor(),
            AlgorithmId.SM4_GCM, new Sm4GcmEncryptor(),
            AlgorithmId.SM4_CBC, new Sm4CbcEncryptor()
    );

    private CryptoCodec() {
    }

    /**
     * Encrypts plaintext into a Base64URL-encoded Wire Format V1 string.
     *
     * @param dek        the data encryption key
     * @param plaintext  the data to encrypt
     * @param algorithm  the symmetric algorithm to use
     * @param namespace  the namespace (canonical form used in blob and AAD)
     * @param dekVersion the DEK version (must be ≥ 1)
     * @return Base64URL-encoded (no padding) Wire Format V1 blob
     */
    public static String encrypt(byte[] dek, byte[] plaintext, AlgorithmId algorithm,
                                 Namespace namespace, int dekVersion) {
        String canonicalNs = namespace.canonical();
        byte[] iv = new byte[algorithm.ivLength()];
        RANDOM.nextBytes(iv);

        byte[] aad = WireFormatEncoder.buildAad(algorithm, canonicalNs, dekVersion);
        SymmetricEncryptor encryptor = getEncryptor(algorithm);
        byte[] ciphertext = encryptor.encrypt(dek, iv, plaintext, aad);

        return WireFormatEncoder.encodeToBase64Url(algorithm, canonicalNs, dekVersion, iv, ciphertext);
    }

    /**
     * Decrypts a Base64URL-encoded Wire Format V1 blob.
     *
     * @param dek            the data encryption key (must match the key used for encryption)
     * @param wireFormatBlob the Base64URL-encoded Wire Format V1 string
     * @return the decrypted plaintext bytes
     */
    public static byte[] decrypt(byte[] dek, String wireFormatBlob) {
        WireFormatDecoder.DecodedBlob decoded = WireFormatDecoder.decodeFromBase64Url(wireFormatBlob);

        byte[] aad = decoded.reconstructAad();
        SymmetricEncryptor encryptor = getEncryptor(decoded.algorithm());
        return encryptor.decrypt(dek, decoded.iv(), decoded.ciphertext(), aad);
    }

    /**
     * Encrypts plaintext with a raw namespace string (parsed to canonical form).
     *
     * @param dek        the data encryption key
     * @param plaintext  the data to encrypt
     * @param algorithm  the symmetric algorithm to use
     * @param namespace  the namespace string (shorthand or full form)
     * @param dekVersion the DEK version
     * @return Base64URL-encoded Wire Format V1 blob
     */
    public static String encrypt(byte[] dek, byte[] plaintext, AlgorithmId algorithm,
                                 String namespace, int dekVersion) {
        return encrypt(dek, plaintext, algorithm, Namespace.parse(namespace), dekVersion);
    }

    /**
     * Returns the SymmetricEncryptor for the given algorithm.
     *
     * @param algorithm the algorithm identifier
     * @return the corresponding encryptor
     */
    public static SymmetricEncryptor getEncryptor(AlgorithmId algorithm) {
        SymmetricEncryptor encryptor = ENCRYPTORS.get(algorithm);
        if (encryptor == null) {
            throw new IllegalArgumentException("No encryptor registered for algorithm: " + algorithm);
        }
        return encryptor;
    }
}
