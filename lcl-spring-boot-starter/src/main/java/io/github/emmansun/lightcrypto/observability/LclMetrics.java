package io.github.emmansun.lightcrypto.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Centralized Micrometer meter definitions for LCL observability.
 * <p>
 * Manages Timer, Counter, and Gauge registrations for core cryptographic operations.
 *
 * @since 1.0.0
 */
public class LclMetrics {

    private final MeterRegistry registry;
    private final boolean publishPercentiles;

    public LclMetrics(MeterRegistry registry, boolean publishPercentiles) {
        this.registry = registry;
        this.publishPercentiles = publishPercentiles;
    }

    /**
     * Record a crypto encrypt operation duration.
     */
    public void recordEncryptDuration(String algorithm, String namespace, long durationMicros) {
        Timer timer = Timer.builder("lcl.crypto.encrypt.duration")
                .tag("algorithm", algorithm != null ? algorithm : "unknown")
                .tag("namespace", namespace != null ? namespace : "unknown")
                .publishPercentiles(publishPercentiles ? new double[]{0.5, 0.95, 0.99} : new double[]{})
                .register(registry);
        timer.record(durationMicros, TimeUnit.MICROSECONDS);
    }

    /**
     * Record a crypto decrypt operation duration.
     */
    public void recordDecryptDuration(String algorithm, String namespace, long durationMicros) {
        Timer timer = Timer.builder("lcl.crypto.decrypt.duration")
                .tag("algorithm", algorithm != null ? algorithm : "unknown")
                .tag("namespace", namespace != null ? namespace : "unknown")
                .publishPercentiles(publishPercentiles ? new double[]{0.5, 0.95, 0.99} : new double[]{})
                .register(registry);
        timer.record(durationMicros, TimeUnit.MICROSECONDS);
    }

    /**
     * Record a blind index compute operation duration.
     */
    public void recordBlindIndexDuration(String namespace, long durationMicros) {
        Timer timer = Timer.builder("lcl.blind_index.compute.duration")
                .tag("namespace", namespace != null ? namespace : "unknown")
                .publishPercentiles(publishPercentiles ? new double[]{0.5, 0.95, 0.99} : new double[]{})
                .register(registry);
        timer.record(durationMicros, TimeUnit.MICROSECONDS);
    }

    /**
     * Record a key vault load operation duration.
     */
    public void recordKeyVaultLoadDuration(String namespace, long durationMicros) {
        Timer timer = Timer.builder("lcl.keyvault.load.duration")
                .tag("namespace", namespace != null ? namespace : "unknown")
                .publishPercentiles(publishPercentiles ? new double[]{0.5, 0.95, 0.99} : new double[]{})
                .register(registry);
        timer.record(durationMicros, TimeUnit.MICROSECONDS);
    }

    /**
     * Record a DEK rotation operation duration.
     */
    public void recordRotationDuration(String namespace, long durationMicros) {
        Timer timer = Timer.builder("lcl.rotation.duration")
                .tag("namespace", namespace != null ? namespace : "unknown")
                .publishPercentiles(publishPercentiles ? new double[]{0.5, 0.95, 0.99} : new double[]{})
                .register(registry);
        timer.record(durationMicros, TimeUnit.MICROSECONDS);
    }

    /**
     * Increment the encrypt counter.
     */
    public void incrementEncryptCount(String algorithm, String result) {
        Counter.builder("lcl.crypto.encrypt.total")
                .tag("algorithm", algorithm != null ? algorithm : "unknown")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Increment the decrypt counter.
     */
    public void incrementDecryptCount(String algorithm, String result) {
        Counter.builder("lcl.crypto.decrypt.total")
                .tag("algorithm", algorithm != null ? algorithm : "unknown")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Increment the rotation counter.
     */
    public void incrementRotationCount(String result) {
        Counter.builder("lcl.rotation.total")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Get the underlying MeterRegistry.
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
