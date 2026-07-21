package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.LclEvent;

/**
 * EventBus implementation that routes metric-relevant events to {@link LclMetrics}.
 * <p>
 * Listens for events matching patterns: {@code lcl.crypto.*}, {@code lcl.rotation.*},
 * {@code lcl.keyvault.*}, {@code lcl.blind_index.*}.
 * Non-metric events are silently ignored.
 *
 * @since 1.0.0
 */
public class MicrometerEventBus implements EventBus {

    private final LclMetrics metrics;

    public MicrometerEventBus(LclMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void emit(LclEvent event) {
        String name = event.event();
        if (name == null) return;

        if (name.startsWith("lcl.crypto.encrypt")) {
            handleEncrypt(event);
        } else if (name.startsWith("lcl.crypto.decrypt")) {
            handleDecrypt(event);
        } else if (name.startsWith("lcl.rotation")) {
            handleRotation(event);
        } else if (name.startsWith("lcl.keyvault.load")) {
            handleKeyVaultLoad(event);
        } else if (name.startsWith("lcl.blind_index")) {
            handleBlindIndex(event);
        }
        // Non-metric events are ignored
    }

    private void handleEncrypt(LclEvent event) {
        if (event.durationMicros() >= 0) {
            metrics.recordEncryptDuration(event.algorithm(), event.namespace(), event.durationMicros());
        }
        metrics.incrementEncryptCount(event.algorithm(), event.result());
    }

    private void handleDecrypt(LclEvent event) {
        if (event.durationMicros() >= 0) {
            metrics.recordDecryptDuration(event.algorithm(), event.namespace(), event.durationMicros());
        }
        metrics.incrementDecryptCount(event.algorithm(), event.result());
    }

    private void handleRotation(LclEvent event) {
        if (event.durationMicros() >= 0) {
            metrics.recordRotationDuration(event.namespace(), event.durationMicros());
        }
        metrics.incrementRotationCount(event.result());
    }

    private void handleKeyVaultLoad(LclEvent event) {
        if (event.durationMicros() >= 0) {
            metrics.recordKeyVaultLoadDuration(event.namespace(), event.durationMicros());
        }
    }

    private void handleBlindIndex(LclEvent event) {
        if (event.durationMicros() >= 0) {
            metrics.recordBlindIndexDuration(event.namespace(), event.durationMicros());
        }
    }
}
