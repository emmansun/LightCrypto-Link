package io.github.emmansun.lightcrypto.diagnostics;

import io.github.emmansun.lightcrypto.config.RuntimeProperties;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapResult;
import io.github.emmansun.lightcrypto.core.bootstrap.PhaseResult;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.observability.ComponentHealthCheck;
import io.github.emmansun.lightcrypto.observability.LclHealthStatus;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsAutoConfigurationTest {

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
    void lclBootstrapRunnerBeanCanBeCreatedWithoutVaultStore() {
        DiagnosticsAutoConfiguration configuration = new DiagnosticsAutoConfiguration();
        RuntimeProperties runtimeProperties = runtimeProperties();
        ObjectProvider<VaultStore> provider = new StaticListableBeanFactory().getBeanProvider(VaultStore.class);

        LclBootstrapRunner runner = configuration.lclBootstrapRunner(STUB_CMK, NoOpEventBus.INSTANCE, runtimeProperties, provider);

        assertThat(runner).isNotNull();
        assertThat(runner.getLastResult()).isNull();
        assertThat(runner.getKatRunner()).isNotNull();
    }

    @Test
    void lclBootstrapRunnerBeanCanBeCreatedWithVaultStore() {
        DiagnosticsAutoConfiguration configuration = new DiagnosticsAutoConfiguration();
        RuntimeProperties runtimeProperties = runtimeProperties();
        VaultStore vaultStore = new NoOpVaultStore();
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("vaultStore", vaultStore);

        LclBootstrapRunner runner = configuration.lclBootstrapRunner(
                STUB_CMK,
                NoOpEventBus.INSTANCE,
                runtimeProperties,
                factory.getBeanProvider(VaultStore.class));

        assertThat(runner).isNotNull();
        assertThat(runner.getKatRunner()).isNotNull();
    }

    @Test
    void bootstrapHealthConfigurationMapsStatusesCorrectly() {
        DiagnosticsAutoConfiguration.BootstrapHealthConfiguration configuration =
                new DiagnosticsAutoConfiguration.BootstrapHealthConfiguration();

        ComponentHealthCheck starting = configuration.bootstrapHealthCheck(runnerWith(null));
        ComponentHealthCheck ready = configuration.bootstrapHealthCheck(runnerWith(
                BootstrapResult.ready(List.of(PhaseResult.success("BOOT-4 KAT", 5)), 5)));
        ComponentHealthCheck degraded = configuration.bootstrapHealthCheck(runnerWith(
                BootstrapResult.degraded(List.of(PhaseResult.failure("BOOT-9 KMS", 3, "timeout")), 3, "BOOT-9 KMS", "timeout")));
        ComponentHealthCheck failed = configuration.bootstrapHealthCheck(runnerWith(
                BootstrapResult.failed(List.of(PhaseResult.failure("BOOT-10 Canary", 4, "fatal")), 4, "BOOT-10 Canary", "fatal")));

        assertThat(starting.check()).isEqualTo(LclHealthStatus.STARTING);
        assertThat(ready.check()).isEqualTo(LclHealthStatus.READY);
        assertThat(degraded.check()).isEqualTo(LclHealthStatus.DEGRADED);
        assertThat(failed.check()).isEqualTo(LclHealthStatus.FAILED);
    }

    @Test
    void diagnosticsEndpointsConfigurationCreatesEndpointBeans() {
        DiagnosticsAutoConfiguration.DiagnosticsEndpointsConfiguration configuration =
                new DiagnosticsAutoConfiguration.DiagnosticsEndpointsConfiguration();
        LclBootstrapRunner runner = runnerWith(BootstrapResult.ready(List.of(), 1));

        LclHealthEndpoint healthEndpoint = configuration.lclHealthEndpoint(runner);
        LclKatEndpoint katEndpoint = configuration.lclKatEndpoint(runner, STUB_CMK);

        assertThat(healthEndpoint).isNotNull();
        assertThat(katEndpoint).isNotNull();
        assertThat(healthEndpoint.health()).containsEntry("status", "READY");
    }

    private static RuntimeProperties runtimeProperties() {
        RuntimeProperties runtimeProperties = new RuntimeProperties();
        runtimeProperties.setBootstrapTimeout(Duration.ofSeconds(5));
        runtimeProperties.setStrictMode(true);
        runtimeProperties.setSpiVersion(1);
        return runtimeProperties;
    }

    private static LclBootstrapRunner runnerWith(BootstrapResult result) {
        return new LclBootstrapRunner(STUB_CMK, NoOpEventBus.INSTANCE, runtimeProperties(), null) {
            @Override
            public BootstrapResult getLastResult() {
                return result;
            }
        };
    }

    private static final class NoOpVaultStore implements VaultStore {
        @Override
        public java.util.Optional<io.github.emmansun.lightcrypto.spi.VaultDocument> load(String namespace) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean exists(String namespace) {
            return false;
        }

        @Override
        public void save(io.github.emmansun.lightcrypto.spi.VaultDocument doc) {
        }

        @Override
        public io.github.emmansun.lightcrypto.spi.VaultDocument rotate(
                io.github.emmansun.lightcrypto.spi.VaultDocument updatedDoc) {
            return updatedDoc;
        }

        @Override
        public java.util.List<io.github.emmansun.lightcrypto.spi.VaultDocument> loadAll() {
            return java.util.List.of();
        }
    }
}
