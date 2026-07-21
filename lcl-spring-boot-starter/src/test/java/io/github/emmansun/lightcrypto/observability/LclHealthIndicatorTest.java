package io.github.emmansun.lightcrypto.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LclHealthIndicatorTest {

    @Test
    void allHealthyReportsUp() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> LclHealthStatus.READY);
        checks.put("kms", () -> LclHealthStatus.READY);
        checks.put("vault", () -> LclHealthStatus.READY);

        LclHealthIndicator indicator = new LclHealthIndicator(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("overall", "READY");
        assertThat(health.getDetails()).containsEntry("core", "READY");
        assertThat(health.getDetails()).containsEntry("kms", "READY");
        assertThat(health.getDetails()).containsEntry("vault", "READY");
    }

    @Test
    void degradedComponentReportsOutOfService() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> LclHealthStatus.READY);
        checks.put("kms", () -> LclHealthStatus.DEGRADED);
        checks.put("vault", () -> LclHealthStatus.READY);

        LclHealthIndicator indicator = new LclHealthIndicator(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("overall", "DEGRADED");
    }

    @Test
    void failedComponentReportsDown() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> LclHealthStatus.READY);
        checks.put("kms", () -> LclHealthStatus.FAILED);
        checks.put("vault", () -> LclHealthStatus.READY);

        LclHealthIndicator indicator = new LclHealthIndicator(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("overall", "FAILED");
    }

    @Test
    void startingComponentReportsUnknown() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> LclHealthStatus.STARTING);

        LclHealthIndicator indicator = new LclHealthIndicator(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void exceptionInCheckTreatedAsFailed() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> {
            throw new RuntimeException("boom");
        });

        LclHealthIndicator indicator = new LclHealthIndicator(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("core", "FAILED");
    }

    @Test
    void emptyChecksReportsUp() {
        LclHealthIndicator indicator = new LclHealthIndicator(Map.of());
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void includesSdkVersionInDetails() {
        LclHealthIndicator indicator = new LclHealthIndicator(Map.of());
        Health health = indicator.health();

        assertThat(health.getDetails()).containsKey("sdkVersion");
    }
}
