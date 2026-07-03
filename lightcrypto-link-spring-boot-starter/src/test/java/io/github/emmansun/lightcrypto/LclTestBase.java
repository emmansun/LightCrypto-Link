package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.provider.LocalSymmetricCmkProvider;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Test base class — provides shared test keys and utility methods.
 */
public abstract class LclTestBase {

    // Test CMK (32 bytes = 64 hex chars)
    protected static final String TEST_CMK_HEX = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2";
    protected static final byte[] TEST_CMK = HexFormat.of().parseHex(TEST_CMK_HEX);

    // Test DEK and HMAC Key (fixed values for easy assertions, each 32 bytes = 64 hex chars)
    protected static final byte[] TEST_DEK = HexFormat.of().parseHex("1122334455667788990011223344556677889900aabbccddeeff00112233aabb");
    protected static final byte[] TEST_HMAC_KEY = HexFormat.of().parseHex("aabbccddeeff00112233445566778899aabbccddeeff001122334455667788aa");

    protected CmkProvider createTestCmkProvider() {
        return new LocalSymmetricCmkProvider(TEST_CMK);
    }

    protected CryptoCodec createTestCryptoCodec() {
        return new CryptoCodec();
    }

    protected TypeSerializer createTestTypeSerializer() {
        return new TypeSerializer();
    }

    protected TypeDeserializer createTestTypeDeserializer() {
        return new TypeDeserializer();
    }

    protected byte[] generateRandomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
