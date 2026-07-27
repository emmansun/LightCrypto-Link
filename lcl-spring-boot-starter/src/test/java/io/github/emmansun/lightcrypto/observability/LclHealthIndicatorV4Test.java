package io.github.emmansun.lightcrypto.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LclHealthIndicatorV4Test {

    @Test
    void allHealthyReportsUp() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> LclHealthStatus.READY);
        checks.put("kms", () -> LclHealthStatus.READY);
        checks.put("vault", () -> LclHealthStatus.READY);

        LclHealthIndicatorV4 indicator = new LclHealthIndicatorV4(checks);
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

        LclHealthIndicatorV4 indicator = new LclHealthIndicatorV4(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("overall", "DEGRADED");
    }

    @Test
    void failedComponentReportsDown() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> LclHealthStatus.READY);
        checks.put("kms", () -> LclHealthStatus.FAILED);

        LclHealthIndicatorV4 indicator = new LclHealthIndicatorV4(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("overall", "FAILED");
    }

    @Test
    void startingComponentReportsUnknown() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> LclHealthStatus.STARTING);

        LclHealthIndicatorV4 indicator = new LclHealthIndicatorV4(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("overall", "STARTING");
    }

    @Test
    void exceptionInCheckTreatedAsFailed() {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();
        checks.put("core", () -> {
            throw new RuntimeException("boom");
        });

        LclHealthIndicatorV4 indicator = new LclHealthIndicatorV4(checks);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("core", "FAILED");
        assertThat(health.getDetails()).containsEntry("overall", "FAILED");
    }

    @Test
    void emptyChecksReportsUpAndIncludesSdkVersion() {
        LclHealthIndicatorV4 indicator = new LclHealthIndicatorV4(Map.of());
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("sdkVersion");
    }
}
