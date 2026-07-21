package io.github.emmansun.lightcrypto.core.event;

/**
 * Event bus SPI for emitting structured LCL events.
 * <p>
 * This is a functional interface with zero framework dependencies (only JDK types).
 * Implementations must not throw exceptions to the caller.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface EventBus {

    /**
     * Emit an LCL event. Implementations SHALL process the event without
     * throwing exceptions to the caller.
     *
     * @param event the event to emit
     */
    void emit(LclEvent event);
}
