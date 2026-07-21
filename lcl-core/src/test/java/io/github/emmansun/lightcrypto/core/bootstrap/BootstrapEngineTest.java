package io.github.emmansun.lightcrypto.core.bootstrap;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapEngineTest {

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
    void allPhasesPass_returnsReady() {
        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("phase-1", ctx -> PhaseResult.success("phase-1", 1), FailureClass.FATAL),
                new BootstrapPhase("phase-2", ctx -> PhaseResult.success("phase-2", 2), FailureClass.RECOVERABLE)
        );
        BootstrapEngine engine = new BootstrapEngine(phases);

        BootstrapResult result = engine.run(defaultContext());

        assertThat(result.status()).isEqualTo(BootstrapResult.Status.READY);
        assertThat(result.phaseResults()).hasSize(2);
        assertThat(result.isReady()).isTrue();
    }

    @Test
    void fatalFailure_abortsSubsequentPhases() {
        AtomicInteger executed = new AtomicInteger(0);
        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("phase-1", ctx -> {
                    executed.incrementAndGet();
                    return PhaseResult.failure("phase-1", 1, "fatal error");
                }, FailureClass.FATAL),
                new BootstrapPhase("phase-2", ctx -> {
                    executed.incrementAndGet();
                    return PhaseResult.success("phase-2", 1);
                }, FailureClass.FATAL)
        );
        BootstrapEngine engine = new BootstrapEngine(phases);

        BootstrapResult result = engine.run(defaultContext());

        assertThat(result.status()).isEqualTo(BootstrapResult.Status.FAILED);
        assertThat(result.failedPhase()).isEqualTo("phase-1");
        assertThat(executed.get()).isEqualTo(1);
    }

    @Test
    void advisoryFailure_continuesExecution() {
        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("phase-1", ctx -> PhaseResult.failure("phase-1", 1, "warning"), FailureClass.ADVISORY),
                new BootstrapPhase("phase-2", ctx -> PhaseResult.success("phase-2", 1), FailureClass.FATAL)
        );
        BootstrapEngine engine = new BootstrapEngine(phases);

        BootstrapResult result = engine.run(defaultContext());

        assertThat(result.status()).isEqualTo(BootstrapResult.Status.READY);
        assertThat(result.phaseResults()).hasSize(2);
    }

    @Test
    void timeout_throwsBootstrapTimeoutException() {
        // Use a very short timeout; first phase sleeps to exceed it
        BootstrapContext ctx = BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(NoOpEventBus.INSTANCE)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofMillis(50))
                .build();

        List<BootstrapPhase> twoPhases = List.of(
                new BootstrapPhase("slow-phase", c -> {
                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return PhaseResult.success("slow-phase", 100);
                }, FailureClass.FATAL),
                new BootstrapPhase("next-phase", c -> PhaseResult.success("next-phase", 0), FailureClass.FATAL)
        );
        BootstrapEngine engine = new BootstrapEngine(twoPhases);

        assertThatThrownBy(() -> engine.run(ctx))
                .isInstanceOf(BootstrapTimeoutException.class);
    }

    @Test
    void recoverableRetry_strictMode_failsAfterRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("flaky", ctx -> {
                    attempts.incrementAndGet();
                    return PhaseResult.failure("flaky", 1, "connection refused");
                }, FailureClass.RECOVERABLE)
        );
        BootstrapEngine engine = new BootstrapEngine(phases);

        BootstrapResult result = engine.run(defaultContext());

        assertThat(result.status()).isEqualTo(BootstrapResult.Status.FAILED);
        assertThat(attempts.get()).isEqualTo(4); // 1 initial + 3 retries
    }

    @Test
    void recoverableRetry_tolerantMode_degradesAfterRetries() {
        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("flaky", ctx -> PhaseResult.failure("flaky", 1, "timeout"), FailureClass.RECOVERABLE),
                new BootstrapPhase("next", ctx -> PhaseResult.success("next", 1), FailureClass.FATAL)
        );
        BootstrapEngine engine = new BootstrapEngine(phases);

        BootstrapContext tolerantCtx = BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(NoOpEventBus.INSTANCE)
                .strictMode(false)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(15))
                .build();

        BootstrapResult result = engine.run(tolerantCtx);

        assertThat(result.status()).isEqualTo(BootstrapResult.Status.DEGRADED);
        assertThat(result.failedPhase()).isEqualTo("flaky");
    }

    @Test
    void eventsEmitted_duringExecution() {
        List<String> events = new ArrayList<>();
        EventBus collectingBus = event -> events.add(event.event());

        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("BOOT-4 KAT", ctx -> PhaseResult.success("BOOT-4 KAT", 1), FailureClass.FATAL)
        );
        BootstrapEngine engine = new BootstrapEngine(phases);

        BootstrapContext ctx = BootstrapContext.builder()
                .cmkProvider(STUB_CMK)
                .eventBus(collectingBus)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(15))
                .build();

        engine.run(ctx);

        assertThat(events).contains(
                "lcl.bootstrap.started",
                "lcl.bootstrap.kat.started",
                "lcl.bootstrap.kat.completed",
                "lcl.bootstrap.ready"
        );
    }
}
