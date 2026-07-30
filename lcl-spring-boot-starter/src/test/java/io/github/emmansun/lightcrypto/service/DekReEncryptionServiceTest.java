package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.config.CryptographyProperties;
import io.github.emmansun.lightcrypto.config.KeyVaultProperties;
import io.github.emmansun.lightcrypto.config.TenantProperties;
import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.spi.*;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyEntry;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DekReEncryptionService engine.
 */
class DekReEncryptionServiceTest {

    private static final AlgorithmId KCV_ALGORITHM = AlgorithmId.AES_256_GCM;
    private static final String TEST_NAMESPACE = "default.default.TestUser#email";

    private InMemoryVaultStore vaultStore;
    private InMemoryDocumentRewriteStore rewriteStore;
    private KeyVaultService keyVaultService;
    private EntityMetadataCache metadataCache;
    private DekReEncryptionService service;

    private byte[] dekV1;
    private byte[] dekV2;
    private byte[] hmacV1;
    private byte[] hmacV2;

    @BeforeEach
    void setUp() {
        vaultStore = new InMemoryVaultStore();
        rewriteStore = new InMemoryDocumentRewriteStore();

        dekV1 = fixedKey((byte) 0x11);
        dekV2 = fixedKey((byte) 0x22);
        hmacV1 = fixedKey((byte) 0x31);
        hmacV2 = fixedKey((byte) 0x32);

        // Setup vault with v1 ROTATED and v2 ACTIVE
        KeyEntry v1 = rotatedEntry("v1-aaaaaaaa", dekV1, hmacV1);
        KeyEntry v2 = activeEntry("v2-bbbbbbbb", dekV2, hmacV2);
        VaultDocument doc = vaultDoc(TEST_NAMESPACE, "v2-bbbbbbbb", List.of(v1, v2));
        vaultStore.save(doc);

        keyVaultService = new KeyVaultService(vaultStore, new IdentityCmkProvider(), (KeyVaultProperties) null);
        keyVaultService.ensureVaultInitialized(TEST_NAMESPACE);

        CryptographyProperties cryptoProps = new CryptographyProperties();
        TenantProperties tenantProps = new TenantProperties();
        metadataCache = new EntityMetadataCache(cryptoProps, tenantProps);

        service = new DekReEncryptionService(
                metadataCache,
                keyVaultService,
                new TestStorageAdapter(),
                rewriteStore,
                new TypeSerializer()
        );
    }

    @Test
    void reEncryptSkipsAlreadyCurrentDocuments() {
        // Document already encrypted with v2 (active)
        String blob = CryptoCodec.encrypt(dekV2, "test@example.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 2);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", Map.of("c", blob, "_e", 1, "_t", "STR"));
        fields.put("_collection", "testUser");

        RawDocument doc = new RawDocument("doc1", fields, null);
        rewriteStore.addDocument(doc);

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        // Should skip because already at active version
        assertThat(result.docsSkipped()).isEqualTo(1);
        assertThat(result.docsProcessed()).isZero();
        assertThat(rewriteStore.getReplaceCalls()).isZero();
    }

    @Test
    void reEncryptProcessesOldVersionDocuments() {
        // Document encrypted with v1 (old)
        String blob = CryptoCodec.encrypt(dekV1, "old@example.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", Map.of("c", blob, "_e", 1, "_t", "STR"));
        fields.put("_collection", "testUser");

        RawDocument doc = new RawDocument("doc1", fields, null);
        rewriteStore.addDocument(doc);

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        assertThat(result.docsProcessed()).isEqualTo(1);
        assertThat(result.fieldsReEncrypted()).isEqualTo(1);
        assertThat(rewriteStore.getReplaceCalls()).isEqualTo(1);
    }

    @Test
    void reEncryptHandlesCasConflict() {
        // Document encrypted with v1
        String blob = CryptoCodec.encrypt(dekV1, "conflict@example.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", Map.of("c", blob, "_e", 1, "_t", "STR"));
        fields.put("_collection", "testUser");

        RawDocument doc = new RawDocument("doc1", fields, null);
        rewriteStore.addDocument(doc);
        rewriteStore.setFailReplace(true); // Simulate CAS conflict

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        // CAS conflict means replace returns false, counted as skipped
        assertThat(result.docsSkipped()).isEqualTo(1);
    }

    @Test
    void reEncryptSavesCheckpoint() {
        String blob = CryptoCodec.encrypt(dekV1, "checkpoint@example.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", Map.of("c", blob, "_e", 1, "_t", "STR"));
        fields.put("_collection", "testUser");

        RawDocument doc = new RawDocument("doc1", fields, null);
        rewriteStore.addDocument(doc);

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class)
                .withTaskId("test-task-123");
        service.reEncrypt(TestUser.class, options);

        // Checkpoint should be saved
        Optional<String> checkpoint = rewriteStore.loadCheckpoint("test-task-123");
        assertThat(checkpoint).isPresent();
        assertThat(checkpoint.get()).isEqualTo("doc1");
    }

    @Test
    void reEncryptWithDryRunDoesNotModify() {
        String blob = CryptoCodec.encrypt(dekV1, "dryrun@example.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", Map.of("c", blob, "_e", 1, "_t", "STR"));
        fields.put("_collection", "testUser");

        RawDocument doc = new RawDocument("doc1", fields, null);
        rewriteStore.addDocument(doc);

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class)
                .withDryRun(true);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        // Dry run counts but doesn't replace
        assertThat(result.fieldsReEncrypted()).isEqualTo(1);
        assertThat(rewriteStore.getReplaceCalls()).isZero();
    }

    // ===== Test entity =====

    static class TestUser {
        @Encrypted
        private String email;
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

    // ===== Test doubles =====

    private static class IdentityCmkProvider implements CmkProvider {
        @Override public String getProviderId() { return "test-provider"; }
        @Override public String getPublicReference() { return "cmk:test"; }
        @Override public boolean supportsAlgorithm(String lclAlgorithm) { return "IDENTITY".equals(lclAlgorithm); }
        @Override public String mapAlgorithm(String lclAlgorithm) { return "IDENTITY"; }
        @Override public WrappedKey wrap(byte[] plaintextKey) { return new WrappedKey(plaintextKey.clone(), "IDENTITY"); }
        @Override public byte[] unwrap(WrappedKey wrappedKey) { return wrappedKey.ciphertext(); }
    }

    private static final class InMemoryVaultStore implements VaultStore {
        private final Map<String, VaultDocument> documents = new HashMap<>();

        @Override public void save(VaultDocument doc) { documents.put(doc.namespace(), doc); }
        @Override public Optional<VaultDocument> load(String namespace) { return Optional.ofNullable(documents.get(namespace)); }
        @Override public boolean exists(String namespace) { return documents.containsKey(namespace); }
        @Override public VaultDocument rotate(VaultDocument updatedDoc) {
            documents.put(updatedDoc.namespace(), updatedDoc);
            return updatedDoc;
        }
        @Override public List<VaultDocument> loadAll() { return new ArrayList<>(documents.values()); }
    }

    private static final class InMemoryDocumentRewriteStore implements DocumentRewriteStore {
        private final List<RawDocument> documents = new ArrayList<>();
        private final Map<String, String> checkpoints = new HashMap<>();
        private int replaceCalls = 0;
        private boolean failReplace = false;

        void addDocument(RawDocument doc) { documents.add(doc); }
        void setFailReplace(boolean fail) { this.failReplace = fail; }
        int getReplaceCalls() { return replaceCalls; }

        @Override
        public CloseableIterator<RawDocument> scan(ScanOptions options) {
            Iterator<RawDocument> iter = documents.iterator();
            return new CloseableIterator<>() {
                @Override public boolean hasNext() { return iter.hasNext(); }
                @Override public RawDocument next() { return iter.next(); }
                @Override public void close() { }
            };
        }

        @Override
        public boolean replace(RawDocument document) {
            replaceCalls++;
            return !failReplace;
        }

        @Override
        public int replaceBatch(List<RawDocument> docs) {
            replaceCalls += docs.size();
            return failReplace ? 0 : docs.size();
        }

        @Override
        public void saveCheckpoint(String taskId, String cursorState) {
            checkpoints.put(taskId, cursorState);
        }

        @Override
        public Optional<String> loadCheckpoint(String taskId) {
            return Optional.ofNullable(checkpoints.get(taskId));
        }
    }

    private static final class TestStorageAdapter implements StorageAdapter {
        @Override
        public Object buildEncryptedPayload(String blob, String typeMarker, String blindIndex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("c", blob);
            payload.put("_e", 1);
            payload.put("_t", typeMarker);
            if (blindIndex != null) {
                payload.put("b", blindIndex);
            }
            return payload;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String extractBlob(Object payload) {
            if (payload instanceof Map<?, ?> map) {
                return (String) map.get("c");
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String extractTypeMarker(Object payload) {
            if (payload instanceof Map<?, ?> map) {
                return (String) map.get("_t");
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String extractBlindIndex(Object payload) {
            if (payload instanceof Map<?, ?> map) {
                return (String) map.get("b");
            }
            return null;
        }

        @Override
        public boolean isEncryptedPayload(Object value) {
            if (value instanceof Map<?, ?> map) {
                return map.containsKey("_e") && Integer.valueOf(1).equals(map.get("_e"));
            }
            return false;
        }
    }
}
