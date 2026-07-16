package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.model.KeyVaultDocument;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
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

    private static final CryptoCodec CODEC = new CryptoCodec();

    @Test
    void getActiveKidBeforeInitThrows() {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);

        assertThatThrownBy(() -> service.getActiveKid(TestEntity.class))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Call ensureVaultInitialized() first");
    }

    @Test
    void verifyAndLoadKeysLoadsContextForActiveKid() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName());

        assertThat(service.getActiveKid(TestEntity.class)).isEqualTo(kid);
        assertThat(service.getDek(kid)).containsExactly(dek);
        assertThat(service.getHmacKey(kid)).containsExactly(hmac);
    }

    @Test
    void verifyAndLoadKeysRejectsNoActiveKey() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);
        String kid = "v1-a1b2c3d4";
        KeyVaultDocument.KeyVersionEntry entry = activeEntry(kid, fixedKey((byte) 0x11), fixedKey((byte) 0x22));
        entry.setStatus("ROTATED");
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", kid, List.of(entry));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName()))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("no ACTIVE key entry");
    }

    @Test
    void verifyAndLoadKeysRejectsMultipleActiveKeys() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);
        KeyVaultDocument.KeyVersionEntry e1 = activeEntry("v1-a1b2c3d4", fixedKey((byte) 0x11), fixedKey((byte) 0x22));
        KeyVaultDocument.KeyVersionEntry e2 = activeEntry("v2-a1b2c3d5", fixedKey((byte) 0x33), fixedKey((byte) 0x44));
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", "v1-a1b2c3d4", List.of(e1, e2));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName()))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("multiple ACTIVE key entries");
    }

    @Test
    void verifyAndLoadKeysRejectsDekKcvMismatch() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        KeyVaultDocument.KeyVersionEntry entry = activeEntry("v1-a1b2c3d4", dek, hmac);
        entry.getDek().setKcv("bad-kcv");
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", "v1-a1b2c3d4", List.of(entry));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName()))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("DEK KCV mismatch");
    }

    @Test
    void verifyAndLoadKeysRejectsHmacKcvMismatch() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        KeyVaultDocument.KeyVersionEntry entry = activeEntry("v1-a1b2c3d4", dek, hmac);
        entry.getHmk().setKcv("bad-kcv");
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", "v1-a1b2c3d4", List.of(entry));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName()))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("HMAC Key KCV mismatch");
    }

    @Test
    void verifyAndLoadKeysRejectsBindingMismatch() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        KeyVaultDocument.KeyVersionEntry entry = activeEntry("v1-a1b2c3d4", dek, hmac);
        entry.setBinding("broken-binding");
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", "v1-a1b2c3d4", List.of(entry));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName()))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Key binding mismatch");
    }

    @Test
    void getDekAndGetHmacUnknownKidThrow() {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);

        assertThatThrownBy(() -> service.getDek("v9-unknown")).isInstanceOf(FatalCryptoException.class).hasMessageContaining("Unknown kid");
        assertThatThrownBy(() -> service.getHmacKey("v9-unknown")).isInstanceOf(FatalCryptoException.class).hasMessageContaining("Unknown kid");
    }

    @Test
    void verifyAndLoadKeysWrapsUnexpectedException() {
        KeyVaultService service = new KeyVaultService(null, new BrokenCmkProvider(), new CryptoProperties(), CODEC);
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", "v1-a1b2c3d4", List.of(activeEntry("v1-a1b2c3d4", fixedKey((byte) 0x11), fixedKey((byte) 0x22))));

        assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName()))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Failed to verify key vault");
    }

    @Test
    void defaultCacheTtlIsOneHour() {
        CryptoProperties props = new CryptoProperties();
        assertThat(props.getCacheTtl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void cacheEntryHasFutureExpiresAtWithinTtl() throws Exception {
        CryptoProperties props = new CryptoProperties();
        props.setCacheTtl(Duration.ofHours(1));
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), props, CODEC);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName());

        assertThat(service.getActiveKid(TestEntity.class)).isEqualTo(kid);
        // DEK is the same reference (cached)
        assertThat(service.getDek(kid)).isSameAs(service.getDek(kid));
    }

    @Test
    void cacheEntryExpiresAtIsEpochWhenTtlIsZero() throws Exception {
        CryptoProperties props = new CryptoProperties();
        props.setCacheTtl(Duration.ZERO);
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), props, CODEC);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName());

        // When TTL is zero, the entry is NOT stored in cache
        Field mapField = KeyVaultService.class.getDeclaredField("entityKeyContexts");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ?> map = (ConcurrentHashMap<String, ?>) mapField.get(service);
        assertThat(map).isEmpty();
    }

    @Test
    void flushCacheZerosKeyMaterialAndClearsMap() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), new CryptoProperties(), CODEC);
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName());

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
        Field mapField = KeyVaultService.class.getDeclaredField("entityKeyContexts");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ?> map = (ConcurrentHashMap<String, ?>) mapField.get(service);
        assertThat(map).isEmpty();

        // getActiveKid throws after flush
        assertThatThrownBy(() -> service.getActiveKid(TestEntity.class))
                .isInstanceOf(FatalCryptoException.class);
    }

    @Test
    void expiredEntryIsDetectedByIsExpired() throws Exception {
        CryptoProperties props = new CryptoProperties();
        props.setCacheTtl(Duration.ofHours(1));

        // Use a fixed clock in the past so expiresAt = pastTime + 1h = still in the past
        Instant fixedPastTime = Instant.parse("2020-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(fixedPastTime, ZoneOffset.UTC);
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), props, CODEC, fixedClock);

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        KeyVaultDocument doc = vaultDoc("lcl-dek-TestEntity", kid, List.of(activeEntry(kid, dek, hmac)));

        invokeVerifyAndLoadKeys(service, doc, TestEntity.class.getSimpleName());

        // expiresAt was computed as 2020-01-01T01:00:00Z which is in the past relative to now
        // The entry should be expired
        Field mapField = KeyVaultService.class.getDeclaredField("entityKeyContexts");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ?> map = (ConcurrentHashMap<String, ?>) mapField.get(service);
        Object ctx = map.get("TestEntity");
        assertThat(ctx).isNotNull();

        Method isExpired = ctx.getClass().getDeclaredMethod("isExpired");
        isExpired.setAccessible(true);
        assertThat((boolean) isExpired.invoke(ctx)).isTrue();
    }

    @Test
    void customCacheTtlIsRespected() {
        CryptoProperties props = new CryptoProperties();
        props.setCacheTtl(Duration.ofMinutes(30));
        assertThat(props.getCacheTtl()).isEqualTo(Duration.ofMinutes(30));
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

    private static void invokeVerifyAndLoadKeys(KeyVaultService service, KeyVaultDocument doc, String entityName) throws Exception {
        Method method = KeyVaultService.class.getDeclaredMethod("verifyAndLoadKeys", KeyVaultDocument.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(service, doc, entityName);
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

    private static KeyVaultDocument vaultDoc(String id, String activeKid, List<KeyVaultDocument.KeyVersionEntry> keys) {
        KeyVaultDocument doc = new KeyVaultDocument();
        doc.setId(id);
        doc.setV(1);
        doc.setStatus("ACTIVE");
        doc.setActiveKid(activeKid);
        doc.setKeys(keys);
        KeyVaultDocument.CmkInfo cmkInfo = new KeyVaultDocument.CmkInfo();
        cmkInfo.setProvider("test");
        cmkInfo.setId("cmk:test");
        doc.setCmk(cmkInfo);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }

    private static KeyVaultDocument.KeyVersionEntry activeEntry(String kid, byte[] dek, byte[] hmac) {
        KeyVaultDocument.WrappedKeyInfo dekInfo = new KeyVaultDocument.WrappedKeyInfo();
        dekInfo.setWrapped(dek);
        dekInfo.setAlgorithm("IDENTITY");
        dekInfo.setKcv(CODEC.computeKcv(dek));

        KeyVaultDocument.WrappedKeyInfo hmkInfo = new KeyVaultDocument.WrappedKeyInfo();
        hmkInfo.setWrapped(hmac);
        hmkInfo.setAlgorithm("IDENTITY");
        hmkInfo.setKcv(CODEC.computeKcv(hmac));

        KeyVaultDocument.KeyVersionEntry entry = new KeyVaultDocument.KeyVersionEntry();
        entry.setKid(kid);
        entry.setStatus("ACTIVE");
        entry.setDek(dekInfo);
        entry.setHmk(hmkInfo);
        entry.setBinding(CODEC.computeBinding(hmac, dek));
        entry.setCreatedAt(Instant.now());
        return entry;
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

    static class TestEntity {
    }
}
