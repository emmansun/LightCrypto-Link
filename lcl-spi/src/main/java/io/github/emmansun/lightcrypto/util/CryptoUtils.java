package io.github.emmansun.lightcrypto.util;

import java.security.SecureRandom;

public final class CryptoUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
        // Prevent instantiation
    }

    public static SecureRandom getSecureRandom() {
        return SECURE_RANDOM;
    }

    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }
}
