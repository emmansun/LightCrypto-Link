package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.config.CryptographyProperties;
import io.github.emmansun.lightcrypto.config.KeyVaultProperties;
import io.github.emmansun.lightcrypto.config.TenantProperties;
import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
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
    private static final String PHONE_NAMESPACE = "default.default.TestUserWithBlindIndex#phone";

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

        // Setup vault for blind index entity (same keys, different namespace)
        VaultDocument phoneDoc = vaultDoc(PHONE_NAMESPACE, "v2-bbbbbbbb", List.of(
                rotatedEntry("v1-aaaaaaaa", dekV1, hmacV1),
                activeEntry("v2-bbbbbbbb", dekV2, hmacV2)));
        vaultStore.save(phoneDoc);

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

        RawDocument doc = new RawDocument("doc1", fields, Map.of());
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

        RawDocument doc = new RawDocument("doc1", fields, Map.of());
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

        // fieldKids carries the kid snapshot from scan phase
        RawDocument doc = new RawDocument("doc1", fields, Map.of("email", "v1-aaaaaaaa"));
        rewriteStore.addDocument(doc);
        rewriteStore.setFailReplace(true); // Simulate CAS conflict (_k changed concurrently)

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

        RawDocument doc = new RawDocument("doc1", fields, Map.of());
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

        RawDocument doc = new RawDocument("doc1", fields, Map.of());
        rewriteStore.addDocument(doc);

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class)
                .withDryRun(true);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        // Dry run counts but doesn't replace
        assertThat(result.fieldsReEncrypted()).isEqualTo(1);
        assertThat(rewriteStore.getReplaceCalls()).isZero();
    }

    @Test
    void reEncryptWithBlindIndexRecomputesIndex() {
        // Entity with blindIndex=true
        String blob = CryptoCodec.encrypt(dekV1, "13800001111".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(PHONE_NAMESPACE), 1);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("phone", Map.of("c", blob, "_e", 1, "_t", "STR", "b", "old-blind-index"));
        fields.put("_collection", "testUserBi");

        RawDocument doc = new RawDocument("doc-bi", fields, Map.of());
        rewriteStore.addDocument(doc);

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUserWithBlindIndex.class);
        ReEncryptResult result = service.reEncrypt(TestUserWithBlindIndex.class, options);

        assertThat(result.docsProcessed()).isEqualTo(1);
        assertThat(result.fieldsReEncrypted()).isEqualTo(1);

        // Verify the payload was rebuilt with a new blind index (not the old one)
        RawDocument replaced = rewriteStore.getLastReplacedDoc();
        assertThat(replaced).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) replaced.fields().get("phone");
        assertThat(payload.get("b")).isNotNull();
        assertThat(payload.get("b")).isNotEqualTo("old-blind-index");
    }

    @Test
    void reEncryptMultipleBatchesWithCheckpointInterval() {
        // Create 7 documents with batchSize=3, checkpointInterval=2
        for (int i = 0; i < 7; i++) {
            String blob = CryptoCodec.encrypt(dekV1, ("user" + i + "@test.com").getBytes(),
                    AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("email", Map.of("c", blob, "_e", 1, "_t", "STR"));
            fields.put("_collection", "testUser");
            rewriteStore.addDocument(new RawDocument("doc-" + i, fields, Map.of()));
        }

        ReEncryptOptions options = new ReEncryptOptions(TestUser.class, 3, "batch-task", false, 2);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        assertThat(result.docsProcessed()).isEqualTo(7);
        assertThat(result.fieldsReEncrypted()).isEqualTo(7);
        assertThat(result.success()).isTrue();
        assertThat(result.totalDocsScanned()).isEqualTo(7);

        // Checkpoint should be saved (at batch 2 and final)
        Optional<String> checkpoint = rewriteStore.loadCheckpoint("batch-task");
        assertThat(checkpoint).isPresent();
    }

    @Test
    void reEncryptHandlesDocumentException() {
        // First doc: valid old version
        String validBlob = CryptoCodec.encrypt(dekV1, "valid@test.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
        Map<String, Object> validFields = new LinkedHashMap<>();
        validFields.put("email", Map.of("c", validBlob, "_e", 1, "_t", "STR"));
        validFields.put("_collection", "testUser");
        rewriteStore.addDocument(new RawDocument("doc-valid", validFields, Map.of()));

        // Second doc: valid wire format but encrypted with WRONG key (GCM auth will fail)
        byte[] wrongDek = fixedKey((byte) 0x99);
        String badBlob = CryptoCodec.encrypt(wrongDek, "bad@test.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
        Map<String, Object> corruptFields = new LinkedHashMap<>();
        corruptFields.put("email", Map.of("c", badBlob, "_e", 1, "_t", "STR"));
        corruptFields.put("_collection", "testUser");
        rewriteStore.addDocument(new RawDocument("doc-corrupt", corruptFields, Map.of()));

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        // One processed, one failed
        assertThat(result.docsProcessed()).isEqualTo(1);
        assertThat(result.docsFailed()).isEqualTo(1);
        assertThat(result.success()).isFalse();
        assertThat(result.totalDocsScanned()).isEqualTo(2);
    }

    @Test
    void reEncryptSkipsNullAndNonEncryptedFields() {
        // Document with null email and a non-encrypted payload
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", null);
        fields.put("_collection", "testUser");
        rewriteStore.addDocument(new RawDocument("doc-null", fields, Map.of()));

        // Document with non-encrypted field (plain string, not a payload map)
        Map<String, Object> fields2 = new LinkedHashMap<>();
        fields2.put("email", "plain-text-not-encrypted");
        fields2.put("_collection", "testUser");
        rewriteStore.addDocument(new RawDocument("doc-plain", fields2, Map.of()));

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        // Both docs skipped (no encrypted payloads found)
        assertThat(result.docsSkipped()).isEqualTo(2);
        assertThat(result.docsProcessed()).isZero();
    }

    @Test
    void reEncryptSkipsInvalidWireFormat() {
        // Document with invalid base64url blob
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", Map.of("c", "not-valid-base64url!!!", "_e", 1, "_t", "STR"));
        fields.put("_collection", "testUser");
        rewriteStore.addDocument(new RawDocument("doc-invalid", fields, Map.of()));

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class);
        ReEncryptResult result = service.reEncrypt(TestUser.class, options);

        // Skipped because wire format decode fails gracefully
        assertThat(result.docsSkipped()).isEqualTo(1);
        assertThat(result.docsFailed()).isZero();
    }

    @Test
    void reEncryptAllReturnsEmptyPlaceholder() {
        ReEncryptOptions options = ReEncryptOptions.forAll();
        List<ReEncryptResult> results = service.reEncryptAll(options);
        assertThat(results).isEmpty();
    }

    @Test
    void reEncryptEntityWithNoEncryptedFields() {
        ReEncryptOptions options = ReEncryptOptions.forEntity(PlainEntity.class);
        ReEncryptResult result = service.reEncrypt(PlainEntity.class, options);

        assertThat(result.namespace()).isEqualTo("none");
        assertThat(result.totalDocsScanned()).isZero();
    }

    @Test
    void reEncryptEmitsEventsToEventBus() {
        List<LclEvent> capturedEvents = new ArrayList<>();
        EventBus capturingBus = capturedEvents::add;

        DekReEncryptionService serviceWithBus = new DekReEncryptionService(
                metadataCache, keyVaultService, new TestStorageAdapter(),
                rewriteStore, new TypeSerializer(), capturingBus);

        String blob = CryptoCodec.encrypt(dekV1, "event@test.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", Map.of("c", blob, "_e", 1, "_t", "STR"));
        fields.put("_collection", "testUser");
        rewriteStore.addDocument(new RawDocument("doc-evt", fields, Map.of()));

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class).withBatchSize(1);
        serviceWithBus.reEncrypt(TestUser.class, options);

        // Should emit batch event + completion event
        assertThat(capturedEvents).hasSizeGreaterThanOrEqualTo(2);
        assertThat(capturedEvents.stream().map(LclEvent::event))
                .contains("lcl.reencrypt.batch.completed", "lcl.reencrypt.namespace.completed");

        LclEvent completion = capturedEvents.stream()
                .filter(e -> e.event().equals("lcl.reencrypt.namespace.completed"))
                .findFirst().orElseThrow();
        assertThat(completion.result()).isEqualTo("success");
        assertThat(completion.namespace()).isEqualTo(TEST_NAMESPACE);
    }

    @Test
    void reEncryptEmitsPartialResultOnFailure() {
        List<LclEvent> capturedEvents = new ArrayList<>();
        EventBus capturingBus = capturedEvents::add;

        DekReEncryptionService serviceWithBus = new DekReEncryptionService(
                metadataCache, keyVaultService, new TestStorageAdapter(),
                rewriteStore, new TypeSerializer(), capturingBus);

        // Valid wire format but wrong key → GCM auth failure
        byte[] wrongDek = fixedKey((byte) 0x99);
        String badBlob = CryptoCodec.encrypt(wrongDek, "fail@test.com".getBytes(),
                AlgorithmId.AES_256_GCM, Namespace.parse(TEST_NAMESPACE), 1);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", Map.of("c", badBlob, "_e", 1, "_t", "STR"));
        fields.put("_collection", "testUser");
        rewriteStore.addDocument(new RawDocument("doc-fail", fields, Map.of()));

        ReEncryptOptions options = ReEncryptOptions.forEntity(TestUser.class);
        serviceWithBus.reEncrypt(TestUser.class, options);

        LclEvent completion = capturedEvents.stream()
                .filter(e -> e.event().equals("lcl.reencrypt.namespace.completed"))
                .findFirst().orElseThrow();
        assertThat(completion.result()).isEqualTo("partial");
    }

    @Test
    void reEncryptOptionsHelperMethods() {
        ReEncryptOptions base = ReEncryptOptions.forEntity(TestUser.class);
        assertThat(base.entityClass()).isEqualTo(TestUser.class);
        assertThat(base.batchSize()).isEqualTo(ReEncryptOptions.DEFAULT_BATCH_SIZE);
        assertThat(base.dryRun()).isFalse();
        assertThat(base.taskId()).isNull();
        assertThat(base.checkpointInterval()).isEqualTo(ReEncryptOptions.DEFAULT_CHECKPOINT_INTERVAL);

        ReEncryptOptions modified = base.withBatchSize(100).withTaskId("my-task").withDryRun(true);
        assertThat(modified.batchSize()).isEqualTo(100);
        assertThat(modified.taskId()).isEqualTo("my-task");
        assertThat(modified.dryRun()).isTrue();
        assertThat(modified.entityClass()).isEqualTo(TestUser.class);

        ReEncryptOptions all = ReEncryptOptions.forAll();
        assertThat(all.entityClass()).isNull();
    }

    @Test
    void reEncryptResultHelperMethods() {
        ReEncryptResult success = new ReEncryptResult("ns", 10, 2, 0, 10, 5000);
        assertThat(success.success()).isTrue();
        assertThat(success.totalDocsScanned()).isEqualTo(12);

        ReEncryptResult partial = new ReEncryptResult("ns", 8, 1, 3, 8, 9000);
        assertThat(partial.success()).isFalse();
        assertThat(partial.totalDocsScanned()).isEqualTo(12);
    }

    // ===== Test entities =====

    static class TestUser {
        @Encrypted
        private String email;
    }

    static class TestUserWithBlindIndex {
        @Encrypted(blindIndex = true)
        private String phone;
    }

    static class PlainEntity {
        private String name;
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
        private final List<RawDocument> replacedDocs = new ArrayList<>();
        private int replaceCalls = 0;
        private boolean failReplace = false;

        void addDocument(RawDocument doc) { documents.add(doc); }
        void setFailReplace(boolean fail) { this.failReplace = fail; }
        int getReplaceCalls() { return replaceCalls; }
        RawDocument getLastReplacedDoc() { return replacedDocs.isEmpty() ? null : replacedDocs.get(replacedDocs.size() - 1); }

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
            if (!failReplace) {
                replacedDocs.addAll(docs);
            }
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
