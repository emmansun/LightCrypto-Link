package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapChecksTest {

    private static final CmkProvider STUB_CMK = new CmkProvider() {
        @Override public String getProviderId() { return "stub"; }
        @Override public String getPublicReference() { return "stub-ref"; }
        @Override public boolean supportsAlgorithm(String a) { return true; }
        @Override public String mapAlgorithm(String a) { return a; }
        @Override public WrappedKey wrap(byte[] key) { return new WrappedKey(key, "STUB"); }
        @Override public byte[] unwrap(WrappedKey w) { return w.ciphertext(); }
    };

    private BootstrapContext.Builder baseBuilder() {
        return BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(NoOpEventBus.INSTANCE)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(15));
    }

    // === ConfigValidationCheck ===

    @Test
    void configValidation_validConfig_passes() {
        ConfigValidationCheck check = new ConfigValidationCheck();
        PhaseResult result = check.check(baseBuilder().build());
        assertThat(result.success()).isTrue();
    }

    @Test
    void configValidation_invalidSpiVersion_fails() {
        ConfigValidationCheck check = new ConfigValidationCheck();
        PhaseResult result = check.check(baseBuilder().spiVersion(0).build());
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("spiVersion");
    }

    // === SpiVersionCheck ===

    @Test
    void spiVersionCheck_version1_passes() {
        SpiVersionCheck check = new SpiVersionCheck();
        PhaseResult result = check.check(baseBuilder().spiVersion(1).build());
        assertThat(result.success()).isTrue();
    }

    @Test
    void spiVersionCheck_version2_fails() {
        SpiVersionCheck check = new SpiVersionCheck();
        PhaseResult result = check.check(baseBuilder().spiVersion(2).build());
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("mismatch");
    }

    // === VaultReachabilityCheck ===

    @Test
    void vaultReachability_noVault_fails() {
        VaultReachabilityCheck check = new VaultReachabilityCheck();
        PhaseResult result = check.check(baseBuilder().build());
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("No VaultStore");
    }

    @Test
    void vaultReachability_reachableVault_passes() {
        VaultStore stubVault = new VaultStore() {
            @Override public void save(VaultDocument doc) {}
            @Override public Optional<VaultDocument> load(String ns) { return Optional.empty(); }
            @Override public boolean exists(String ns) { return false; }
            @Override public VaultDocument rotate(VaultDocument doc) { return doc; }
            @Override public List<VaultDocument> loadAll() { return List.of(); }
        };
        VaultReachabilityCheck check = new VaultReachabilityCheck();
        PhaseResult result = check.check(baseBuilder().vaultStore(stubVault).build());
        assertThat(result.success()).isTrue();
    }

    // === KmsReachabilityCheck ===

    @Test
    void kmsReachability_workingKms_passes() {
        KmsReachabilityCheck check = new KmsReachabilityCheck();
        PhaseResult result = check.check(baseBuilder().build());
        assertThat(result.success()).isTrue();
    }

    @Test
    void kmsReachability_failingKms_fails() {
        CmkProvider failingKms = new CmkProvider() {
            @Override public String getProviderId() { return "failing"; }
            @Override public String getPublicReference() { return "fail-ref"; }
            @Override public boolean supportsAlgorithm(String a) { return true; }
            @Override public String mapAlgorithm(String a) { return a; }
            @Override public WrappedKey wrap(byte[] key) { throw new RuntimeException("KMS down"); }
            @Override public byte[] unwrap(WrappedKey w) { throw new RuntimeException("KMS down"); }
        };
        KmsReachabilityCheck check = new KmsReachabilityCheck();
        PhaseResult result = check.check(baseBuilder().cmkProvider(failingKms).build());
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("KMS unreachable");
    }
}
