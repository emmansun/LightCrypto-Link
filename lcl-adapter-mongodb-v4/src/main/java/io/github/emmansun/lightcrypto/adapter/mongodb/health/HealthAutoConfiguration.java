package io.github.emmansun.lightcrypto.adapter.mongodb.health;

import io.github.emmansun.lightcrypto.observability.ComponentHealthCheck;
import io.github.emmansun.lightcrypto.observability.LclHealthStatus;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot 4.x health auto-configuration for Light Crypto Link.
 *
 * <p>Provides SB4-compatible health indicator support using the new
 * {@code org.springframework.boot.health.contributor} package.
 * This replaces the starter's SB3 {@code HealthConfiguration} which
 * uses the old {@code org.springframework.boot.actuate.health} package.
 *
 * <p>Activates only when SB4 health classes are on the classpath and
 * health is enabled ({@code lightcrypto.observability.health.enabled=true}).
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "lightcrypto.observability.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealthAutoConfiguration {

    @Bean
    public LclHealthIndicator lclHealthIndicator(ApplicationContext context) {
        Map<String, ComponentHealthCheck> checks = new LinkedHashMap<>();

        // CoreHealthCheck: EventBus bean exists
        checks.put("core", () -> {
            boolean hasEventBus = context.getBeanNamesForType(
                    io.github.emmansun.lightcrypto.core.event.EventBus.class).length > 0;
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
