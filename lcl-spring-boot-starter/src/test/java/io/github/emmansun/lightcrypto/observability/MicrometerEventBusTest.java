package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.core.event.EventTier;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerEventBusTest {

    private SimpleMeterRegistry registry;
    private LclMetrics metrics;
    private MicrometerEventBus bus;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new LclMetrics(registry, true);
        bus = new MicrometerEventBus(metrics);
    }

    @Test
    void encryptEventRecordsTimerAndCounter() {
        LclEvent event = LclEvent.builder()
                .event("lcl.crypto.encrypt.completed")
                .tier(EventTier.L2)
                .result("success")
                .algorithm("AES_256_GCM")
                .namespace("app.users#email")
                .durationMicros(240)
                .build();

        bus.emit(event);

        Timer timer = registry.find("lcl.crypto.encrypt.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        Counter counter = registry.find("lcl.crypto.encrypt.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void decryptEventRecordsTimerAndCounter() {
        LclEvent event = LclEvent.builder()
                .event("lcl.crypto.decrypt.completed")
                .tier(EventTier.L2)
                .result("success")
                .algorithm("AES_256_GCM")
                .durationMicros(180)
                .build();

        bus.emit(event);

        Timer timer = registry.find("lcl.crypto.decrypt.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        Counter counter = registry.find("lcl.crypto.decrypt.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void rotationEventRecordsTimerAndCounter() {
        LclEvent event = LclEvent.builder()
                .event("lcl.rotation.execute.completed")
                .tier(EventTier.L2)
                .result("success")
                .namespace("app.users#email")
                .durationMicros(5000)
                .build();

        bus.emit(event);

        Timer timer = registry.find("lcl.rotation.duration").timer();
        assertThat(timer).isNotNull();

        Counter counter = registry.find("lcl.rotation.total").counter();
        assertThat(counter).isNotNull();
    }

    @Test
    void nonMetricEventIsIgnored() {
        LclEvent event = LclEvent.builder()
                .event("lcl.health.check.completed")
                .tier(EventTier.L1)
                .result("success")
                .build();

        bus.emit(event);

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void keyVaultLoadEventRecordsTimer() {
        LclEvent event = LclEvent.builder()
                .event("lcl.keyvault.load.completed")
                .tier(EventTier.L2)
                .result("success")
                .namespace("app.users#email")
                .durationMicros(1200)
                .build();

        bus.emit(event);

        Timer timer = registry.find("lcl.keyvault.load.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
