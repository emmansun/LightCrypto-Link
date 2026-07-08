package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.util.CryptoUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * SM4-CBC encryptor implementation.
 * Uses 16-byte key (derived from first 16 bytes of DEK), 16-byte IV,
 * SM4/CBC/PKCS5Padding cipher via Bouncy Castle.
 */
public class Sm4CbcEncryptor implements SymmetricEncryptor {

    private static final int SM4_KEY_LENGTH = 16;
    private static final int IV_LENGTH = 16;
    private static final int ZERO_BLOCK_SIZE = 16;
    private static final String CIPHER_ALGORITHM = "SM4/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "SM4";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public byte[] encrypt(byte[] key, byte[] plaintext) {
        try {
            byte[] sm4Key = Arrays.copyOf(key, SM4_KEY_LENGTH);
            byte[] iv = CryptoUtils.generateRandomBytes(IV_LENGTH);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(sm4Key, KEY_ALGORITHM),
                    new IvParameterSpec(iv));

            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new CryptoException("SM4-CBC encryption failed", e);
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
                    new IvParameterSpec(iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoException("SM4-CBC decryption failed", e);
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
                    new IvParameterSpec(zeroIv));

            byte[] encrypted = cipher.doFinal(zeroBlock);
            return HexFormat.of().formatHex(encrypted);
        } catch (Exception e) {
            throw new CryptoException("SM4-CBC KCV computation failed", e);
        }
    }

    @Override
    public SymmetricAlgorithm getAlgorithm() {
        return SymmetricAlgorithm.SM4_CBC;
    }
}
