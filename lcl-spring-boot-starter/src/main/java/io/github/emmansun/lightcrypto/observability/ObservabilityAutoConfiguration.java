package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.config.ObservabilityProperties;
import io.github.emmansun.lightcrypto.core.event.CompositeEventBus;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for LCL observability: EventBus, Metrics, and Health Indicator.
 * <p>
 * Activated by {@code lightcrypto.observability.enabled=true} (default).
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "lightcrypto.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfiguration {

    // ===== Events (Slf4jEventBus) =====

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "lightcrypto.observability.events", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class EventsConfiguration {

        @Bean
        public Slf4jEventBus slf4jEventBus() {
            return new Slf4jEventBus();
        }
    }

    // ===== Metrics (Micrometer) =====

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "lightcrypto.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MetricsConfiguration {

        @Bean
        public LclMetrics lclMetrics(MeterRegistry meterRegistry, ObservabilityProperties properties) {
            return new LclMetrics(meterRegistry, properties.getMetrics().isPublishPercentiles());
        }

        @Bean
        public MicrometerEventBus micrometerEventBus(LclMetrics lclMetrics) {
            return new MicrometerEventBus(lclMetrics);
        }
    }

    // ===== Composite EventBus (combines Slf4j + Micrometer) =====

    @Configuration(proxyBeanMethods = false)
    static class CompositeEventBusConfiguration {

        @Bean
        @Primary
        public EventBus compositeEventBus(ApplicationContext context) {
            List<EventBus> delegates = new ArrayList<>();

            // Add Slf4jEventBus if present
            if (context.getBeanNamesForType(Slf4jEventBus.class).length > 0) {
                delegates.add(context.getBean(Slf4jEventBus.class));
            }

            // Add MicrometerEventBus if present
            if (context.getBeanNamesForType(MicrometerEventBus.class).length > 0) {
                delegates.add(context.getBean(MicrometerEventBus.class));
            }

            if (delegates.isEmpty()) {
                return NoOpEventBus.INSTANCE;
            }
            if (delegates.size() == 1) {
                return delegates.get(0);
            }
            return new CompositeEventBus(delegates);
        }
    }

    // ===== Health Indicator =====

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "lightcrypto.observability.health", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class HealthConfiguration {

        @Bean
        public LclHealthIndicator lclHealthIndicator(ApplicationContext context) {
            Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();

            // CoreHealthCheck: EventBus bean exists
            checks.put("core", () -> {
                boolean hasEventBus = context.getBeanNamesForType(EventBus.class).length > 0;
                return hasEventBus ? LclHealthStatus.READY : LclHealthStatus.FAILED;
            });

            // KmsHealthCheck: at least one CmkProvider bean
            checks.put("kms", () -> {
                boolean hasCmkProvider = context.getBeanNamesForType(CmkProvider.class).length > 0;
                return hasCmkProvider ? LclHealthStatus.READY : LclHealthStatus.FAILED;
            });

            // VaultHealthCheck: KeyVaultService is initialized
            checks.put("vault", () -> {
                try {
                    context.getBean(KeyVaultService.class);
                    return LclHealthStatus.READY;
                } catch (Exception e) {
                    return LclHealthStatus.DEGRADED;
                }
            });

            return new LclHealthIndicator(checks);
        }
    }
}
