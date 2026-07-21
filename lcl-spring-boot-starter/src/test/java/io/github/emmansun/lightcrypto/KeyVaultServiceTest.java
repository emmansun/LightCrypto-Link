package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.KeyVaultProperties;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyEntry;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyStatus;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyVaultServiceTest {

    private static final AlgorithmId KCV_ALGORITHM = AlgorithmId.AES_256_GCM;
    private static final String TEST_NAMESPACE = "default.default.TestEntity#field";

    @Test
    void getActiveKidBeforeInitThrows() {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);

        assertThatThrownBy(() -> service.getActiveKid(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Call ensureVaultInitialized() first");
    }

    @Test
    void verifyAndLoadKeysLoadsContextForActiveKid() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE);

        assertThat(service.getActiveKid(TEST_NAMESPACE)).isEqualTo(kid);
        assertThat(service.getDek(kid)).containsExactly(dek);
        assertThat(service.getHmacKey(kid)).containsExactly(hmac);
    }

    @Test
    void verifyAndLoadKeysRejectsNoActiveKey() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);
        String kid = "v1-a1b2c3d4";
        KeyEntry entry = activeEntry(kid, fixedKey((byte) 0x11), fixedKey((byte) 0x22));
        KeyEntry rotatedEntry = new KeyEntry(entry.kid(), KeyStatus.ROTATED, entry.wrappedDek(), entry.wrappedHmac(),
                entry.wrappingAlgorithm(), entry.dekKcv(), entry.hmacKcv(), entry.binding(), entry.createdAt());
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, kid, List.of(rotatedEntry));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("no ACTIVE key entry");
    }

    @Test
    void verifyAndLoadKeysRejectsMultipleActiveKeys() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);
        KeyEntry e1 = activeEntry("v1-a1b2c3d4", fixedKey((byte) 0x11), fixedKey((byte) 0x22));
        KeyEntry e2 = activeEntry("v2-a1b2c3d5", fixedKey((byte) 0x33), fixedKey((byte) 0x44));
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4", List.of(e1, e2));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("multiple ACTIVE key entries");
    }

    @Test
    void verifyAndLoadKeysRejectsDekKcvMismatch() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        KeyEntry entry = activeEntry("v1-a1b2c3d4", dek, hmac);
        // Create a corrupted entry with wrong dekKcv
        KeyEntry badEntry = new KeyEntry(entry.kid(), entry.status(), entry.wrappedDek(), entry.wrappedHmac(),
                entry.wrappingAlgorithm(), "bad-kcv", entry.hmacKcv(), entry.binding(), entry.createdAt());
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4", List.of(badEntry));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("DEK KCV mismatch");
    }

    @Test
    void verifyAndLoadKeysRejectsHmacKcvMismatch() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        KeyEntry entry = activeEntry("v1-a1b2c3d4", dek, hmac);
        // Create a corrupted entry with wrong hmacKcv
        KeyEntry badEntry = new KeyEntry(entry.kid(), entry.status(), entry.wrappedDek(), entry.wrappedHmac(),
                entry.wrappingAlgorithm(), entry.dekKcv(), "bad-kcv", entry.binding(), entry.createdAt());
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4", List.of(badEntry));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("HMAC Key KCV mismatch");
    }

    @Test
    void verifyAndLoadKeysRejectsBindingMismatch() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        KeyEntry entry = activeEntry("v1-a1b2c3d4", dek, hmac);
        // Create a corrupted entry with wrong binding
        KeyEntry badEntry = new KeyEntry(entry.kid(), entry.status(), entry.wrappedDek(), entry.wrappedHmac(),
                entry.wrappingAlgorithm(), entry.dekKcv(), entry.hmacKcv(), "broken-binding", entry.createdAt());
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4", List.of(badEntry));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Key binding mismatch");
    }

    @Test
    void getDekAndGetHmacUnknownKidThrow() {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);

        assertThatThrownBy(() -> service.getDek("v9-unknown")).isInstanceOf(FatalCryptoException.class).hasMessageContaining("Unknown kid");
        assertThatThrownBy(() -> service.getHmacKey("v9-unknown")).isInstanceOf(FatalCryptoException.class).hasMessageContaining("Unknown kid");
    }

    @Test
    void verifyAndLoadKeysWrapsUnexpectedException() {
        KeyVaultService service = new KeyVaultService(null, new BrokenCmkProvider(), (KeyVaultProperties) null);
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4", List.of(activeEntry("v1-a1b2c3d4", fixedKey((byte) 0x11), fixedKey((byte) 0x22))));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Failed to verify key vault");
    }

    @Test
    void defaultCacheTtlIsOneHour() {
        KeyVaultProperties props = new KeyVaultProperties();
        assertThat(props.getCache().getTtl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void cacheEntryHasFutureExpiresAtWithinTtl() throws Exception {
        KeyVaultProperties props = new KeyVaultProperties();
        props.getCache().setTtl(Duration.ofHours(1));
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), props);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE);

        assertThat(service.getActiveKid(TEST_NAMESPACE)).isEqualTo(kid);
        // DEK is the same reference (cached)
        assertThat(service.getDek(kid)).isSameAs(service.getDek(kid));
    }

    @Test
    void cacheEntryExpiresAtIsEpochWhenTtlIsZero() throws Exception {
        KeyVaultProperties props = new KeyVaultProperties();
        props.getCache().setTtl(Duration.ZERO);
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), props);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE);

        // When TTL is zero, the entry is NOT stored in cache
        Field mapField = KeyVaultService.class.getDeclaredField("namespaceKeyContexts");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ?> map = (ConcurrentHashMap<String, ?>) mapField.get(service);
        assertThat(map).isEmpty();
    }

    @Test
    void flushCacheZerosKeyMaterialAndClearsMap() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE);

        // Grab references to the cached byte arrays before flushing
        byte[] cachedDek = service.getDek(kid);
        byte[] cachedHmac = service.getHmacKey(kid);
        assertThat(cachedDek).containsExactly(dek);
        assertThat(cachedHmac).containsExactly(hmac);

        service.flushCache();

        // Arrays are zeroed
        assertThat(cachedDek).containsOnly((byte) 0);
        assertThat(cachedHmac).containsOnly((byte) 0);

        // Map is cleared
        Field mapField = KeyVaultService.class.getDeclaredField("namespaceKeyContexts");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ?> map = (ConcurrentHashMap<String, ?>) mapField.get(service);
        assertThat(map).isEmpty();

        // getActiveKid throws after flush
        assertThatThrownBy(() -> service.getActiveKid(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class);
    }

    @Test
    void expiredEntryIsDetectedByIsExpired() throws Exception {
        KeyVaultProperties props = new KeyVaultProperties();
        props.getCache().setTtl(Duration.ofHours(1));

        // Use a fixed clock in the past so expiresAt = pastTime + 1h = still in the past
        Instant fixedPastTime = Instant.parse("2020-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(fixedPastTime, ZoneOffset.UTC);
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), props, fixedClock);

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE);

        // expiresAt was computed as 2020-01-01T01:00:00Z which is in the past relative to now
        Field mapField = KeyVaultService.class.getDeclaredField("namespaceKeyContexts");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ?> map = (ConcurrentHashMap<String, ?>) mapField.get(service);
        Object ctx = map.get(TEST_NAMESPACE);
        assertThat(ctx).isNotNull();

        Method isExpired = ctx.getClass().getDeclaredMethod("isExpired");
        isExpired.setAccessible(true);
        assertThat((boolean) isExpired.invoke(ctx)).isTrue();
    }

    @Test
    void customCacheTtlIsRespected() {
        KeyVaultProperties props = new KeyVaultProperties();
        props.getCache().setTtl(Duration.ofMinutes(30));
        assertThat(props.getCache().getTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void parseVersionAndGenerateKidAreWellFormed() throws Exception {
        Method generateKid = KeyVaultService.class.getDeclaredMethod("generateKid", int.class);
        generateKid.setAccessible(true);
        String kid = (String) generateKid.invoke(null, 7);

        assertThat(kid).startsWith("v7-");
        assertThat(kid).hasSize(11);

        Method parseVersion = KeyVaultService.class.getDeclaredMethod("parseVersion", String.class);
        parseVersion.setAccessible(true);

        int version = (int) parseVersion.invoke(null, kid);
        assertThat(version).isEqualTo(7);
        assertThatThrownBy(() -> parseVersion.invoke(null, "bad-kid"))
                .hasCauseInstanceOf(FatalCryptoException.class)
                .hasRootCauseMessage("Invalid kid format: bad-kid");
    }

    private static void invokeVerifyAndLoadKeys(KeyVaultService service, VaultDocument doc, String namespace) throws Exception {
        Method method = KeyVaultService.class.getDeclaredMethod("verifyAndLoadKeys", VaultDocument.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(service, doc, namespace);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private static byte[] fixedKey(byte b) {
        byte[] out = new byte[32];
        java.util.Arrays.fill(out, b);
        return out;
    }

    private static VaultDocument vaultDoc(String namespace, String activeKid, List<KeyEntry> keys) {
        return new VaultDocument(
                namespace,
                keys,
                activeKid,
                1L,
                "test",
                "cmk:test",
                Instant.now(),
                Instant.now()
        );
    }

    private static KeyEntry activeEntry(String kid, byte[] dek, byte[] hmac) {
        return new KeyEntry(
                kid,
                KeyStatus.ACTIVE,
                dek,        // wrappedDek (identity provider returns as-is)
                hmac,       // wrappedHmac (identity provider returns as-is)
                "IDENTITY", // wrappingAlgorithm
                KeyCheckValue.computeDekKcv(dek, KCV_ALGORITHM),
                KeyCheckValue.computeHmacKcv(hmac),
                KeyCheckValue.computeBinding(hmac, dek),
                Instant.now()
        );
    }

    private static class IdentityCmkProvider implements CmkProvider {

        @Override
        public String getProviderId() {
            return "test-provider";
        }

        @Override
        public String getPublicReference() {
            return "cmk:test";
        }

        @Override
        public boolean supportsAlgorithm(String lclAlgorithm) {
            return "IDENTITY".equals(lclAlgorithm);
        }

        @Override
        public String mapAlgorithm(String lclAlgorithm) {
            return "IDENTITY";
        }
        
        @Override
        public WrappedKey wrap(byte[] plaintextKey) {
            return new WrappedKey(plaintextKey.clone(), "IDENTITY");
        }

        @Override
        public byte[] unwrap(WrappedKey wrappedKey) {
            return wrappedKey.ciphertext();
        }
    }

    private static class BrokenCmkProvider extends IdentityCmkProvider {
        @Override
        public byte[] unwrap(WrappedKey wrappedKey) {
            throw new IllegalStateException("broken unwrap");
        }
    }
}
