package io.github.emmansun.lightcrypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for LCL observability subsystem.
 * <p>
 * Prefix: {@code lightcrypto.observability}
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "lightcrypto.observability")
public class ObservabilityProperties {

    /** Master switch for all observability features. Default: true. */
    private boolean enabled = true;

    private final Events events = new Events();
    private final Metrics metrics = new Metrics();
    private final Health health = new Health();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Events getEvents() {
        return events;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Health getHealth() {
        return health;
    }

    /**
     * EventBus / structured event configuration.
     */
    public static class Events {
        /** Enable EventBus (Slf4jEventBus). Default: true. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Micrometer metrics configuration.
     */
    public static class Metrics {
        /** Enable Micrometer metrics registration. Default: true. */
        private boolean enabled = true;

        /** Publish Timer percentiles (p50, p95, p99). Default: true. */
        private boolean publishPercentiles = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPublishPercentiles() {
            return publishPercentiles;
        }

        public void setPublishPercentiles(boolean publishPercentiles) {
            this.publishPercentiles = publishPercentiles;
        }
    }

    /**
     * Health indicator configuration.
     */
    public static class Health {
        /** Enable LclHealthIndicator. Default: true. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
