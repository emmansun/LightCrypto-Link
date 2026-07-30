package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.KeyVaultProperties;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyEntry;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyStatus;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RETIRED key lifecycle: markKeysRetired, pruneRetiredKeys, and getDekByVersion guard.
 */
class KeyVaultServiceRetiredTest {

    private static final AlgorithmId KCV_ALGORITHM = AlgorithmId.AES_256_GCM;
    private static final String TEST_NAMESPACE = "default.default.User#email";

    private InMemoryVaultStore vaultStore;
    private KeyVaultService service;

    @BeforeEach
    void setUp() {
        vaultStore = new InMemoryVaultStore();
        service = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);
    }

    @Test
    void markKeysRetiredTransitionsRotatedToRetired() {
        // Setup: vault with ACTIVE v2 and ROTATED v1
        byte[] dek1 = fixedKey((byte) 0x11);
        byte[] hmac1 = fixedKey((byte) 0x21);
        byte[] dek2 = fixedKey((byte) 0x12);
        byte[] hmac2 = fixedKey((byte) 0x22);

        KeyEntry v1 = rotatedEntry("v1-aaaaaaaa", dek1, hmac1);
        KeyEntry v2 = activeEntry("v2-bbbbbbbb", dek2, hmac2);
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v2-bbbbbbbb", List.of(v1, v2));
        vaultStore.save(doc);

        // Initialize the vault context
        service.ensureVaultInitialized(TEST_NAMESPACE);

        // Mark v1 as retired
        service.markKeysRetired(TEST_NAMESPACE, Set.of("v1-aaaaaaaa"));

        // Verify: v1 is now RETIRED
        VaultDocument updated = vaultStore.load(TEST_NAMESPACE).orElseThrow();
        KeyEntry retiredEntry = updated.keys().stream()
                .filter(k -> k.kid().equals("v1-aaaaaaaa"))
                .findFirst().orElseThrow();
        assertThat(retiredEntry.status()).isEqualTo(KeyStatus.RETIRED);
    }

    @Test
    void markKeysRetiredIgnoresActiveKeys() {
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x21);
        KeyEntry active = activeEntry("v1-aaaaaaaa", dek, hmac);
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v1-aaaaaaaa", List.of(active));
        vaultStore.save(doc);

        service.ensureVaultInitialized(TEST_NAMESPACE);

        // Try to mark ACTIVE key as retired - should be ignored
        service.markKeysRetired(TEST_NAMESPACE, Set.of("v1-aaaaaaaa"));

        VaultDocument updated = vaultStore.load(TEST_NAMESPACE).orElseThrow();
        assertThat(updated.keys().get(0).status()).isEqualTo(KeyStatus.ACTIVE);
    }

    @Test
    void pruneRetiredKeysRemovesRetiredEntries() {
        byte[] dek1 = fixedKey((byte) 0x11);
        byte[] hmac1 = fixedKey((byte) 0x21);
        byte[] dek2 = fixedKey((byte) 0x12);
        byte[] hmac2 = fixedKey((byte) 0x22);

        KeyEntry retired = retiredEntry("v1-aaaaaaaa", dek1, hmac1);
        KeyEntry active = activeEntry("v2-bbbbbbbb", dek2, hmac2);
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v2-bbbbbbbb", List.of(retired, active));
        vaultStore.save(doc);

        service.ensureVaultInitialized(TEST_NAMESPACE);

        int removed = service.pruneRetiredKeys(TEST_NAMESPACE);

        assertThat(removed).isEqualTo(1);
        VaultDocument updated = vaultStore.load(TEST_NAMESPACE).orElseThrow();
        assertThat(updated.keys()).hasSize(1);
        assertThat(updated.keys().get(0).kid()).isEqualTo("v2-bbbbbbbb");
    }

    @Test
    void pruneRetiredKeysReturnsZeroWhenNoRetiredKeys() {
        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x21);
        KeyEntry active = activeEntry("v1-aaaaaaaa", dek, hmac);
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v1-aaaaaaaa", List.of(active));
        vaultStore.save(doc);

        service.ensureVaultInitialized(TEST_NAMESPACE);

        int removed = service.pruneRetiredKeys(TEST_NAMESPACE);

        assertThat(removed).isZero();
    }

    @Test
    void getDekByVersionThrowsForRetiredKey() {
        byte[] dek1 = fixedKey((byte) 0x11);
        byte[] hmac1 = fixedKey((byte) 0x21);
        byte[] dek2 = fixedKey((byte) 0x12);
        byte[] hmac2 = fixedKey((byte) 0x22);

        KeyEntry retired = retiredEntry("v1-aaaaaaaa", dek1, hmac1);
        KeyEntry active = activeEntry("v2-bbbbbbbb", dek2, hmac2);
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v2-bbbbbbbb", List.of(retired, active));
        vaultStore.save(doc);

        service.ensureVaultInitialized(TEST_NAMESPACE);

        // Attempting to get DEK for retired version should throw
        assertThatThrownBy(() -> service.getDekByVersion(TEST_NAMESPACE, 1))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("RETIRED");
    }

    @Test
    void getDekByVersionWorksForRotatedKey() {
        byte[] dek1 = fixedKey((byte) 0x11);
        byte[] hmac1 = fixedKey((byte) 0x21);
        byte[] dek2 = fixedKey((byte) 0x12);
        byte[] hmac2 = fixedKey((byte) 0x22);

        KeyEntry rotated = rotatedEntry("v1-aaaaaaaa", dek1, hmac1);
        KeyEntry active = activeEntry("v2-bbbbbbbb", dek2, hmac2);
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v2-bbbbbbbb", List.of(rotated, active));
        vaultStore.save(doc);

        service.ensureVaultInitialized(TEST_NAMESPACE);

        // ROTATED keys should still be accessible for decryption
        byte[] dek = service.getDekByVersion(TEST_NAMESPACE, 1);
        assertThat(dek).containsExactly(dek1);
    }

    // ===== Helper methods =====

    private static byte[] fixedKey(byte b) {
        byte[] out = new byte[32];
        Arrays.fill(out, b);
        return out;
    }

    private static VaultDocument vaultDoc(String namespace, String activeKid, List<KeyEntry> keys) {
        return new VaultDocument(namespace, keys, activeKid, 1L, "test", "cmk:test", Instant.now(), Instant.now());
    }

    private static KeyEntry activeEntry(String kid, byte[] dek, byte[] hmac) {
        return new KeyEntry(kid, KeyStatus.ACTIVE, dek, hmac, "IDENTITY",
                KeyCheckValue.computeDekKcv(dek, KCV_ALGORITHM),
                KeyCheckValue.computeHmacKcv(hmac),
                KeyCheckValue.computeBinding(hmac, dek),
                Instant.now());
    }

    private static KeyEntry rotatedEntry(String kid, byte[] dek, byte[] hmac) {
        return new KeyEntry(kid, KeyStatus.ROTATED, dek, hmac, "IDENTITY",
                KeyCheckValue.computeDekKcv(dek, KCV_ALGORITHM),
                KeyCheckValue.computeHmacKcv(hmac),
                KeyCheckValue.computeBinding(hmac, dek),
                Instant.now());
    }

    private static KeyEntry retiredEntry(String kid, byte[] dek, byte[] hmac) {
        return new KeyEntry(kid, KeyStatus.RETIRED, dek, hmac, "IDENTITY",
                KeyCheckValue.computeDekKcv(dek, KCV_ALGORITHM),
                KeyCheckValue.computeHmacKcv(hmac),
                KeyCheckValue.computeBinding(hmac, dek),
                Instant.now());
    }

    private static class IdentityCmkProvider implements CmkProvider {
        @Override
        public String getProviderId() { return "test-provider"; }

        @Override
        public String getPublicReference() { return "cmk:test"; }

        @Override
        public boolean supportsAlgorithm(String lclAlgorithm) { return "IDENTITY".equals(lclAlgorithm); }

        @Override
        public String mapAlgorithm(String lclAlgorithm) { return "IDENTITY"; }

        @Override
        public WrappedKey wrap(byte[] plaintextKey) { return new WrappedKey(plaintextKey.clone(), "IDENTITY"); }

        @Override
        public byte[] unwrap(WrappedKey wrappedKey) { return wrappedKey.ciphertext().clone(); }
    }

    private static final class InMemoryVaultStore implements VaultStore {
        private final Map<String, VaultDocument> documents = new HashMap<>();

        @Override
        public void save(VaultDocument doc) { documents.put(doc.namespace(), doc); }

        @Override
        public Optional<VaultDocument> load(String namespace) { return Optional.ofNullable(documents.get(namespace)); }

        @Override
        public boolean exists(String namespace) { return documents.containsKey(namespace); }

        @Override
        public VaultDocument rotate(VaultDocument updatedDoc) {
            documents.put(updatedDoc.namespace(), updatedDoc);
            return updatedDoc;
        }

        @Override
        public List<VaultDocument> loadAll() { return new ArrayList<>(documents.values()); }
    }
}
