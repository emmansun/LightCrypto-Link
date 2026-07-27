package io.github.emmansun.lightcrypto.diagnostics;

import io.github.emmansun.lightcrypto.config.RuntimeProperties;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LclBootstrapRunnerTest {

    private static final CmkProvider STUB_CMK = new CmkProvider() {
        @Override
        public String getProviderId() {
            return "stub";
        }

        @Override
        public String getPublicReference() {
            return "stub-ref";
        }

        @Override
        public boolean supportsAlgorithm(String lclAlgorithm) {
            return true;
        }

        @Override
        public String mapAlgorithm(String lclAlgorithm) {
            return lclAlgorithm;
        }

        @Override
        public WrappedKey wrap(byte[] plaintextKey) {
            return new WrappedKey(plaintextKey, "STUB");
        }

        @Override
        public byte[] unwrap(WrappedKey wrappedKey) {
            return wrappedKey.ciphertext();
        }
    };

    @Test
    void runReadyWhenStrictAndVaultReachable() {
        LclBootstrapRunner runner = new LclBootstrapRunner(
                STUB_CMK,
                NoOpEventBus.INSTANCE,
                runtimeProperties(true, Duration.ofSeconds(5)),
                new ReachableVaultStore());

        runner.run(null);

        assertThat(runner.getLastResult()).isNotNull();
        assertThat(runner.getLastResult().status()).isEqualTo(BootstrapResult.Status.READY);
        assertThat(runner.getKatRunner()).isNotNull();
    }

    @Test
    void runDegradedWhenNotStrictAndVaultMissing() {
        LclBootstrapRunner runner = new LclBootstrapRunner(
                STUB_CMK,
                NoOpEventBus.INSTANCE,
                runtimeProperties(false, Duration.ofSeconds(5)),
                null);

        runner.run(null);

        assertThat(runner.getLastResult()).isNotNull();
        assertThat(runner.getLastResult().status()).isEqualTo(BootstrapResult.Status.DEGRADED);
        assertThat(runner.getLastResult().failedPhase()).isEqualTo("BOOT-8 Vault");
    }

    @Test
    void runFailedWhenStrictAndVaultMissing() {
        LclBootstrapRunner runner = new LclBootstrapRunner(
                STUB_CMK,
                NoOpEventBus.INSTANCE,
                runtimeProperties(true, Duration.ofSeconds(5)),
                null);

        runner.run(null);

        assertThat(runner.getLastResult()).isNotNull();
        assertThat(runner.getLastResult().status()).isEqualTo(BootstrapResult.Status.FAILED);
        assertThat(runner.getLastResult().failedPhase()).isEqualTo("BOOT-8 Vault");
    }

    @Test
    void runStoresFailedResultWhenBootstrapEngineThrows() {
        LclBootstrapRunner runner = new LclBootstrapRunner(
                STUB_CMK,
                NoOpEventBus.INSTANCE,
                runtimeProperties(true, Duration.ZERO),
                new ReachableVaultStore());

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(RuntimeException.class);
        assertThat(runner.getLastResult()).isNotNull();
        assertThat(runner.getLastResult().status()).isEqualTo(BootstrapResult.Status.FAILED);
        assertThat(runner.getLastResult().failedPhase()).isEqualTo("bootstrap");
    }

    private static RuntimeProperties runtimeProperties(boolean strictMode, Duration timeout) {
        RuntimeProperties runtimeProperties = new RuntimeProperties();
        runtimeProperties.setStrictMode(strictMode);
        runtimeProperties.setBootstrapTimeout(timeout);
        runtimeProperties.setSpiVersion(1);
        return runtimeProperties;
    }

    private static final class ReachableVaultStore implements VaultStore {
        @Override
        public void save(VaultDocument doc) {
        }

        @Override
        public Optional<VaultDocument> load(String namespace) {
            return Optional.empty();
        }

        @Override
        public boolean exists(String namespace) {
            return false;
        }

        @Override
        public VaultDocument rotate(VaultDocument updatedDoc) {
            return updatedDoc;
        }

        @Override
        public List<VaultDocument> loadAll() {
            return List.of();
        }
    }
}
