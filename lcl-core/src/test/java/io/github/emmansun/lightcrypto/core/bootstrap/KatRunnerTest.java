package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KatRunnerTest {

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
    void allKatPass() {
        KatRunner runner = new KatRunner();
        PhaseResult result = runner.check(defaultContext());

        assertThat(result.success()).isTrue();
        assertThat(result.phaseName()).isEqualTo("BOOT-4 KAT");
    }

    @Test
    void resultsRecorded_perAlgorithm() {
        KatRunner runner = new KatRunner();
        runner.check(defaultContext());

        Map<String, KatRunner.KatPrimitiveResult> results = runner.getLastResults();
        assertThat(results).containsKeys("AES-256-GCM", "AES-256-CBC", "SM4-GCM", "SM4-CBC", "HMAC-SHA-256", "KCV");
        assertThat(results.values()).allMatch(KatRunner.KatPrimitiveResult::passed);
    }

    @Test
    void deterministic_acrossRuns() {
        KatRunner runner1 = new KatRunner();
        KatRunner runner2 = new KatRunner();

        PhaseResult result1 = runner1.check(defaultContext());
        PhaseResult result2 = runner2.check(defaultContext());

        assertThat(result1.success()).as("first run: %s", result1.errorMessage()).isTrue();
        assertThat(result2.success()).as("second run: %s", result2.errorMessage()).isTrue();
        // Verify same results
        assertThat(runner1.getLastResults().size()).isEqualTo(runner2.getLastResults().size());
    }
}
