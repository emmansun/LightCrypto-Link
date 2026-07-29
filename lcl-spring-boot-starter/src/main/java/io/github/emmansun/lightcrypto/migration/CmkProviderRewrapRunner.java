package io.github.emmansun.lightcrypto.migration;

import io.github.emmansun.lightcrypto.config.RewrapProperties;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.EventTier;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.RewrapResult;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;

/**
 * CommandLineRunner that performs cross-CMK provider re-wrap at application startup.
 * <p>
 * Disabled by default. Enable via {@code lightcrypto.migration.rewrap.enabled=true}.
 * Supports dry-run mode for validation without mutation.
 * </p>
 */
@Slf4j
public class CmkProviderRewrapRunner implements CommandLineRunner {

    private final KeyVaultService keyVaultService;
    private final VaultStore vaultStore;
    private final List<CmkProvider> cmkProviders;
    private final RewrapProperties properties;
    private final EventBus eventBus;
    private final ApplicationContext applicationContext;

    public CmkProviderRewrapRunner(KeyVaultService keyVaultService,
                                   VaultStore vaultStore,
                                   List<CmkProvider> cmkProviders,
                                   RewrapProperties properties,
                                   EventBus eventBus,
                                   ApplicationContext applicationContext) {
        this.keyVaultService = keyVaultService;
        this.vaultStore = vaultStore;
        this.cmkProviders = List.copyOf(cmkProviders);
        this.properties = properties;
        this.eventBus = eventBus;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            return;
        }

        CmkProvider targetProvider = resolveTargetProvider();
        if (targetProvider == null) {
            return; // Error already logged in resolveTargetProvider
        }

        log.info("[REWRAP] Starting cross-CMK re-wrap. target={} (id={}, ref={}), dryRun={}",
                describeTarget(), targetProvider.getProviderId(),
                targetProvider.getPublicReference(), properties.isDryRun());

        if (properties.isDryRun()) {
            performDryRun(targetProvider);
        } else {
            performLiveRewrap(targetProvider);
        }
    }

    private String describeTarget() {
        if (properties.getTargetBeanName() != null && !properties.getTargetBeanName().isBlank()) {
            return "bean:" + properties.getTargetBeanName();
        }
        if (properties.getTargetPublicReference() != null && !properties.getTargetPublicReference().isBlank()) {
            return properties.getTargetProviderId() + "#" + properties.getTargetPublicReference();
        }
        return properties.getTargetProviderId();
    }

    /**
     * Three-level target provider resolution:
     * <ol>
     *   <li>Bean name lookup (highest priority)</li>
     *   <li>ProviderId + publicReference dual match</li>
     *   <li>ProviderId alone (backward compatible)</li>
     * </ol>
     */
    private CmkProvider resolveTargetProvider() {
        // Level 1: Bean name
        String beanName = properties.getTargetBeanName();
        if (beanName != null && !beanName.isBlank()) {
            try {
                Object bean = applicationContext.getBean(beanName);
                if (bean instanceof CmkProvider provider) {
                    return provider;
                }
                log.error("[REWRAP] Bean '{}' is not a CmkProvider (actual type: {}). Skipping re-wrap.",
                        beanName, bean.getClass().getName());
                return null;
            } catch (Exception e) {
                log.error("[REWRAP] Target bean '{}' not found in application context. Skipping re-wrap.", beanName);
                return null;
            }
        }

        // Level 2/3: ProviderId-based
        String targetProviderId = properties.getTargetProviderId();
        if (targetProviderId == null || targetProviderId.isBlank()) {
            log.error("[REWRAP] Configuration error: neither target-bean-name nor target-provider-id is set. Skipping re-wrap.");
            return null;
        }

        String targetRef = properties.getTargetPublicReference();
        boolean hasRef = targetRef != null && !targetRef.isBlank();

        for (CmkProvider provider : cmkProviders) {
            if (!provider.getProviderId().equals(targetProviderId)) {
                continue;
            }
            if (hasRef) {
                // Level 2: dual match
                if (provider.getPublicReference().equals(targetRef)) {
                    return provider;
                }
            } else {
                // Level 3: providerId only
                return provider;
            }
        }

        if (hasRef) {
            log.error("[REWRAP] Target provider with id='{}' and publicReference='{}' not found. Skipping re-wrap.",
                    targetProviderId, targetRef);
        } else {
            log.error("[REWRAP] Target provider '{}' not found among registered CmkProvider beans. Skipping re-wrap.",
                    targetProviderId);
        }
        return null;
    }

    private void performDryRun(CmkProvider targetProvider) {
        List<VaultDocument> allDocs = vaultStore.loadAll();
        log.info("[REWRAP] DRY-RUN: Found {} vault(s) to validate.", allDocs.size());

        // Canary wrap/unwrap roundtrip with target provider
        byte[] canaryKey = new byte[32];
        Arrays.fill(canaryKey, (byte) 0x42);
        try {
            WrappedKey wrapped = targetProvider.wrap(canaryKey);
            byte[] unwrapped = targetProvider.unwrap(wrapped);
            if (!Arrays.equals(canaryKey, unwrapped)) {
                log.error("[REWRAP] DRY-RUN FAILED: Target provider canary roundtrip mismatch. Aborting.");
                return;
            }
            log.info("[REWRAP] DRY-RUN: Target provider canary roundtrip OK (algorithm={}).", wrapped.algorithm());
        } catch (Exception e) {
            log.error("[REWRAP] DRY-RUN FAILED: Target provider canary wrap/unwrap threw exception.", e);
            return;
        } finally {
            Arrays.fill(canaryKey, (byte) 0);
        }

        for (VaultDocument doc : allDocs) {
            String currentProvider = doc.cmkProvider();
            String currentRef = doc.cmkId();
            boolean sameProvider = targetProvider.getProviderId().equals(currentProvider)
                    && targetProvider.getPublicReference().equals(currentRef);
            if (sameProvider) {
                log.info("[REWRAP] DRY-RUN: Namespace '{}' already uses target provider (same id + ref) — would skip.", doc.namespace());
            } else {
                log.info("[REWRAP] DRY-RUN: Namespace '{}' would be re-wrapped from '{}#{}' to '{}#{}' ({} keys).",
                        doc.namespace(), currentProvider, currentRef,
                        targetProvider.getProviderId(), targetProvider.getPublicReference(), doc.keys().size());
            }
        }

        log.info("[REWRAP] DRY-RUN complete. No vaults were modified.");
    }

    private void performLiveRewrap(CmkProvider targetProvider) {
        List<RewrapResult> results = keyVaultService.rewrapAllVaults(targetProvider);

        long successCount = results.stream().filter(RewrapResult::success).count();
        long failedCount = results.size() - successCount;

        log.info("[REWRAP] Live re-wrap complete. total={}, success={}, failed={}",
                results.size(), successCount, failedCount);

        for (RewrapResult result : results) {
            if (result.success()) {
                log.info("[REWRAP] Namespace '{}': re-wrapped {} key(s) in {} micros.",
                        result.namespace(), result.keyCount(), result.durationMicros());
            } else {
                log.error("[REWRAP] Namespace '{}': FAILED — {}",
                        result.namespace(), result.errorMessage());
            }
        }

        eventBus.emit(LclEvent.builder()
                .event("lcl.rewrap.runner.completed")
                .tier(EventTier.L2)
                .result(failedCount == 0 ? "success" : "partial")
                .attribute("totalCount", String.valueOf(results.size()))
                .attribute("successCount", String.valueOf(successCount))
                .attribute("failedCount", String.valueOf(failedCount))
                .build());
    }
}
