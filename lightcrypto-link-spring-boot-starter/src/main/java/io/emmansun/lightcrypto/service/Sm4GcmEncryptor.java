package io.emmansun.lightcrypto.service;

import io.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.emmansun.lightcrypto.exception.CryptoException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * SM4-GCM encryptor implementation.
 * Uses 16-byte key (derived from first 16 bytes of DEK), 12-byte IV,
 * SM4/GCM/NoPadding cipher via Bouncy Castle.
 */
public class Sm4GcmEncryptor implements SymmetricEncryptor {

    private static final int SM4_KEY_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int ZERO_BLOCK_SIZE = 16;
    private static final String CIPHER_ALGORITHM = "SM4/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "SM4";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public byte[] encrypt(byte[] key, byte[] plaintext) {
        try {
            byte[] sm4Key = Arrays.copyOf(key, SM4_KEY_LENGTH);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(sm4Key, KEY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new CryptoException("SM4-GCM encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] data) {
        try {
            byte[] sm4Key = Arrays.copyOf(key, SM4_KEY_LENGTH);
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_LENGTH, data.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(sm4Key, KEY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoException("SM4-GCM decryption failed", e);
        }
    }

    @Override
    public String computeKcv(byte[] key) {
        try {
            byte[] sm4Key = Arrays.copyOf(key, SM4_KEY_LENGTH);
            byte[] zeroIv = new byte[IV_LENGTH];
            byte[] zeroBlock = new byte[ZERO_BLOCK_SIZE];

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(sm4Key, KEY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, zeroIv));

            byte[] encrypted = cipher.doFinal(zeroBlock);
            return HexFormat.of().formatHex(encrypted);
        } catch (Exception e) {
            throw new CryptoException("SM4-GCM KCV computation failed", e);
        }
    }

    @Override
    public SymmetricAlgorithm getAlgorithm() {
        return SymmetricAlgorithm.SM4_GCM;
    }
}
