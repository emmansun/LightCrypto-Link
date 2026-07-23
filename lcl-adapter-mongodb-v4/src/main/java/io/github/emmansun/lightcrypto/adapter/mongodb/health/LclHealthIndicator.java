package io.github.emmansun.lightcrypto.adapter.mongodb.health;

import io.github.emmansun.lightcrypto.core.SdkVersion;
import io.github.emmansun.lightcrypto.observability.ComponentHealthCheck;
import io.github.emmansun.lightcrypto.observability.LclHealthStatus;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot 4.x health indicator for Light Crypto Link.
 *
 * <p>This is the SB4-compatible variant that imports from
 * {@code org.springframework.boot.health.contributor} (the new SB4 health package).
 *
 * <p>Composes multiple {@link ComponentHealthCheck} instances and reports
 * the worst status as the overall health.
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
