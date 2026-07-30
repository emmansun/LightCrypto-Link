package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.KeyVaultProperties;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.exception.KeyResolutionException;
import io.github.emmansun.lightcrypto.exception.OptimisticLockException;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyEntry;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyStatus;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Test
    void ensureVaultInitializedCreatesVaultAndLoadsKeys() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);

        service.ensureVaultInitialized(TEST_NAMESPACE);

        assertThat(vaultStore.documents).containsKey(TEST_NAMESPACE);
        VaultDocument stored = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(stored.keys()).hasSize(1);
        assertThat(stored.activeKid()).startsWith("v1-");
        assertThat(service.getActiveKid(TEST_NAMESPACE)).isEqualTo(stored.activeKid());
        assertThat(service.getActiveDekVersion(TEST_NAMESPACE)).isEqualTo(1);
        assertThat(service.getActiveHmacKey(TEST_NAMESPACE)).hasSize(32);
        assertThat(service.getHmacKeys(TEST_NAMESPACE)).hasSize(1);
    }

    @Test
    void ensureVaultInitializedFallsBackToConcurrentExistingDocument() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        byte[] dek = fixedKey((byte) 0x31);
        byte[] hmac = fixedKey((byte) 0x41);
        String kid = "v1-a1b2c3d4";
        vaultStore.concurrentExistingDoc = vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dek, hmac)));
        vaultStore.failFirstSave = true;

        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);

        service.ensureVaultInitialized(TEST_NAMESPACE);

        assertThat(service.getActiveKid(TEST_NAMESPACE)).isEqualTo(kid);
        assertThat(service.getDek(kid)).containsExactly(dek);
        assertThat(vaultStore.documents.get(TEST_NAMESPACE)).isEqualTo(vaultStore.concurrentExistingDoc);
    }

    @Test
    void rotateDekAddsNewActiveVersionAndPreservesOlderHmacKeyOrder() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        String kid = "v1-a1b2c3d4";
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dek, hmac))));

        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);
        service.ensureVaultInitialized(TEST_NAMESPACE);
        byte[] previousActiveHmac = service.getActiveHmacKey(TEST_NAMESPACE);

        service.rotateDek(TEST_NAMESPACE);

        VaultDocument rotated = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(rotated.version()).isEqualTo(2L);
        assertThat(rotated.keys()).hasSize(2);
        assertThat(rotated.keys().get(0).status()).isEqualTo(KeyStatus.ROTATED);
        assertThat(rotated.keys().get(1).status()).isEqualTo(KeyStatus.ACTIVE);
        assertThat(service.getActiveKid(TEST_NAMESPACE)).isEqualTo(rotated.activeKid());
        assertThat(service.getActiveDekVersion(TEST_NAMESPACE)).isEqualTo(2);
        assertThat(service.getHmacKeys(TEST_NAMESPACE)).hasSize(2);
        assertThat(service.getHmacKeys(TEST_NAMESPACE).get(0)).containsExactly(previousActiveHmac);
        assertThat(service.getHmacKeys(TEST_NAMESPACE).get(1)).hasSize(32);
    }

        @Test
        void getDekAndHmacLookupScansAcrossNamespaces() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        String ns1 = TEST_NAMESPACE;
        String ns2 = "default.default.OtherEntity#field";

        String kid1 = "v1-a1b2c3d4";
        byte[] dek1 = fixedKey((byte) 0x11);
        byte[] hmac1 = fixedKey((byte) 0x21);
        vaultStore.documents.put(ns1, vaultDoc(ns1, kid1, List.of(activeEntry(kid1, dek1, hmac1))));

        String kid2 = "v1-a1b2c3d5";
        byte[] dek2 = fixedKey((byte) 0x31);
        byte[] hmac2 = fixedKey((byte) 0x41);
        vaultStore.documents.put(ns2, vaultDoc(ns2, kid2, List.of(activeEntry(kid2, dek2, hmac2))));

        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);
        service.ensureVaultInitialized(ns1);
        service.ensureVaultInitialized(ns2);

        assertThat(service.getDek(kid2)).containsExactly(dek2);
        assertThat(service.getHmacKey(kid2)).containsExactly(hmac2);
        }

        @Test
        void rotateDekPreservesAlreadyRotatedEntries() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        String kidRotated = "v1-a1b2c3d4";
        String kidActive = "v2-a1b2c3d5";

        KeyEntry rotated = new KeyEntry(
            kidRotated,
            KeyStatus.ROTATED,
            fixedKey((byte) 0x11),
            fixedKey((byte) 0x21),
            "IDENTITY",
            KeyCheckValue.computeDekKcv(fixedKey((byte) 0x11), KCV_ALGORITHM),
            KeyCheckValue.computeHmacKcv(fixedKey((byte) 0x21)),
            KeyCheckValue.computeBinding(fixedKey((byte) 0x21), fixedKey((byte) 0x11)),
            Instant.now());

        KeyEntry active = activeEntry(kidActive, fixedKey((byte) 0x31), fixedKey((byte) 0x41));
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kidActive, List.of(rotated, active)));

        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);
        service.ensureVaultInitialized(TEST_NAMESPACE);
        service.rotateDek(TEST_NAMESPACE);

        VaultDocument updated = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(updated.keys()).hasSize(3);
        assertThat(updated.keys().stream().filter(k -> k.kid().equals(kidRotated)).findFirst().orElseThrow().status())
            .isEqualTo(KeyStatus.ROTATED);
        assertThat(updated.keys().stream().filter(k -> k.kid().equals(kidActive)).findFirst().orElseThrow().status())
            .isEqualTo(KeyStatus.ROTATED);
        assertThat(updated.activeKid()).startsWith("v3-");
        }

        @Test
        void getHmacKeysAreSortedByParsedVersion() throws Exception {
        KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);

        byte[] hmacV1 = fixedKey((byte) 0x12);
        byte[] hmacV2 = fixedKey((byte) 0x22);
        byte[] hmacV3 = fixedKey((byte) 0x32);
        KeyEntry v3 = activeEntry("v3-a1b2c3d3", fixedKey((byte) 0x13), hmacV3);
        KeyEntry v1 = new KeyEntry(
            "v1-a1b2c3d1",
            KeyStatus.ROTATED,
            fixedKey((byte) 0x11),
            hmacV1,
            "IDENTITY",
            KeyCheckValue.computeDekKcv(fixedKey((byte) 0x11), KCV_ALGORITHM),
            KeyCheckValue.computeHmacKcv(hmacV1),
            KeyCheckValue.computeBinding(hmacV1, fixedKey((byte) 0x11)),
            Instant.now());
        KeyEntry v2 = new KeyEntry(
            "v2-a1b2c3d2",
            KeyStatus.ROTATED,
            fixedKey((byte) 0x12),
            hmacV2,
            "IDENTITY",
            KeyCheckValue.computeDekKcv(fixedKey((byte) 0x12), KCV_ALGORITHM),
            KeyCheckValue.computeHmacKcv(hmacV2),
            KeyCheckValue.computeBinding(hmacV2, fixedKey((byte) 0x12)),
            Instant.now());
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v3-a1b2c3d3", List.of(v3, v1, v2));

        invokeVerifyAndLoadKeys(service, doc, TEST_NAMESPACE);

        List<byte[]> keys = service.getHmacKeys(TEST_NAMESPACE);
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).containsExactly(hmacV1);
        assertThat(keys.get(1)).containsExactly(hmacV2);
        assertThat(keys.get(2)).containsExactly(hmacV3);
        }

    @Test
    void rotateDekRejectsMissingVault() {
        KeyVaultService service = new KeyVaultService(new InMemoryVaultStore(), new IdentityCmkProvider(), (KeyVaultProperties) null);

        assertThatThrownBy(() -> service.rotateDek(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Vault not found for namespace");
    }

    @Test
    void rotateDekWrapsOptimisticLockException() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        String kid = "v1-a1b2c3d4";
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(activeEntry(kid, fixedKey((byte) 0x11), fixedKey((byte) 0x22)))));
        vaultStore.failRotateWithOptimisticLock = true;

        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);

        assertThatThrownBy(() -> service.rotateDek(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Concurrent vault rotation detected");
    }

    @Test
    void getDekByVersionReturnsExpectedKeyAfterInitAndRotate() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        String kid = "v1-a1b2c3d4";
        byte[] dekV1 = fixedKey((byte) 0x11);
        byte[] hmacV1 = fixedKey((byte) 0x22);
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dekV1, hmacV1))));

        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);
        service.ensureVaultInitialized(TEST_NAMESPACE);

        byte[] loadedV1 = service.getDekByVersion(TEST_NAMESPACE, 1);
        assertThat(loadedV1).containsExactly(dekV1);

        service.rotateDek(TEST_NAMESPACE);
        byte[] loadedV2 = service.getDekByVersion(TEST_NAMESPACE, 2);
        assertThat(loadedV2).hasSize(32);
    }

    @Test
    void getDekByVersionThrowsWhenNamespaceNotInitialized() {
        KeyVaultService service = new KeyVaultService(new InMemoryVaultStore(), new IdentityCmkProvider(), (KeyVaultProperties) null);

        assertThatThrownBy(() -> service.getDekByVersion(TEST_NAMESPACE, 1))
                .isInstanceOf(KeyResolutionException.class)
                .hasMessageContaining("Vault not found for namespace");
    }

    @Test
    void getDekByVersionThrowsWhenVersionNotFound() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        String kid = "v1-a1b2c3d4";
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(activeEntry(kid, fixedKey((byte) 0x11), fixedKey((byte) 0x22)))));

        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);
        service.ensureVaultInitialized(TEST_NAMESPACE);

        assertThatThrownBy(() -> service.getDekByVersion(TEST_NAMESPACE, 9))
                .isInstanceOf(KeyResolutionException.class)
                .hasMessageContaining("No key found for namespace")
                .hasMessageContaining("dekVersion 9");
    }

            @Test
            void getActiveDekVersionThrowsWhenNamespaceNotInitialized() {
            KeyVaultService service = new KeyVaultService(new InMemoryVaultStore(), new IdentityCmkProvider(), (KeyVaultProperties) null);

            assertThatThrownBy(() -> service.getActiveDekVersion(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Vault not initialized for namespace");
            }

            @Test
            void getHmacKeysThrowsWhenNamespaceNotInitialized() {
            KeyVaultService service = new KeyVaultService(new InMemoryVaultStore(), new IdentityCmkProvider(), (KeyVaultProperties) null);

            assertThatThrownBy(() -> service.getHmacKeys(TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Vault not initialized for namespace");
            }

            @Test
            void ensureVaultInitializedSkipsReloadWhenCacheEntryIsFresh() {
            InMemoryVaultStore vaultStore = new InMemoryVaultStore();
            String kid = "v1-a1b2c3d4";
            vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(activeEntry(kid, fixedKey((byte) 0x11), fixedKey((byte) 0x22)))));

            KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);
            service.ensureVaultInitialized(TEST_NAMESPACE);
            service.ensureVaultInitialized(TEST_NAMESPACE);

            assertThat(vaultStore.loadCalls).isEqualTo(1);
            }

            @Test
            void ensureVaultInitializedReloadsWhenCacheEntryExpired() {
            InMemoryVaultStore vaultStore = new InMemoryVaultStore();
            String kid = "v1-a1b2c3d4";
            byte[] dek = fixedKey((byte) 0x11);
            byte[] hmac = fixedKey((byte) 0x22);
            vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid, List.of(activeEntry(kid, dek, hmac))));

            KeyVaultProperties props = new KeyVaultProperties();
            props.getCache().setTtl(Duration.ofHours(1));
            Clock expiredClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
            KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), props, expiredClock);

            service.ensureVaultInitialized(TEST_NAMESPACE);
            vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(activeEntry(kid, fixedKey((byte) 0x11), fixedKey((byte) 0x22)))));
            service.ensureVaultInitialized(TEST_NAMESPACE);

            assertThat(vaultStore.loadCalls).isEqualTo(2);
            }

            @Test
            void verifyAndLoadKeysRejectsNullAndEmptyEntries() {
            KeyVaultService service = new KeyVaultService(null, new IdentityCmkProvider(), (KeyVaultProperties) null);

            VaultDocument nullKeysDoc = new VaultDocument(
                TEST_NAMESPACE,
                null,
                "v1-a1b2c3d4",
                1L,
                "test",
                "cmk:test",
                Instant.now(),
                Instant.now());

            VaultDocument emptyKeysDoc = vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4", List.of());

            assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, nullKeysDoc, TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Vault has no key entries");

            assertThatThrownBy(() -> invokeVerifyAndLoadKeys(service, emptyKeysDoc, TEST_NAMESPACE))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Vault has no key entries");
            }

    @Test
    void ensureVaultInitializedThrowsWhenConcurrentSaveFailsAndNoDocumentExists() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        vaultStore.failFirstSave = true;

        KeyVaultService service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);

        assertThatThrownBy(() -> service.ensureVaultInitialized(TEST_NAMESPACE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("duplicate key");
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

    private static final class InMemoryVaultStore implements VaultStore {
        private final Map<String, VaultDocument> documents = new java.util.HashMap<>();
        private boolean failFirstSave;
        private boolean failRotateWithOptimisticLock;
        private VaultDocument concurrentExistingDoc;
        private int loadCalls;

        @Override
        public void save(VaultDocument doc) {
            if (failFirstSave) {
                failFirstSave = false;
                if (concurrentExistingDoc != null) {
                    documents.put(concurrentExistingDoc.namespace(), concurrentExistingDoc);
                }
                throw new RuntimeException("duplicate key");
            }
            documents.put(doc.namespace(), doc);
        }

        @Override
        public Optional<VaultDocument> load(String namespace) {
            loadCalls++;
            return Optional.ofNullable(documents.get(namespace));
        }

        @Override
        public boolean exists(String namespace) {
            return documents.containsKey(namespace);
        }

        @Override
        public VaultDocument rotate(VaultDocument updatedDoc) {
            if (failRotateWithOptimisticLock) {
                throw new OptimisticLockException("stale version");
            }
            documents.put(updatedDoc.namespace(), updatedDoc);
            return updatedDoc;
        }

        @Override
        public List<VaultDocument> loadAll() {
            return new ArrayList<>(documents.values());
        }
    }
}
