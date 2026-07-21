package io.github.emmansun.lightcrypto.core.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event model for Light Crypto Link observability.
 * <p>
 * Events follow the naming convention: {@code lcl.<subsystem>.<operation>.<status>}
 * (lowercase, dot-separated, max 96 characters).
 * <p>
 * Events MUST NEVER contain: IV, Tag, Ciphertext, Wrapped DEK, CMK material,
 * Plaintext values, Query values, or Personal data of any kind.
 *
 * @since 1.0.0
 */
public final class LclEvent {

    private final String event;
    private final EventTier tier;
    private final Instant timestamp;
    private final long durationMicros;
    private final String result;
    private final String namespace;
    private final String algorithm;
    private final int dekVersion;
    private final String errorType;
    private final Map<String, String> attributes;

    private LclEvent(Builder builder) {
        this.event = Objects.requireNonNull(builder.event, "event must not be null");
        this.tier = Objects.requireNonNull(builder.tier, "tier must not be null");
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.durationMicros = builder.durationMicros;
        this.result = Objects.requireNonNull(builder.result, "result must not be null");
        this.namespace = builder.namespace;
        this.algorithm = builder.algorithm;
        this.dekVersion = builder.dekVersion;
        this.errorType = builder.errorType;
        this.attributes = builder.attributes.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public String event() {
        return event;
    }

    public EventTier tier() {
        return tier;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public long durationMicros() {
        return durationMicros;
    }

    public String result() {
        return result;
    }

    public String namespace() {
        return namespace;
    }

    public String algorithm() {
        return algorithm;
    }

    public int dekVersion() {
        return dekVersion;
    }

    public String errorType() {
        return errorType;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "LclEvent{event='" + event + "', tier=" + tier + ", result='" + result + "'}";
    }

    /**
     * Builder for constructing {@link LclEvent} instances.
     */
    public static final class Builder {
        private String event;
        private EventTier tier;
        private Instant timestamp;
        private long durationMicros = -1;
        private String result;
        private String namespace;
        private String algorithm;
        private int dekVersion = -1;
        private String errorType;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder event(String event) {
            this.event = event;
            return this;
        }

        public Builder tier(EventTier tier) {
            this.tier = tier;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder durationMicros(long durationMicros) {
            this.durationMicros = durationMicros;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder dekVersion(int dekVersion) {
            this.dekVersion = dekVersion;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        public LclEvent build() {
            return new LclEvent(this);
        }
    }
}
