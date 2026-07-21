package io.github.emmansun.lightcrypto.diagnostics;

import io.github.emmansun.lightcrypto.config.RuntimeProperties;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapContext;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapEngine;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapPhase;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapResult;
import io.github.emmansun.lightcrypto.core.bootstrap.CanaryRunner;
import io.github.emmansun.lightcrypto.core.bootstrap.ConfigValidationCheck;
import io.github.emmansun.lightcrypto.core.bootstrap.FailureClass;
import io.github.emmansun.lightcrypto.core.bootstrap.KatRunner;
import io.github.emmansun.lightcrypto.core.bootstrap.SpiVersionCheck;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full bootstrap diagnostics sequence.
 */
class BootstrapIntegrationTest {

    private static final CmkProvider STUB_CMK = new CmkProvider() {
        @Override public String getProviderId() { return "stub"; }
        @Override public String getPublicReference() { return "stub-ref"; }
        @Override public boolean supportsAlgorithm(String a) { return true; }
        @Override public String mapAlgorithm(String a) { return a; }
        @Override public WrappedKey wrap(byte[] key) { return new WrappedKey(key, "STUB"); }
        @Override public byte[] unwrap(WrappedKey w) { return w.ciphertext(); }
    };

    @Test
    void fullBootstrapSequence_allPhasesPass_eventsEmitted() {
        List<String> events = new ArrayList<>();
        EventBus collectingBus = event -> events.add(event.event());

        BootstrapContext context = BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(collectingBus)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(15))
                .build();

        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("BOOT-1 Config", new ConfigValidationCheck(), FailureClass.FATAL),
                new BootstrapPhase("BOOT-2 SPI", new SpiVersionCheck(), FailureClass.FATAL),
                new BootstrapPhase("BOOT-4 KAT", new KatRunner(), FailureClass.FATAL),
                new BootstrapPhase("BOOT-10 Canary", new CanaryRunner(), FailureClass.FATAL)
        );

        BootstrapEngine engine = new BootstrapEngine(phases);
        BootstrapResult result = engine.run(context);

        assertThat(result.isReady()).isTrue();
        assertThat(result.phaseResults()).hasSize(4);

        // Verify event sequence
        assertThat(events).contains(
                "lcl.bootstrap.started",
                "lcl.bootstrap.config.started",
                "lcl.bootstrap.config.completed",
                "lcl.bootstrap.spi.started",
                "lcl.bootstrap.spi.completed",
                "lcl.bootstrap.kat.started",
                "lcl.bootstrap.kat.completed",
                "lcl.bootstrap.canary.started",
                "lcl.bootstrap.canary.completed",
                "lcl.bootstrap.ready"
        );
    }

    @Test
    void katFailure_abortsBootstrap_fatalStatus() {
        BootstrapContext context = BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(NoOpEventBus.INSTANCE)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(15))
                .build();

        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("BOOT-1 Config", new ConfigValidationCheck(), FailureClass.FATAL),
                new BootstrapPhase("BOOT-4 KAT-fail", ctx ->
                        io.github.emmansun.lightcrypto.core.bootstrap.PhaseResult.failure("BOOT-4 KAT-fail", 1, "KAT failed"),
                        FailureClass.FATAL),
                new BootstrapPhase("BOOT-10 Canary", new CanaryRunner(), FailureClass.FATAL)
        );

        BootstrapEngine engine = new BootstrapEngine(phases);
        BootstrapResult result = engine.run(context);

        assertThat(result.status()).isEqualTo(BootstrapResult.Status.FAILED);
        assertThat(result.failedPhase()).isEqualTo("BOOT-4 KAT-fail");
        assertThat(result.phaseResults()).hasSize(2); // Config + KAT-fail (canary not executed)
    }

    @Test
    void canaryRoundtrip_duringBootstrap_successEvent() {
        List<String> events = new ArrayList<>();
        EventBus collectingBus = event -> events.add(event.event());

        BootstrapContext context = BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(collectingBus)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(15))
                .build();

        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("BOOT-10 Canary", new CanaryRunner(), FailureClass.FATAL)
        );

        BootstrapEngine engine = new BootstrapEngine(phases);
        BootstrapResult result = engine.run(context);

        assertThat(result.isReady()).isTrue();
        assertThat(events).contains("lcl.bootstrap.canary.started", "lcl.bootstrap.canary.completed");
    }

    @Test
    void actuatorEndpoints_returnCorrectJson() {
        // Simulate bootstrap completion
        BootstrapResult readyResult = BootstrapResult.ready(
                List.of(
                        io.github.emmansun.lightcrypto.core.bootstrap.PhaseResult.success("BOOT-4 KAT", 10),
                        io.github.emmansun.lightcrypto.core.bootstrap.PhaseResult.success("BOOT-9 KMS", 5)
                ), 15);

        RuntimeProperties props = new RuntimeProperties();
        props.setBootstrapTimeout(Duration.ofSeconds(15));
        LclBootstrapRunner runner = new LclBootstrapRunner(STUB_CMK, NoOpEventBus.INSTANCE, props, null) {
            @Override
            public BootstrapResult getLastResult() {
                return readyResult;
            }
        };

        LclHealthEndpoint healthEndpoint = new LclHealthEndpoint(runner);
        Map<String, Object> healthResponse = healthEndpoint.health();
        assertThat(healthResponse.get("status")).isEqualTo("READY");
        assertThat(healthResponse).containsKey("components");
        assertThat(healthResponse).containsKey("sdkVersion");

        LclKatEndpoint katEndpoint = new LclKatEndpoint(runner, STUB_CMK);
        Map<String, Object> katResponse = katEndpoint.katResults();
        // KAT not run via runner (overridden), so NOT_RUN
        assertThat(katResponse.get("status")).isEqualTo("NOT_RUN");
    }
}
