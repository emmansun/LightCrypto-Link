package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.core.SdkVersion;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot Actuator {@link HealthIndicator} for Light Crypto Link.
 * <p>
 * Composes multiple {@link ComponentHealthCheck} instances and reports
 * the worst status as the overall health.
 * <p>
 * Status mapping: READY→UP, DEGRADED→OUT_OF_SERVICE, FAILED→DOWN, STARTING→UNKNOWN.
 *
 * @since 1.0.0
 */
public class LclHealthIndicator implements HealthIndicator {

    private final Map<String, ComponentHealthCheck> checks;

    public LclHealthIndicator(Map<String, ComponentHealthCheck> checks) {
        this.checks = checks != null ? checks : Map.of();
    }

    @Override
    public Health health() {
        LclHealthStatus overall = LclHealthStatus.READY;
        Map<String, String> componentDetails = new LinkedHashMap<>();

        for (Map.Entry<String, ComponentHealthCheck> entry : checks.entrySet()) {
            LclHealthStatus status;
            try {
                status = entry.getValue().check();
            } catch (Exception e) {
                status = LclHealthStatus.FAILED;
            }
            componentDetails.put(entry.getKey(), status.name());
            overall = LclHealthStatus.worst(overall, status);
        }

        Status springStatus = mapToSpringStatus(overall);
        Health.Builder builder = new Health.Builder(springStatus);
        componentDetails.forEach(builder::withDetail);
        builder.withDetail("overall", overall.name());
        builder.withDetail("sdkVersion", SdkVersion.getVersion());
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
