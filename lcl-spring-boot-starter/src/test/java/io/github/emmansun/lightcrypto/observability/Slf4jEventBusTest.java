package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.core.event.EventTier;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class Slf4jEventBusTest {

    private final Slf4jEventBus bus = new Slf4jEventBus();

    @Test
    void emitL2EventDoesNotThrow() {
        LclEvent event = LclEvent.builder()
                .event("lcl.crypto.encrypt.completed")
                .tier(EventTier.L2)
                .result("success")
                .namespace("app.users#email")
                .algorithm("AES_256_GCM")
                .dekVersion(1)
                .durationMicros(240)
                .build();

        assertThatCode(() -> bus.emit(event)).doesNotThrowAnyException();
    }

    @Test
    void emitL1EventDoesNotThrow() {
        LclEvent event = LclEvent.builder()
                .event("lcl.keyvault.cache.evicted")
                .tier(EventTier.L1)
                .result("success")
                .build();

        assertThatCode(() -> bus.emit(event)).doesNotThrowAnyException();
    }

    @Test
    void emitEventWithAttributesDoesNotThrow() {
        LclEvent event = LclEvent.builder()
                .event("lcl.rotation.execute.completed")
                .tier(EventTier.L2)
                .result("success")
                .attribute("kid", "v1-a3b2c1d4")
                .build();

        assertThatCode(() -> bus.emit(event)).doesNotThrowAnyException();
    }

    @Test
    void emitFailureEventDoesNotThrow() {
        LclEvent event = LclEvent.builder()
                .event("lcl.crypto.decrypt.failed")
                .tier(EventTier.L2)
                .result("failure")
                .errorType("TAG_MISMATCH")
                .build();

        assertThatCode(() -> bus.emit(event)).doesNotThrowAnyException();
    }
}
