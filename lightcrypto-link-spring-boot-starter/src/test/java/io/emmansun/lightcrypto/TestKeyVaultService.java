package io.emmansun.lightcrypto;

import io.emmansun.lightcrypto.service.KeyVaultService;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Test KeyVaultService — returns fixed DEK and HMAC Key per entity class,
 * without requiring MongoDB or a CMK Provider.
 * <p>
 * Supports the multi-DEK API: ensureVaultInitialized(), getActiveKid(),
 * getDek(kid), getHmacKey(kid).
 * </p>
 */
public class TestKeyVaultService extends KeyVaultService {

    private static final String TEST_KID = "v1-test0001";

    private final byte[] dek;
    private final byte[] hmacKey;
    private final ConcurrentHashMap<String, String> entityActiveKids = new ConcurrentHashMap<>();

    public TestKeyVaultService(byte[] dek, byte[] hmacKey) {
        super(null, null, null, null);
        this.dek = dek;
        this.hmacKey = hmacKey;
    }

    @Override
    public void ensureVaultInitialized(Class<?> entityClass) {
        entityActiveKids.putIfAbsent(entityClass.getSimpleName(), TEST_KID);
    }

    @Override
    public String getActiveKid(Class<?> entityClass) {
        return entityActiveKids.computeIfAbsent(entityClass.getSimpleName(), k -> TEST_KID);
    }

    @Override
    public byte[] getDek(String kid) {
        if (TEST_KID.equals(kid)) return dek;
        throw new IllegalArgumentException("Unknown kid in test: " + kid);
    }

    @Override
    public byte[] getHmacKey(String kid) {
        if (TEST_KID.equals(kid)) return hmacKey;
        throw new IllegalArgumentException("Unknown kid in test: " + kid);
    }
}
