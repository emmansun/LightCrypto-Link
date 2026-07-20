package io.github.emmansun.lightcrypto.core.crypto;

import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.exception.CryptoException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

/**
 * AES-256-GCM encryptor with AAD support.
 *
 * @since 1.0.0
 */
public class AesGcmEncryptor implements SymmetricEncryptor {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;
    private static final int ZERO_BLOCK_SIZE = 16;
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";

    @Override
    public byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new CryptoException("AES-256-GCM encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoException("AES-256-GCM decryption failed", e);
        }
    }

    @Override
    public String computeKcv(byte[] key) {
        try {
            byte[] zeroIv = new byte[IV_LENGTH];
            byte[] zeroBlock = new byte[ZERO_BLOCK_SIZE];
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, zeroIv));
            byte[] encrypted = cipher.doFinal(zeroBlock);
            return HexFormat.of().formatHex(encrypted);
        } catch (Exception e) {
            throw new CryptoException("AES-256-GCM KCV computation failed", e);
        }
    }

    @Override
    public AlgorithmId algorithmId() {
        return AlgorithmId.AES_256_GCM;
    }
}
