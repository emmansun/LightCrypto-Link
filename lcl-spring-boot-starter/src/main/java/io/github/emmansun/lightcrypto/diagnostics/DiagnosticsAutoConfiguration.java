package io.github.emmansun.lightcrypto.diagnostics;

import io.github.emmansun.lightcrypto.config.RuntimeProperties;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapResult;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.observability.ComponentHealthCheck;
import io.github.emmansun.lightcrypto.observability.LclHealthStatus;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for LCL bootstrap diagnostics.
 * <p>
 * Activated by {@code lightcrypto.runtime.bootstrap-enabled=true} (default).
 * Registers the {@link LclBootstrapRunner} and wires bootstrap status into the health indicator.
 *
 * @since 1.0.0
 */
@AutoConfiguration(after = io.github.emmansun.lightcrypto.observability.ObservabilityAutoConfiguration.class)
@ConditionalOnProperty(prefix = "lightcrypto.runtime", name = "bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class DiagnosticsAutoConfiguration {

    @Bean
    @ConditionalOnBean(CmkProvider.class)
    public LclBootstrapRunner lclBootstrapRunner(
            CmkProvider cmkProvider,
            EventBus eventBus,
            RuntimeProperties runtimeProperties,
            ObjectProvider<VaultStore> vaultStoreProvider) {
        VaultStore vaultStore = vaultStoreProvider.getIfAvailable();
        return new LclBootstrapRunner(cmkProvider, eventBus, runtimeProperties, vaultStore);
    }

    /**
     * Wires bootstrap result into the health indicator as a "bootstrap" component check.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(LclBootstrapRunner.class)
    static class BootstrapHealthConfiguration {

        @Bean
        public ComponentHealthCheck bootstrapHealthCheck(LclBootstrapRunner bootstrapRunner) {
            return () -> {
                BootstrapResult result = bootstrapRunner.getLastResult();
                if (result == null) {
                    return LclHealthStatus.STARTING;
                }
                return switch (result.status()) {
                    case READY -> LclHealthStatus.READY;
                    case DEGRADED -> LclHealthStatus.DEGRADED;
                    case FAILED -> LclHealthStatus.FAILED;
                };
            };
        }
    }

    /**
     * Actuator endpoints — only active when spring-boot-actuator is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Endpoint.class)
    @ConditionalOnBean(LclBootstrapRunner.class)
    @ConditionalOnProperty(prefix = "lightcrypto.observability.health", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class DiagnosticsEndpointsConfiguration {

        @Bean
        public LclHealthEndpoint lclHealthEndpoint(LclBootstrapRunner bootstrapRunner) {
            return new LclHealthEndpoint(bootstrapRunner);
        }

        @Bean
        public LclKatEndpoint lclKatEndpoint(LclBootstrapRunner bootstrapRunner, CmkProvider cmkProvider) {
            return new LclKatEndpoint(bootstrapRunner, cmkProvider);
        }
    }
}
