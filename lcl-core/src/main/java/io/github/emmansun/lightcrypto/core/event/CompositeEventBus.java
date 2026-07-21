package io.github.emmansun.lightcrypto.core.event;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Composite EventBus that delegates to multiple EventBus implementations in order.
 * <p>
 * If any delegate throws an exception, the exception is caught and logged,
 * and remaining delegates still receive the event (failure isolation).
 *
 * @since 1.0.0
 */
public final class CompositeEventBus implements EventBus {

    private static final Logger LOG = Logger.getLogger(CompositeEventBus.class.getName());

    private final List<EventBus> delegates;

    public CompositeEventBus(List<EventBus> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates must not be null"));
    }

    @Override
    public void emit(LclEvent event) {
        for (EventBus delegate : delegates) {
            try {
                delegate.emit(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "EventBus delegate failed for event: " + event.event(), e);
            }
        }
    }

    /**
     * Returns the number of registered delegates.
     */
    public int size() {
        return delegates.size();
    }
}
