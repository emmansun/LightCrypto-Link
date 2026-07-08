package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.util.CryptoUtils;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * AES-256-CBC encryptor implementation.
 * Uses 16-byte IV, AES/CBC/PKCS5Padding cipher.
 */
public class AesCbcEncryptor implements SymmetricEncryptor {

    private static final int IV_LENGTH = 16;
    private static final int ZERO_BLOCK_SIZE = 16;
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    @Override
    public byte[] encrypt(byte[] key, byte[] plaintext) {
        try {
            byte[] iv = CryptoUtils.generateRandomBytes(IV_LENGTH);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, KEY_ALGORITHM),
                    new IvParameterSpec(iv));

            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new CryptoException("AES-256-CBC encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] data) {
        try {
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_LENGTH, data.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, KEY_ALGORITHM),
                    new IvParameterSpec(iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoException("AES-256-CBC decryption failed", e);
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
                    new IvParameterSpec(zeroIv));

            byte[] encrypted = cipher.doFinal(zeroBlock);
            return HexFormat.of().formatHex(encrypted);
        } catch (Exception e) {
            throw new CryptoException("AES-256-CBC KCV computation failed", e);
        }
    }

    @Override
    public SymmetricAlgorithm getAlgorithm() {
        return SymmetricAlgorithm.AES_256_CBC;
    }
}
