package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void defaultConfigurationRegistersEventBus() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EventBus.class);
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
            assertThat(context).hasSingleBean(Slf4jEventBus.class);
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
}
