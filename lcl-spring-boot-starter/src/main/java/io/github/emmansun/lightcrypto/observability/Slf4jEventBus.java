package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EventBus implementation that formats {@link LclEvent} as a structured JSON string
 * and outputs to Slf4j logger.
 * <p>
 * Log level mapping:
 * <ul>
 *   <li>L1 (Diagnostic) → DEBUG</li>
 *   <li>L2 (Operational) → INFO</li>
 *   <li>L3 (Audit) → INFO</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class Slf4jEventBus implements EventBus {

    private static final Logger LOG = LoggerFactory.getLogger("lcl.events");

    @Override
    public void emit(LclEvent event) {
        String json = toJson(event);
        switch (event.tier()) {
            case L1 -> LOG.debug("{}", json);
            case L2, L3 -> LOG.info("{}", json);
        }
    }

    /**
     * Format LclEvent as a JSON string. Only includes non-null fields.
     */
    private String toJson(LclEvent event) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendField(sb, "event", event.event(), true);
        appendField(sb, "tier", event.tier().name(), false);
        appendField(sb, "timestamp", event.timestamp().toString(), false);
        if (event.durationMicros() >= 0) {
            appendRawField(sb, "durationMicros", event.durationMicros());
        }
        appendField(sb, "result", event.result(), false);
        if (event.namespace() != null) {
            appendField(sb, "namespace", event.namespace(), false);
        }
        if (event.algorithm() != null) {
            appendField(sb, "algorithm", event.algorithm(), false);
        }
        if (event.dekVersion() >= 0) {
            appendRawField(sb, "dekVersion", event.dekVersion());
        }
        if (event.errorType() != null) {
            appendField(sb, "errorType", event.errorType(), false);
        }
        if (!event.attributes().isEmpty()) {
            sb.append(",\"attributes\":{");
            boolean first = true;
            for (var entry : event.attributes().entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(escapeJson(entry.getKey())).append("\":\"")
                  .append(escapeJson(entry.getValue())).append('"');
                first = false;
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String key, String value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(key).append("\":\"").append(escapeJson(value)).append('"');
    }

    private void appendRawField(StringBuilder sb, String key, long value) {
        sb.append(",\"").append(key).append("\":").append(value);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
