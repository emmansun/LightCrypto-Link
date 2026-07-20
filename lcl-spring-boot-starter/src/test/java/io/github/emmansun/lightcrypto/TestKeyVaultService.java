package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.service.KeyVaultService;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Test KeyVaultService — returns fixed DEK and HMAC Key per namespace,
 * without requiring MongoDB or a CMK Provider.
 */
public class TestKeyVaultService extends KeyVaultService {

    private static final String TEST_KID = "v1-test0001";
    private static final int TEST_DEK_VERSION = 1;

    private final byte[] dek;
    private final byte[] hmacKey;
    private final ConcurrentHashMap<String, String> namespaceActiveKids = new ConcurrentHashMap<>();

    public TestKeyVaultService(byte[] dek, byte[] hmacKey) {
        super(null, null, null);
        this.dek = dek;
        this.hmacKey = hmacKey;
    }

    @Override
    public void ensureVaultInitialized(String namespace) {
        namespaceActiveKids.putIfAbsent(namespace, TEST_KID);
    }

    @Override
    public String getActiveKid(String namespace) {
        return namespaceActiveKids.computeIfAbsent(namespace, k -> TEST_KID);
    }

    @Override
    public int getActiveDekVersion(String namespace) {
        return TEST_DEK_VERSION;
    }

    @Override
    public byte[] getDek(String kid) {
        if (TEST_KID.equals(kid)) return dek;
        throw new IllegalArgumentException("Unknown kid in test: " + kid);
    }

    @Override
    public byte[] getDekByVersion(String namespace, int dekVersion) {
        return dek;
    }

    @Override
    public byte[] getHmacKey(String kid) {
        if (TEST_KID.equals(kid)) return hmacKey;
        throw new IllegalArgumentException("Unknown kid in test: " + kid);
    }

    @Override
    public byte[] getActiveHmacKey(String namespace) {
        return hmacKey;
    }
}
