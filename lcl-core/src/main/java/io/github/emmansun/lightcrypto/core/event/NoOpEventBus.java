package io.github.emmansun.lightcrypto.core.event;

/**
 * No-operation EventBus singleton — silently discards all events with zero overhead.
 * <p>
 * Used as the default when no EventBus implementation is configured.
 *
 * @since 1.0.0
 */
public final class NoOpEventBus implements EventBus {

    /** Singleton instance. */
    public static final NoOpEventBus INSTANCE = new NoOpEventBus();

    private NoOpEventBus() {
    }

    @Override
    public void emit(LclEvent event) {
        // intentionally empty — zero overhead
    }
}
