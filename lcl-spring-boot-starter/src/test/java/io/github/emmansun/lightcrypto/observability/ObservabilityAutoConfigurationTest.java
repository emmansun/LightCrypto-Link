package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.config.ObservabilityProperties;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void defaultConfigurationRegistersEventBus() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("compositeEventBus");
            assertThat(context.getBean("compositeEventBus")).isInstanceOf(EventBus.class);
        });
    }

    @Test
    void disabledObservabilityDoesNotRegisterBeans() {
        contextRunner
                .withPropertyValues("lightcrypto.observability.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EventBus.class);
                    assertThat(context).doesNotHaveBean(Slf4jEventBus.class);
                });
    }

    @Test
    void eventsDisabledDoesNotRegisterSlf4jEventBus() {
        contextRunner
                .withPropertyValues("lightcrypto.observability.events.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(Slf4jEventBus.class);
                });
    }

    @Test
    void eventsEnabledRegistersSlf4jEventBus() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("slf4jEventBus");
            assertThat(context.getBean("slf4jEventBus")).isInstanceOf(Slf4jEventBus.class);
        });
    }

    @Test
    void healthDisabledDoesNotRegisterHealthIndicator() {
        contextRunner
                .withPropertyValues("lightcrypto.observability.health.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LclHealthIndicator.class);
                });
    }

    @Test
    void metricsDisabledDoesNotRegisterMicrometerBeans() {
        contextRunner
                .withPropertyValues("lightcrypto.observability.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MicrometerEventBus.class);
                    assertThat(context).doesNotHaveBean(LclMetrics.class);
                });
    }

    @Test
    void metricsEnabledWithRegistryRegistersMicrometerBeans() {
        contextRunner
                .withUserConfiguration(MeterRegistryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SimpleMeterRegistry.class);
                    assertThat(context).hasSingleBean(LclMetrics.class);
                    assertThat(context).hasSingleBean(MicrometerEventBus.class);
                });
    }

    @Test
    void compositeEventBusFallsBackToNoOpWhenEventsAndMetricsDisabled() {
        contextRunner
                .withPropertyValues(
                        "lightcrypto.observability.events.enabled=false",
                        "lightcrypto.observability.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventBus.class);
                    assertThat(context.getBean(EventBus.class)).isSameAs(NoOpEventBus.INSTANCE);
                });
    }

    @Test
    void compositeEventBusUsesCompositeWhenSlf4jAndMicrometerAreBothPresent() {
        contextRunner
                .withUserConfiguration(MeterRegistryConfig.class)
                .run(context -> {
                    assertThat(context.getBeanNamesForType(EventBus.class))
                            .contains("compositeEventBus", "slf4jEventBus", "micrometerEventBus");
                    assertThat(context.getBean(EventBus.class)).isInstanceOf(io.github.emmansun.lightcrypto.core.event.CompositeEventBus.class);
                });
    }

    @Test
    void healthIndicatorReportsDownWhenKmsMissingAndVaultMissing() {
        contextRunner.run(context -> {
            LclHealthIndicator indicator = context.getBean(LclHealthIndicator.class);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("core", "READY");
            assertThat(health.getDetails()).containsEntry("kms", "FAILED");
            assertThat(health.getDetails()).containsEntry("vault", "DEGRADED");
            assertThat(health.getDetails()).containsEntry("overall", "FAILED");
        });
    }

    @Test
    void healthIndicatorReportsOutOfServiceWhenKmsPresentButVaultMissing() {
        contextRunner
                .withUserConfiguration(CmkProviderConfig.class)
                .run(context -> {
                    LclHealthIndicator indicator = context.getBean(LclHealthIndicator.class);
                    Health health = indicator.health();

                    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
                    assertThat(health.getDetails()).containsEntry("core", "READY");
                    assertThat(health.getDetails()).containsEntry("kms", "READY");
                    assertThat(health.getDetails()).containsEntry("vault", "DEGRADED");
                    assertThat(health.getDetails()).containsEntry("overall", "DEGRADED");
                });
    }

    @Test
    void healthIndicatorReportsUpWhenKmsAndVaultPresent() {
        contextRunner
                .withUserConfiguration(CmkProviderConfig.class, KeyVaultServiceConfig.class)
                .run(context -> {
                    LclHealthIndicator indicator = context.getBean(LclHealthIndicator.class);
                    Health health = indicator.health();

                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                    assertThat(health.getDetails()).containsEntry("core", "READY");
                    assertThat(health.getDetails()).containsEntry("kms", "READY");
                    assertThat(health.getDetails()).containsEntry("vault", "READY");
                    assertThat(health.getDetails()).containsEntry("overall", "READY");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {
        @Bean
        ObservabilityProperties observabilityProperties() {
            return new ObservabilityProperties();
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CmkProviderConfig {
        @Bean
        CmkProvider cmkProvider() {
            return new CmkProvider() {
                @Override
                public String getProviderId() {
                    return "stub";
                }

                @Override
                public String getPublicReference() {
                    return "stub-ref";
                }

                @Override
                public boolean supportsAlgorithm(String lclAlgorithm) {
                    return true;
                }

                @Override
                public String mapAlgorithm(String lclAlgorithm) {
                    return lclAlgorithm;
                }

                @Override
                public WrappedKey wrap(byte[] plaintextKey) {
                    return new WrappedKey(plaintextKey, "STUB");
                }

                @Override
                public byte[] unwrap(WrappedKey wrappedKey) {
                    return wrappedKey.ciphertext();
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class KeyVaultServiceConfig {
        @Bean
        KeyVaultService keyVaultService() {
            return new KeyVaultService(null, null, null);
        }
    }
}
