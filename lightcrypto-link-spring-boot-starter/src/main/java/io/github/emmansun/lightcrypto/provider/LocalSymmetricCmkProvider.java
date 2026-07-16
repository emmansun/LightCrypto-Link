package io.github.emmansun.lightcrypto.provider;

import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.model.LclAlgorithms;
import io.github.emmansun.lightcrypto.model.WrappedKey;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Local symmetric CMK Provider — wraps/unwraps DEKs locally using AES-256-GCM.
 * <p>
 * The CMK must be exactly 32 bytes (256 bits). Wrap format: IV (12 bytes) || GCM ciphertext.
 * </p>
 */
public final class LocalSymmetricCmkProvider implements CmkProvider {

    private static final String PROVIDER_ID = "local-symmetric";
    private static final String ALGORITHM = "AES-256-GCM";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int REQUIRED_CMK_LENGTH = 32;

    private final byte[] cmk;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String publicReference;

    public LocalSymmetricCmkProvider(byte[] cmk) {
        if (cmk == null || cmk.length != REQUIRED_CMK_LENGTH) {
            throw new IllegalArgumentException(
                    "CMK must be exactly " + REQUIRED_CMK_LENGTH + " bytes (256 bits), got: " +
                            (cmk == null ? "null" : cmk.length));
        }
        this.cmk = cmk.clone();
        this.publicReference = buildPublicReference(this.cmk);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getPublicReference() {
        return publicReference;
    }

    @Override
    public boolean supportsAlgorithm(String lclAlgorithm) {
        return LclAlgorithms.AES_256_GCM.equals(lclAlgorithm);
    }

    @Override
    public String mapAlgorithm(String lclAlgorithm) {
        return lclAlgorithm; 
    }

    @Override
    public WrappedKey wrap(byte[] plaintextKey) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(cmk, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintextKey);

            // Concatenate IV || ciphertext
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            return new WrappedKey(combined, LclAlgorithms.AES_256_GCM);
        } catch (Exception e) {
            throw new CryptoException("Failed to wrap key", e);
        }
    }

    @Override
    public byte[] unwrap(WrappedKey wrappedKey) {
        if (wrappedKey == null) {
            throw new IllegalArgumentException("WrappedKey must not be null");
        }
        if (!supportsAlgorithm(wrappedKey.algorithm())) {
            throw new IllegalArgumentException("Unsupported algorithm: " + wrappedKey.algorithm());
        }
        try {
            byte[] data = wrappedKey.ciphertext();
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[data.length - IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, IV_LENGTH);
            System.arraycopy(data, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(cmk, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoException("Failed to unwrap key", e);
        }
    }

    private static String buildPublicReference(byte[] cmk) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cmk);
            return "local-cmk-sha256:" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build local CMK public reference", e);
        }
    }
}
