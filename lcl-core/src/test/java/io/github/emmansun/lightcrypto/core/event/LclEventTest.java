package io.github.emmansun.lightcrypto.core.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class LclEventTest {

    @Test
    void builderWithAllFields() {
        Instant now = Instant.now();
        LclEvent event = LclEvent.builder()
                .event("lcl.crypto.encrypt.completed")
                .tier(EventTier.L2)
                .timestamp(now)
                .durationMicros(240)
                .result("success")
                .namespace("app.users#email")
                .algorithm("AES_256_GCM")
                .dekVersion(3)
                .errorType(null)
                .attribute("key1", "value1")
                .build();

        assertThat(event.event()).isEqualTo("lcl.crypto.encrypt.completed");
        assertThat(event.tier()).isEqualTo(EventTier.L2);
        assertThat(event.timestamp()).isEqualTo(now);
        assertThat(event.durationMicros()).isEqualTo(240);
        assertThat(event.result()).isEqualTo("success");
        assertThat(event.namespace()).isEqualTo("app.users#email");
        assertThat(event.algorithm()).isEqualTo("AES_256_GCM");
        assertThat(event.dekVersion()).isEqualTo(3);
        assertThat(event.errorType()).isNull();
        assertThat(event.attributes()).containsEntry("key1", "value1");
    }

    @Test
    void builderDefaults() {
        LclEvent event = LclEvent.builder()
                .event("lcl.keyvault.load.completed")
                .tier(EventTier.L2)
                .result("success")
                .build();

        assertThat(event.durationMicros()).isEqualTo(-1);
        assertThat(event.dekVersion()).isEqualTo(-1);
        assertThat(event.namespace()).isNull();
        assertThat(event.algorithm()).isNull();
        assertThat(event.errorType()).isNull();
        assertThat(event.attributes()).isEmpty();
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void builderRequiresEvent() {
        assertThatNullPointerException()
                .isThrownBy(() -> LclEvent.builder()
                        .tier(EventTier.L1)
                        .result("success")
                        .build())
                .withMessageContaining("event");
    }

    @Test
    void builderRequiresTier() {
        assertThatNullPointerException()
                .isThrownBy(() -> LclEvent.builder()
                        .event("lcl.test")
                        .result("success")
                        .build())
                .withMessageContaining("tier");
    }

    @Test
    void builderRequiresResult() {
        assertThatNullPointerException()
                .isThrownBy(() -> LclEvent.builder()
                        .event("lcl.test")
                        .tier(EventTier.L1)
                        .build())
                .withMessageContaining("result");
    }

    @Test
    void attributesAreImmutable() {
        LclEvent event = LclEvent.builder()
                .event("lcl.test")
                .tier(EventTier.L1)
                .result("success")
                .attribute("k", "v")
                .build();

        assertThatThrownBy(() -> event.attributes().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void attributesMapCopyIsDefensive() {
        Map<String, String> mutable = new java.util.HashMap<>();
        mutable.put("a", "1");

        LclEvent event = LclEvent.builder()
                .event("lcl.test")
                .tier(EventTier.L1)
                .result("success")
                .attributes(mutable)
                .build();

        mutable.put("b", "2");
        assertThat(event.attributes()).hasSize(1);
    }

    @Test
    void failureEventWithErrorType() {
        LclEvent event = LclEvent.builder()
                .event("lcl.crypto.decrypt.failed")
                .tier(EventTier.L2)
                .result("failure")
                .errorType("TAG_MISMATCH")
                .build();

        assertThat(event.result()).isEqualTo("failure");
        assertThat(event.errorType()).isEqualTo("TAG_MISMATCH");
    }

    @Test
    void toStringContainsEssentialInfo() {
        LclEvent event = LclEvent.builder()
                .event("lcl.rotation.execute.completed")
                .tier(EventTier.L2)
                .result("success")
                .build();

        assertThat(event.toString()).contains("lcl.rotation.execute.completed");
        assertThat(event.toString()).contains("L2");
        assertThat(event.toString()).contains("success");
    }
}
