package io.github.emmansun.lightcrypto.diagnostics;

import io.github.emmansun.lightcrypto.config.RuntimeProperties;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapResult;
import io.github.emmansun.lightcrypto.core.bootstrap.PhaseResult;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsEndpointsTest {

    private static final CmkProvider STUB_CMK = new CmkProvider() {
        @Override public String getProviderId() { return "stub"; }
        @Override public String getPublicReference() { return "stub-ref"; }
        @Override public boolean supportsAlgorithm(String a) { return true; }
        @Override public String mapAlgorithm(String a) { return a; }
        @Override public WrappedKey wrap(byte[] key) { return new WrappedKey(key, "STUB"); }
        @Override public byte[] unwrap(WrappedKey w) { return w.ciphertext(); }
    };

    private LclBootstrapRunner createRunner(BootstrapResult result) {
        RuntimeProperties props = new RuntimeProperties();
        props.setBootstrapTimeout(Duration.ofSeconds(5));
        LclBootstrapRunner runner = new LclBootstrapRunner(STUB_CMK, NoOpEventBus.INSTANCE, props, null) {
            @Override
            public BootstrapResult getLastResult() {
                return result;
            }
        };
        return runner;
    }

    // === LclHealthEndpoint ===

    @Test
    void healthEndpoint_readyState() {
        BootstrapResult readyResult = BootstrapResult.ready(
                List.of(PhaseResult.success("BOOT-4 KAT", 10), PhaseResult.success("BOOT-9 KMS", 5)),
                15);
        LclBootstrapRunner runner = createRunner(readyResult);
        LclHealthEndpoint endpoint = new LclHealthEndpoint(runner);

        Map<String, Object> response = endpoint.health();

        assertThat(response.get("status")).isEqualTo("READY");
        assertThat(response.get("sdkLanguage")).isEqualTo("java");
        assertThat(response.get("spiVersion")).isEqualTo(1);
        assertThat(response.get("wireFormatVersion")).isEqualTo(1);
        assertThat(response.get("bootstrapDurationMs")).isEqualTo(15L);
    }

    @Test
    void healthEndpoint_startingState() {
        LclBootstrapRunner runner = createRunner(null);
        LclHealthEndpoint endpoint = new LclHealthEndpoint(runner);

        Map<String, Object> response = endpoint.health();

        assertThat(response.get("status")).isEqualTo("STARTING");
    }

    @Test
    void healthEndpoint_degradedState() {
        BootstrapResult degradedResult = BootstrapResult.degraded(
                List.of(PhaseResult.success("BOOT-4 KAT", 10), PhaseResult.failure("BOOT-9 KMS", 5, "timeout")),
                15, "BOOT-9 KMS", "KMS unreachable");
        LclBootstrapRunner runner = createRunner(degradedResult);
        LclHealthEndpoint endpoint = new LclHealthEndpoint(runner);

        Map<String, Object> response = endpoint.health();

        assertThat(response.get("status")).isEqualTo("DEGRADED");
        assertThat(response.get("failedPhase")).isEqualTo("BOOT-9 KMS");
    }

    // === Redaction ===

    @Test
    void redaction_masksSecrets() {
        assertThat(LclHealthEndpoint.redact("key=abc123")).isEqualTo("key=***");
        assertThat(LclHealthEndpoint.redact("password=secret")).isEqualTo("password=***");
        assertThat(LclHealthEndpoint.redact("normal text")).isEqualTo("normal text");
        assertThat(LclHealthEndpoint.redact(null)).isNull();
    }

    // === LclKatEndpoint ===

    @Test
    void katEndpoint_notRun() {
        LclBootstrapRunner runner = createRunner(null);
        LclKatEndpoint endpoint = new LclKatEndpoint(runner, STUB_CMK);

        Map<String, Object> response = endpoint.katResults();

        assertThat(response.get("status")).isEqualTo("NOT_RUN");
    }

    @Test
    void katEndpoint_afterBootstrap() {
        // Run bootstrap first to populate KAT results
        RuntimeProperties props = new RuntimeProperties();
        props.setBootstrapTimeout(Duration.ofSeconds(15));
        LclBootstrapRunner runner = new LclBootstrapRunner(STUB_CMK, NoOpEventBus.INSTANCE, props, null);
        // Trigger KAT manually via the runner's KatRunner
        runner.getKatRunner().check(io.github.emmansun.lightcrypto.core.bootstrap.BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(NoOpEventBus.INSTANCE)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(15))
                .build());

        LclKatEndpoint endpoint = new LclKatEndpoint(runner, STUB_CMK);
        Map<String, Object> response = endpoint.katResults();

        assertThat(response.get("status")).isEqualTo("OK");
        assertThat(response).containsKey("algorithms");
    }
}
