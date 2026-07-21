package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CanaryRunnerTest {

    private static final CmkProvider STUB_CMK = new CmkProvider() {
        @Override public String getProviderId() { return "stub"; }
        @Override public String getPublicReference() { return "stub-ref"; }
        @Override public boolean supportsAlgorithm(String a) { return true; }
        @Override public String mapAlgorithm(String a) { return a; }
        @Override public WrappedKey wrap(byte[] key) { return new WrappedKey(key, "STUB"); }
        @Override public byte[] unwrap(WrappedKey w) { return w.ciphertext(); }
    };

    private BootstrapContext defaultContext() {
        return BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(NoOpEventBus.INSTANCE)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Test
    void canaryRoundtrip_allAlgorithms_succeeds() {
        CanaryRunner runner = new CanaryRunner();
        PhaseResult result = runner.check(defaultContext());

        assertThat(result.success()).isTrue();
        assertThat(result.phaseName()).isEqualTo("BOOT-10 Canary");
    }

    @Test
    void canaryMetadataRoundtrip_succeeds() {
        // The metadata roundtrip is part of the canary check
        CanaryRunner runner = new CanaryRunner();
        PhaseResult result = runner.check(defaultContext());

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
    }
}
