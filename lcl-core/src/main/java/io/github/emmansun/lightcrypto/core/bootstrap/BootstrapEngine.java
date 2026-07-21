package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.EventTier;
import io.github.emmansun.lightcrypto.core.event.LclEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Sequential bootstrap engine that executes registered phases with failure
 * classification, timeout enforcement, retry logic, and EventBus event emission.
 *
 * <p>Execution semantics:
 * <ul>
 *   <li>Phases execute in registration order</li>
 *   <li>FATAL failure aborts all subsequent phases</li>
 *   <li>RECOVERABLE failure retries up to 3 times with exponential backoff</li>
 *   <li>ADVISORY failure logs a warning and continues</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class BootstrapEngine {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 100;

    private final List<BootstrapPhase> phases;

    public BootstrapEngine(List<BootstrapPhase> phases) {
        Objects.requireNonNull(phases, "phases must not be null");
        this.phases = Collections.unmodifiableList(new ArrayList<>(phases));
    }

    /**
     * Runs all registered bootstrap phases sequentially.
     *
     * @param context the bootstrap context
     * @return the bootstrap result
     * @throws BootstrapTimeoutException if total execution exceeds the configured timeout
     */
    public BootstrapResult run(BootstrapContext context) {
        EventBus eventBus = context.eventBus();
        long startTime = System.nanoTime();
        long timeoutNanos = context.bootstrapTimeout().toNanos();

        emitEvent(eventBus, "lcl.bootstrap.started", "started", null);

        List<PhaseResult> phaseResults = new ArrayList<>();
        boolean degraded = false;
        String degradedPhase = null;
        String degradedError = null;

        for (BootstrapPhase phase : phases) {
            long elapsed = System.nanoTime() - startTime;
            if (elapsed >= timeoutNanos) {
                emitEvent(eventBus, "lcl.bootstrap.timeout", "timeout", phase.name());
                throw new BootstrapTimeoutException(
                        "Bootstrap exceeded timeout of " + context.bootstrapTimeout().toMillis() + "ms at phase: " + phase.name());
            }

            String phaseEventName = toPhaseEventName(phase.name());
            emitEvent(eventBus, "lcl.bootstrap." + phaseEventName + ".started", "started", phase.name());

            long phaseStart = System.nanoTime();
            PhaseResult result = executePhase(phase, context);
            long phaseDurationMs = (System.nanoTime() - phaseStart) / 1_000_000;

            if (result.success()) {
                phaseResults.add(result);
                emitEvent(eventBus, "lcl.bootstrap." + phaseEventName + ".completed", "success", phase.name());
            } else {
                // Handle failure based on classification
                switch (phase.failureClass()) {
                    case FATAL -> {
                        phaseResults.add(result);
                        emitEvent(eventBus, "lcl.bootstrap." + phaseEventName + ".failed", "failed", phase.name());
                        long totalMs = (System.nanoTime() - startTime) / 1_000_000;
                        return BootstrapResult.failed(phaseResults, totalMs, phase.name(), result.errorMessage());
                    }
                    case RECOVERABLE -> {
                        // Retry logic
                        PhaseResult retryResult = retryPhase(phase, context, phaseEventName, eventBus);
                        phaseResults.add(retryResult);
                        if (!retryResult.success()) {
                            if (context.strictMode()) {
                                emitEvent(eventBus, "lcl.bootstrap." + phaseEventName + ".failed", "failed", phase.name());
                                long totalMs = (System.nanoTime() - startTime) / 1_000_000;
                                return BootstrapResult.failed(phaseResults, totalMs, phase.name(), retryResult.errorMessage());
                            } else {
                                // Downgrade to advisory in tolerant mode
                                degraded = true;
                                degradedPhase = phase.name();
                                degradedError = retryResult.errorMessage();
                                emitEvent(eventBus, "lcl.bootstrap." + phaseEventName + ".degraded", "degraded", phase.name());
                            }
                        } else {
                            emitEvent(eventBus, "lcl.bootstrap." + phaseEventName + ".completed", "success", phase.name());
                        }
                    }
                    case ADVISORY -> {
                        phaseResults.add(result);
                        emitEvent(eventBus, "lcl.bootstrap." + phaseEventName + ".advisory", "advisory", phase.name());
                    }
                }
            }
        }

        long totalMs = (System.nanoTime() - startTime) / 1_000_000;
        emitEvent(eventBus, "lcl.bootstrap.ready", "ready", null);

        if (degraded) {
            return BootstrapResult.degraded(phaseResults, totalMs, degradedPhase, degradedError);
        }
        return BootstrapResult.ready(phaseResults, totalMs);
    }

    private PhaseResult executePhase(BootstrapPhase phase, BootstrapContext context) {
        long start = System.nanoTime();
        try {
            PhaseResult result = phase.check().check(context);
            return result;
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return PhaseResult.failure(phase.name(), durationMs, e.getMessage());
        }
    }

    private PhaseResult retryPhase(BootstrapPhase phase, BootstrapContext context,
                                   String phaseEventName, EventBus eventBus) {
        PhaseResult lastResult = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            long phaseStart = System.nanoTime();
            lastResult = executePhase(phase, context);
            if (lastResult.success()) {
                return lastResult;
            }
            // Exponential backoff before next retry
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(BASE_BACKOFF_MS * (1L << (attempt - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    long durationMs = (System.nanoTime() - phaseStart) / 1_000_000;
                    return PhaseResult.failure(phase.name(), durationMs, "Interrupted during retry backoff");
                }
            }
        }
        return lastResult;
    }

    private void emitEvent(EventBus eventBus, String eventName, String result, String phase) {
        LclEvent.Builder builder = LclEvent.builder()
                .event(eventName)
                .tier(EventTier.L2)
                .result(result);
        if (phase != null) {
            builder.attribute("phase", phase);
        }
        eventBus.emit(builder.build());
    }

    /**
     * Converts a phase name like "BOOT-4 KAT" to an event-friendly name like "kat".
     */
    private static String toPhaseEventName(String phaseName) {
        // Extract the last word, lowercase
        String[] parts = phaseName.split("\\s+");
        return parts[parts.length - 1].toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
