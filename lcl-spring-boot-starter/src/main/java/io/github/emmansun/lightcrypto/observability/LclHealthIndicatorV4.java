package io.github.emmansun.lightcrypto.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import java.util.Map;

/**
 * Spring Boot 4.x health indicator for Light Crypto Link.
 * <p>
 * This is a thin shell that delegates to {@link LclHealthCollector} for
 * the actual health composition logic.
 * <p>
 * Implements the SB4 {@code org.springframework.boot.health.contributor.HealthIndicator}
 * interface (health was split from actuator in Spring Boot 4.x).
 * <p>
 * Status mapping: READY→UP, DEGRADED→OUT_OF_SERVICE, FAILED→DOWN, STARTING→UNKNOWN.
 *
 * @since 1.0.0
 */
public class LclHealthIndicatorV4 implements HealthIndicator {

    private final LclHealthCollector collector;

    public LclHealthIndicatorV4(Map<String, ComponentHealthCheck> checks) {
        this.collector = new LclHealthCollector(checks);
    }

    @Override
    public Health health() {
        LclHealthCollector.HealthResult result = collector.collect();

        Status springStatus = mapToSpringStatus(result.overall());
        Health.Builder builder = new Health.Builder(springStatus);
        result.details().forEach(builder::withDetail);
        return builder.build();
    }

    private Status mapToSpringStatus(LclHealthStatus status) {
        return switch (status) {
            case READY -> Status.UP;
            case DEGRADED -> Status.OUT_OF_SERVICE;
            case FAILED -> Status.DOWN;
            case STARTING -> Status.UNKNOWN;
        };
    }
}
