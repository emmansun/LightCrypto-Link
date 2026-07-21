package io.github.emmansun.lightcrypto.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class CompositeEventBusTest {

    @Test
    void multiListenerEmission() {
        List<LclEvent> received1 = new ArrayList<>();
        List<LclEvent> received2 = new ArrayList<>();

        EventBus bus1 = received1::add;
        EventBus bus2 = received2::add;

        CompositeEventBus composite = new CompositeEventBus(List.of(bus1, bus2));

        LclEvent event = LclEvent.builder()
                .event("lcl.crypto.encrypt.completed")
                .tier(EventTier.L2)
                .result("success")
                .build();

        composite.emit(event);

        assertThat(received1).containsExactly(event);
        assertThat(received2).containsExactly(event);
    }

    @Test
    void failureIsolation() {
        AtomicInteger callCount = new AtomicInteger(0);

        EventBus failing = event -> {
            throw new RuntimeException("boom");
        };
        EventBus succeeding = event -> callCount.incrementAndGet();

        CompositeEventBus composite = new CompositeEventBus(List.of(failing, succeeding));

        LclEvent event = LclEvent.builder()
                .event("lcl.test")
                .tier(EventTier.L1)
                .result("success")
                .build();

        // Should not throw
        assertThatCode(() -> composite.emit(event)).doesNotThrowAnyException();

        // Second delegate should still receive the event
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void orderingIsPreserved() {
        List<String> order = new ArrayList<>();

        EventBus first = event -> order.add("first");
        EventBus second = event -> order.add("second");
        EventBus third = event -> order.add("third");

        CompositeEventBus composite = new CompositeEventBus(List.of(first, second, third));

        LclEvent event = LclEvent.builder()
                .event("lcl.test")
                .tier(EventTier.L1)
                .result("success")
                .build();

        composite.emit(event);

        assertThat(order).containsExactly("first", "second", "third");
    }

    @Test
    void emptyDelegatesList() {
        CompositeEventBus composite = new CompositeEventBus(List.of());

        LclEvent event = LclEvent.builder()
                .event("lcl.test")
                .tier(EventTier.L1)
                .result("success")
                .build();

        assertThatCode(() -> composite.emit(event)).doesNotThrowAnyException();
        assertThat(composite.size()).isEqualTo(0);
    }

    @Test
    void sizeReturnsDelegateCount() {
        EventBus noop = NoOpEventBus.INSTANCE;
        CompositeEventBus composite = new CompositeEventBus(List.of(noop, noop, noop));
        assertThat(composite.size()).isEqualTo(3);
    }

    @Test
    void nullDelegatesThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CompositeEventBus(null));
    }
}
