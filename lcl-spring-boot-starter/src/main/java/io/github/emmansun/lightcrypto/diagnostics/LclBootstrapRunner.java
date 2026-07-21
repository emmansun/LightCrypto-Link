package io.github.emmansun.lightcrypto.diagnostics;

import io.github.emmansun.lightcrypto.config.RuntimeProperties;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapContext;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapEngine;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapPhase;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapResult;
import io.github.emmansun.lightcrypto.core.bootstrap.CanaryRunner;
import io.github.emmansun.lightcrypto.core.bootstrap.ConfigValidationCheck;
import io.github.emmansun.lightcrypto.core.bootstrap.FailureClass;
import io.github.emmansun.lightcrypto.core.bootstrap.KatRunner;
import io.github.emmansun.lightcrypto.core.bootstrap.KmsReachabilityCheck;
import io.github.emmansun.lightcrypto.core.bootstrap.SpiVersionCheck;
import io.github.emmansun.lightcrypto.core.bootstrap.VaultReachabilityCheck;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ApplicationRunner that triggers the LCL bootstrap diagnostics sequence.
 * <p>
 * Constructs a {@link BootstrapContext}, registers all bootstrap phases,
 * and runs the {@link BootstrapEngine} after the Spring context is fully initialized.
 *
 * @since 1.0.0
 */
public class LclBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LclBootstrapRunner.class);

    private final CmkProvider cmkProvider;
    private final EventBus eventBus;
    private final RuntimeProperties runtimeProperties;
    private final VaultStore vaultStore;
    private final KatRunner katRunner;
    private final AtomicReference<BootstrapResult> lastResult = new AtomicReference<>();

    public LclBootstrapRunner(CmkProvider cmkProvider, EventBus eventBus,
                              RuntimeProperties runtimeProperties, VaultStore vaultStore) {
        this.cmkProvider = cmkProvider;
        this.eventBus = eventBus;
        this.runtimeProperties = runtimeProperties;
        this.vaultStore = vaultStore;
        this.katRunner = new KatRunner();
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("LCL Bootstrap diagnostics starting...");

        BootstrapContext context = BootstrapContext.builder()
                .cmkProvider(cmkProvider)
                .eventBus(eventBus)
                .vaultStore(vaultStore)
                .strictMode(runtimeProperties.isStrictMode())
                .spiVersion(runtimeProperties.getSpiVersion())
                .bootstrapTimeout(runtimeProperties.getBootstrapTimeout())
                .build();

        List<BootstrapPhase> phases = List.of(
                new BootstrapPhase("BOOT-1 Config", new ConfigValidationCheck(), FailureClass.FATAL),
                new BootstrapPhase("BOOT-2 SPI", new SpiVersionCheck(), FailureClass.FATAL),
                new BootstrapPhase("BOOT-4 KAT", katRunner, FailureClass.FATAL),
                new BootstrapPhase("BOOT-8 Vault", new VaultReachabilityCheck(), FailureClass.RECOVERABLE),
                new BootstrapPhase("BOOT-9 KMS", new KmsReachabilityCheck(), FailureClass.RECOVERABLE),
                new BootstrapPhase("BOOT-10 Canary", new CanaryRunner(), FailureClass.FATAL)
        );

        BootstrapEngine engine = new BootstrapEngine(phases);

        try {
            BootstrapResult result = engine.run(context);
            lastResult.set(result);

            if (result.isReady()) {
                log.info("LCL Bootstrap completed: READY in {}ms", result.durationMs());
            } else {
                log.warn("LCL Bootstrap completed: {} in {}ms (failed phase: {})",
                        result.status(), result.durationMs(), result.failedPhase());
            }
        } catch (Exception e) {
            log.error("LCL Bootstrap failed with exception: {}", e.getMessage(), e);
            lastResult.set(BootstrapResult.failed(List.of(), 0, "bootstrap", e.getMessage()));
            throw e;
        }
    }

    /**
     * Returns the last bootstrap result (for health indicator and diagnostics endpoint).
     */
    public BootstrapResult getLastResult() {
        return lastResult.get();
    }

    /**
     * Returns the KatRunner instance (for KAT diagnostics endpoint).
     */
    public KatRunner getKatRunner() {
        return katRunner;
    }
}
