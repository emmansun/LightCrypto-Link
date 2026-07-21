package io.github.emmansun.lightcrypto.core.bootstrap;

import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.spi.VaultStore;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable context carrying all dependencies required by bootstrap phases.
 * <p>
 * Constructed by the Spring auto-configuration (or standalone bootstrap initiator)
 * and passed to {@link BootstrapEngine#run(BootstrapContext)}.
 *
 * @since 1.0.0
 */
public final class BootstrapContext {

    private final CmkProvider cmkProvider;
    private final EventBus eventBus;
    private final VaultStore vaultStore;
    private final boolean strictMode;
    private final int spiVersion;
    private final Duration bootstrapTimeout;

    private BootstrapContext(Builder builder) {
        this.cmkProvider = Objects.requireNonNull(builder.cmkProvider, "cmkProvider must not be null");
        this.eventBus = Objects.requireNonNull(builder.eventBus, "eventBus must not be null");
        this.vaultStore = builder.vaultStore;
        this.strictMode = builder.strictMode;
        this.spiVersion = builder.spiVersion;
        this.bootstrapTimeout = builder.bootstrapTimeout;
    }

    public CmkProvider cmkProvider() {
        return cmkProvider;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public Optional<VaultStore> vaultStore() {
        return Optional.ofNullable(vaultStore);
    }

    public boolean strictMode() {
        return strictMode;
    }

    public int spiVersion() {
        return spiVersion;
    }

    public Duration bootstrapTimeout() {
        return bootstrapTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BootstrapContext}.
     */
    public static final class Builder {
        private CmkProvider cmkProvider;
        private EventBus eventBus;
        private VaultStore vaultStore;
        private boolean strictMode = true;
        private int spiVersion = 1;
        private Duration bootstrapTimeout = Duration.ofSeconds(15);

        private Builder() {
        }

        public Builder cmkProvider(CmkProvider cmkProvider) {
            this.cmkProvider = cmkProvider;
            return this;
        }

        public Builder eventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Builder vaultStore(VaultStore vaultStore) {
            this.vaultStore = vaultStore;
            return this;
        }

        public Builder strictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }

        public Builder spiVersion(int spiVersion) {
            this.spiVersion = spiVersion;
            return this;
        }

        public Builder bootstrapTimeout(Duration bootstrapTimeout) {
            this.bootstrapTimeout = bootstrapTimeout;
            return this;
        }

        public BootstrapContext build() {
            return new BootstrapContext(this);
        }
    }
}
