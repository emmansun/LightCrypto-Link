package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.KeyVaultProperties;
import io.github.emmansun.lightcrypto.config.RewrapProperties;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.exception.OptimisticLockException;
import io.github.emmansun.lightcrypto.migration.CmkProviderRewrapRunner;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.RewrapResult;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyEntry;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyStatus;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for cross-CMK re-wrap functionality (tasks 4.1-4.8).
 */
class CmkRewrapTest {

    private static final AlgorithmId KCV_ALGORITHM = AlgorithmId.AES_256_GCM;
    private static final String TEST_NAMESPACE = "default.default.User#phone";

    // ===== 4.1 rewrapVault happy path =====

    @Test
    void rewrapVaultHappyPathUpdatesProviderAndAlgorithm() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        TargetCmkProvider targetProvider = new TargetCmkProvider();

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(activeEntry(kid, dek, hmac)), "source-provider"));

        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        RewrapResult result = service.rewrapVault(TEST_NAMESPACE, targetProvider);

        assertThat(result.success()).isTrue();
        assertThat(result.namespace()).isEqualTo(TEST_NAMESPACE);
        assertThat(result.keyCount()).isEqualTo(1);

        VaultDocument updated = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(updated.cmkProvider()).isEqualTo("target-provider");
        assertThat(updated.cmkId()).isEqualTo("cmk:target");
        assertThat(updated.version()).isEqualTo(2L);
        assertThat(updated.keys().get(0).wrappingAlgorithm()).isEqualTo("TARGET-IDENTITY");
        // KCV unchanged
        assertThat(updated.keys().get(0).dekKcv()).isEqualTo(KeyCheckValue.computeDekKcv(dek, KCV_ALGORITHM));
        assertThat(updated.keys().get(0).hmacKcv()).isEqualTo(KeyCheckValue.computeHmacKcv(hmac));
    }

    // ===== 4.2 rewrapVault same-provider same-key no-op =====

    @Test
    void rewrapVaultSameProviderSameKeyIsNoOp() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        // Vault doc cmkId matches sourceProvider.getPublicReference()
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(activeEntry(kid, dek, hmac)), "source-provider", "cmk:source"));

        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        RewrapResult result = service.rewrapVault(TEST_NAMESPACE, sourceProvider);

        assertThat(result.success()).isTrue();
        // VaultStore.rotate() NOT called — version unchanged
        VaultDocument doc = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(doc.version()).isEqualTo(1L);
        assertThat(vaultStore.rotateCalls).isZero();
    }

    // ===== 4.2b rewrapVault same-providerId different-key proceeds =====

    @Test
    void rewrapVaultSameProviderIdDifferentKeyProceeds() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        // Target has SAME providerId but DIFFERENT publicReference (simulates key rotation)
        SameIdDifferentKeyProvider targetProvider = new SameIdDifferentKeyProvider();

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(activeEntry(kid, dek, hmac)), "source-provider", "cmk:source"));

        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        RewrapResult result = service.rewrapVault(TEST_NAMESPACE, targetProvider);

        // Should proceed (not skip) because publicReference differs
        assertThat(result.success()).isTrue();
        assertThat(vaultStore.rotateCalls).isEqualTo(1);
        VaultDocument updated = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(updated.cmkProvider()).isEqualTo("source-provider"); // same ID
        assertThat(updated.cmkId()).isEqualTo("cmk:source-v2"); // new reference
    }

    // ===== 4.3 rewrapVault KCV mismatch =====

    @Test
    void rewrapVaultKcvMismatchThrowsFatalCryptoException() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        TargetCmkProvider targetProvider = new TargetCmkProvider();

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        // Corrupt the stored KCV
        KeyEntry corruptedEntry = new KeyEntry(
                kid, KeyStatus.ACTIVE, dek, hmac, "SOURCE-IDENTITY",
                "corrupted-kcv", KeyCheckValue.computeHmacKcv(hmac),
                KeyCheckValue.computeBinding(hmac, dek), Instant.now());
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(corruptedEntry), "source-provider"));

        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        assertThatThrownBy(() -> service.rewrapVault(TEST_NAMESPACE, targetProvider))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("KCV mismatch");
    }

    // ===== 4.4 rewrapVault optimistic lock conflict =====

    @Test
    void rewrapVaultOptimisticLockConflictThrowsCleanError() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        TargetCmkProvider targetProvider = new TargetCmkProvider();

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        String kid = "v1-a1b2c3d4";
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, kid,
                List.of(activeEntry(kid, dek, hmac)), "source-provider"));
        vaultStore.failRotateWithOptimisticLock = true;

        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        assertThatThrownBy(() -> service.rewrapVault(TEST_NAMESPACE, targetProvider))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("Concurrent modification detected during re-wrap");
    }

    // ===== 4.5 rewrapAllVaults partial failure =====

    @Test
    void rewrapAllVaultsPartialFailureIsolatesErrors() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        TargetCmkProvider targetProvider = new TargetCmkProvider();

        String ns1 = "default.default.User#phone";
        String ns2 = "default.default.User#email";
        String ns3 = "default.default.Order#amount";

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);

        vaultStore.documents.put(ns1, vaultDoc(ns1, "v1-a1b2c3d4",
                List.of(activeEntry("v1-a1b2c3d4", dek, hmac)), "source-provider"));
        // ns2 has corrupted KCV to trigger failure
        KeyEntry corruptedEntry = new KeyEntry(
                "v1-b1b2c3d4", KeyStatus.ACTIVE, dek, hmac, "SOURCE-IDENTITY",
                "bad-kcv", KeyCheckValue.computeHmacKcv(hmac),
                KeyCheckValue.computeBinding(hmac, dek), Instant.now());
        vaultStore.documents.put(ns2, vaultDoc(ns2, "v1-b1b2c3d4",
                List.of(corruptedEntry), "source-provider"));
        vaultStore.documents.put(ns3, vaultDoc(ns3, "v1-c1b2c3d4",
                List.of(activeEntry("v1-c1b2c3d4", dek, hmac)), "source-provider"));

        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        List<RewrapResult> results = service.rewrapAllVaults(targetProvider);

        assertThat(results).hasSize(3);
        long successCount = results.stream().filter(RewrapResult::success).count();
        long failedCount = results.stream().filter(r -> !r.success()).count();
        assertThat(successCount).isEqualTo(2);
        assertThat(failedCount).isEqualTo(1);
    }

    // ===== 4.6 CmkProviderRewrapRunner disabled by default =====

    @Test
    void runnerDisabledByDefaultDoesNotInteractWithKeyVaultService() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        RewrapProperties props = new RewrapProperties();
        props.setEnabled(false);

        CmkProviderRewrapRunner runner = new CmkProviderRewrapRunner(
                service, vaultStore, List.of(sourceProvider), props, NoOpEventBus.INSTANCE, mock(ApplicationContext.class));

        // Should not throw, no interaction
        runner.run();
        assertThat(vaultStore.rotateCalls).isZero();
    }

    // ===== 4.7 CmkProviderRewrapRunner dry-run =====

    @Test
    void runnerDryRunLoadsVaultsButDoesNotModify() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        TargetCmkProvider targetProvider = new TargetCmkProvider();
        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4",
                List.of(activeEntry("v1-a1b2c3d4", dek, hmac)), "source-provider"));

        RewrapProperties props = new RewrapProperties();
        props.setEnabled(true);
        props.setDryRun(true);
        props.setTargetProviderId("target-provider");

        CmkProviderRewrapRunner runner = new CmkProviderRewrapRunner(
                service, vaultStore, List.of(sourceProvider, targetProvider), props, NoOpEventBus.INSTANCE, mock(ApplicationContext.class));

        runner.run();

        // Vault not modified
        VaultDocument doc = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(doc.version()).isEqualTo(1L);
        assertThat(doc.cmkProvider()).isEqualTo("source-provider");
        assertThat(vaultStore.rotateCalls).isZero();
    }

    // ===== 4.8 CmkProviderRewrapRunner target not found =====

    @Test
    void runnerTargetNotFoundLogsErrorAndDoesNotMutate() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4",
                List.of(activeEntry("v1-a1b2c3d4", dek, hmac)), "source-provider"));

        RewrapProperties props = new RewrapProperties();
        props.setEnabled(true);
        props.setDryRun(false);
        props.setTargetProviderId("nonexistent-provider");

        CmkProviderRewrapRunner runner = new CmkProviderRewrapRunner(
                service, vaultStore, List.of(sourceProvider), props, NoOpEventBus.INSTANCE, mock(ApplicationContext.class));

        runner.run();

        // No mutation
        VaultDocument doc = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(doc.version()).isEqualTo(1L);
        assertThat(vaultStore.rotateCalls).isZero();
    }

    // ===== 4.9 CmkProviderRewrapRunner resolve by bean name =====

    @Test
    void runnerResolvesTargetByBeanName() {
        InMemoryVaultStore vaultStore = new InMemoryVaultStore();
        SourceCmkProvider sourceProvider = new SourceCmkProvider();
        TargetCmkProvider targetProvider = new TargetCmkProvider();
        KeyVaultService service = new KeyVaultService(vaultStore, sourceProvider, (KeyVaultProperties) null);

        byte[] dek = fixedKey((byte) 0x11);
        byte[] hmac = fixedKey((byte) 0x22);
        vaultStore.documents.put(TEST_NAMESPACE, vaultDoc(TEST_NAMESPACE, "v1-a1b2c3d4",
                List.of(activeEntry("v1-a1b2c3d4", dek, hmac)), "source-provider", "cmk:source"));

        RewrapProperties props = new RewrapProperties();
        props.setEnabled(true);
        props.setDryRun(false);
        props.setTargetBeanName("myTargetBean");

        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean("myTargetBean")).thenReturn(targetProvider);

        CmkProviderRewrapRunner runner = new CmkProviderRewrapRunner(
                service, vaultStore, List.of(sourceProvider, targetProvider), props, NoOpEventBus.INSTANCE, ctx);

        runner.run();

        VaultDocument updated = vaultStore.documents.get(TEST_NAMESPACE);
        assertThat(updated.cmkProvider()).isEqualTo("target-provider");
        assertThat(vaultStore.rotateCalls).isEqualTo(1);
    }

    // ===== Helpers =====

    private static byte[] fixedKey(byte b) {
        byte[] out = new byte[32];
        Arrays.fill(out, b);
        return out;
    }

    private static VaultDocument vaultDoc(String namespace, String activeKid, List<KeyEntry> keys, String cmkProvider) {
        return vaultDoc(namespace, activeKid, keys, cmkProvider, "cmk:" + cmkProvider);
    }

    private static VaultDocument vaultDoc(String namespace, String activeKid, List<KeyEntry> keys, String cmkProvider, String cmkId) {
        return new VaultDocument(
                namespace, keys, activeKid, 1L,
                cmkProvider, cmkId,
                Instant.now(), Instant.now());
    }

    private static KeyEntry activeEntry(String kid, byte[] dek, byte[] hmac) {
        return new KeyEntry(
                kid, KeyStatus.ACTIVE, dek, hmac, "SOURCE-IDENTITY",
                KeyCheckValue.computeDekKcv(dek, KCV_ALGORITHM),
                KeyCheckValue.computeHmacKcv(hmac),
                KeyCheckValue.computeBinding(hmac, dek),
                Instant.now());
    }

    // ===== Test providers =====

    private static class SourceCmkProvider implements CmkProvider {
        @Override
        public String getProviderId() { return "source-provider"; }

        @Override
        public String getPublicReference() { return "cmk:source"; }

        @Override
        public boolean supportsAlgorithm(String lclAlgorithm) { return "SOURCE-IDENTITY".equals(lclAlgorithm); }

        @Override
        public String mapAlgorithm(String lclAlgorithm) { return "SOURCE-IDENTITY"; }

        @Override
        public WrappedKey wrap(byte[] plaintextKey) { return new WrappedKey(plaintextKey.clone(), "SOURCE-IDENTITY"); }

        @Override
        public byte[] unwrap(WrappedKey wrappedKey) { return wrappedKey.ciphertext().clone(); }
    }

    private static class TargetCmkProvider implements CmkProvider {
        @Override
        public String getProviderId() { return "target-provider"; }

        @Override
        public String getPublicReference() { return "cmk:target"; }

        @Override
        public boolean supportsAlgorithm(String lclAlgorithm) { return "TARGET-IDENTITY".equals(lclAlgorithm); }

        @Override
        public String mapAlgorithm(String lclAlgorithm) { return "TARGET-IDENTITY"; }

        @Override
        public WrappedKey wrap(byte[] plaintextKey) { return new WrappedKey(plaintextKey.clone(), "TARGET-IDENTITY"); }

        @Override
        public byte[] unwrap(WrappedKey wrappedKey) { return wrappedKey.ciphertext().clone(); }
    }

    /** Same providerId as SourceCmkProvider but different publicReference (simulates key rotation). */
    private static class SameIdDifferentKeyProvider implements CmkProvider {
        @Override
        public String getProviderId() { return "source-provider"; } // SAME as source

        @Override
        public String getPublicReference() { return "cmk:source-v2"; } // DIFFERENT key

        @Override
        public boolean supportsAlgorithm(String lclAlgorithm) { return "SOURCE-IDENTITY".equals(lclAlgorithm); }

        @Override
        public String mapAlgorithm(String lclAlgorithm) { return "SOURCE-IDENTITY"; }

        @Override
        public WrappedKey wrap(byte[] plaintextKey) { return new WrappedKey(plaintextKey.clone(), "SOURCE-IDENTITY"); }

        @Override
        public byte[] unwrap(WrappedKey wrappedKey) { return wrappedKey.ciphertext().clone(); }
    }

    // ===== In-memory VaultStore =====

    private static final class InMemoryVaultStore implements VaultStore {
        private final Map<String, VaultDocument> documents = new HashMap<>();
        private boolean failRotateWithOptimisticLock;
        private int rotateCalls;

        @Override
        public void save(VaultDocument doc) {
            documents.put(doc.namespace(), doc);
        }

        @Override
        public Optional<VaultDocument> load(String namespace) {
            return Optional.ofNullable(documents.get(namespace));
        }

        @Override
        public boolean exists(String namespace) {
            return documents.containsKey(namespace);
        }

        @Override
        public VaultDocument rotate(VaultDocument updatedDoc) {
            rotateCalls++;
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
