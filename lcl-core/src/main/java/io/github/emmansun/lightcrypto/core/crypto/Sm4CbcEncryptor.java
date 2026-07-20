package io.github.emmansun.lightcrypto.core.crypto;

import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.exception.CryptoException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * SM4-CBC encryptor (Bouncy Castle). AAD is ignored (CBC has no authentication).
 *
 * @since 1.0.0
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
    public byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext, byte[] aad) {
        try {
            byte[] sm4Key = Arrays.copyOf(key, SM4_KEY_LENGTH);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(sm4Key, KEY_ALGORITHM),
                    new IvParameterSpec(iv));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new CryptoException("SM4-CBC encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] aad) {
        try {
            byte[] sm4Key = Arrays.copyOf(key, SM4_KEY_LENGTH);
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
    public AlgorithmId algorithmId() {
        return AlgorithmId.SM4_CBC;
    }
}
