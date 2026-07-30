package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.config.KeyVaultProperties;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.EventTier;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.exception.OptimisticLockException;
import io.github.emmansun.lightcrypto.model.GeneratedKey;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyEntry;
import io.github.emmansun.lightcrypto.spi.VaultDocument.KeyStatus;
import io.github.emmansun.lightcrypto.spi.VaultStore;
import io.github.emmansun.lightcrypto.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key Vault service — manages per-namespace DEK and HMAC keys via {@link VaultStore} SPI.
 * <p>
 * Each namespace (tenant.realm.entity#field) gets its own vault document.
 * Each vault supports key versioning via a keys[] array with kid-based lookup.
 * </p>
 * <p>
 * This service is storage-agnostic — all persistence operations are delegated to
 * the injected {@link VaultStore} implementation.
 * </p>
 */
@Slf4j
public class KeyVaultService {

    private static final int KEY_LENGTH = 32;
    private static final AlgorithmId KCV_ALGORITHM = AlgorithmId.AES_256_GCM;

    private final VaultStore vaultStore;
    private final CmkProvider cmkProvider;
    private final Duration cacheTtl;
    private final Clock clock;
    private final EventBus eventBus;

    /** Per-namespace key contexts: canonicalNamespace -> NamespaceKeyContext. */
    private final ConcurrentHashMap<String, NamespaceKeyContext> namespaceKeyContexts = new ConcurrentHashMap<>();

    public KeyVaultService(VaultStore vaultStore, CmkProvider cmkProvider,
                           KeyVaultProperties keyVaultProperties) {
        this(vaultStore, cmkProvider, keyVaultProperties, Clock.systemUTC(), NoOpEventBus.INSTANCE);
    }

    public KeyVaultService(VaultStore vaultStore, CmkProvider cmkProvider,
                           KeyVaultProperties keyVaultProperties, EventBus eventBus) {
        this(vaultStore, cmkProvider, keyVaultProperties, Clock.systemUTC(), eventBus);
    }

    /**
     * Constructor for testing with a custom {@link Clock} to control time-based expiry.
     */
    public KeyVaultService(VaultStore vaultStore, CmkProvider cmkProvider,
                    KeyVaultProperties keyVaultProperties, Clock clock) {
        this(vaultStore, cmkProvider, keyVaultProperties, clock, NoOpEventBus.INSTANCE);
    }

    /**
     * Full constructor with EventBus and Clock.
     */
    public KeyVaultService(VaultStore vaultStore, CmkProvider cmkProvider,
                    KeyVaultProperties keyVaultProperties, Clock clock, EventBus eventBus) {
        this.vaultStore = vaultStore;
        this.cmkProvider = cmkProvider;
        this.cacheTtl = keyVaultProperties != null && keyVaultProperties.getCache() != null
                ? keyVaultProperties.getCache().getTtl()
                : Duration.ofHours(1);
        this.clock = clock;
        this.eventBus = eventBus != null ? eventBus : NoOpEventBus.INSTANCE;
    }

    /**
     * Ensure vault is initialized for the given namespace. Lazily initializes
     * if the vault does not yet exist.
     */
    public void ensureVaultInitialized(String namespace) {
        NamespaceKeyContext existing = namespaceKeyContexts.get(namespace);
        if (existing != null && !existing.isExpired()) return;
        synchronized (this) {
            existing = namespaceKeyContexts.get(namespace);
            if (existing != null && !existing.isExpired()) return;
            if (existing != null) {
                destroyKeyMaterial(existing);
                namespaceKeyContexts.remove(namespace);
            }
            initForNamespace(namespace);
        }
    }

    /**
     * Get the active kid for the given namespace.
     */
    public String getActiveKid(String namespace) {
        NamespaceKeyContext ctx = namespaceKeyContexts.get(namespace);
        if (ctx == null) {
            throw new FatalCryptoException(
                    "Vault not initialized for namespace: " + namespace +
                            ". Call ensureVaultInitialized() first.");
        }
        return ctx.activeKid;
    }

    /**
     * Get the active DEK version number for the given namespace.
     */
    public int getActiveDekVersion(String namespace) {
        NamespaceKeyContext ctx = namespaceKeyContexts.get(namespace);
        if (ctx == null) {
            throw new FatalCryptoException(
                    "Vault not initialized for namespace: " + namespace);
        }
        return ctx.activeDekVersion;
    }

    /**
     * Get the unwrapped DEK for the given kid.
     */
    public byte[] getDek(String kid) {
        for (NamespaceKeyContext ctx : namespaceKeyContexts.values()) {
            ResolvedKeyPair pair = ctx.resolvedKeys.get(kid);
            if (pair != null) return pair.dek;
        }
        throw new FatalCryptoException("Unknown kid: " + kid);
    }

    /**
     * Get the unwrapped DEK for the given namespace and DEK version.
     *
     * @throws FatalCryptoException if the resolved key entry has status RETIRED
     */
    public byte[] getDekByVersion(String namespace, int dekVersion) {
        NamespaceKeyContext ctx = namespaceKeyContexts.get(namespace);
        if (ctx == null) {
            throw new FatalCryptoException("Vault not initialized for namespace: " + namespace);
        }
        ResolvedKeyPair pair = ctx.resolvedKeysByVersion.get(dekVersion);
        if (pair == null) {
            throw new FatalCryptoException(
                    "No key found for namespace " + namespace + " with dekVersion " + dekVersion);
        }
        if (pair.status == KeyStatus.RETIRED) {
            throw new FatalCryptoException(
                    "Key for namespace " + namespace + " with dekVersion " + dekVersion
                            + " has been RETIRED. Data should have been re-encrypted before retirement.");
        }
        return pair.dek;
    }

    /**
     * Get the unwrapped HMAC key for the given kid.
     */
    public byte[] getHmacKey(String kid) {
        for (NamespaceKeyContext ctx : namespaceKeyContexts.values()) {
            ResolvedKeyPair pair = ctx.resolvedKeys.get(kid);
            if (pair != null) return pair.hmacKey;
        }
        throw new FatalCryptoException("Unknown kid: " + kid);
    }

    /**
     * Get the active HMAC key for the given namespace.
     */
    public byte[] getActiveHmacKey(String namespace) {
        String kid = getActiveKid(namespace);
        return getHmacKey(kid);
    }

    /**
     * Get all unwrapped HMAC keys for the given namespace, ordered by key version.
     */
    public List<byte[]> getHmacKeys(String namespace) {
        NamespaceKeyContext ctx = namespaceKeyContexts.get(namespace);
        if (ctx == null) {
            throw new FatalCryptoException("Vault not initialized for namespace: " + namespace);
        }
        return ctx.resolvedKeysByVersion.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().hmacKey.clone())
                .toList();
    }

    /**
     * Rotate the DEK for the given namespace.
     */
    public void rotateDek(String namespace) {
        synchronized (this) {
            Optional<VaultDocument> optDoc = vaultStore.load(namespace);
            if (optDoc.isEmpty()) {
                throw new FatalCryptoException("Vault not found for namespace: " + namespace);
            }

            VaultDocument doc = optDoc.get();
            long expectedVersion = doc.version();

            // Mark all ACTIVE keys as ROTATED, find max version
            List<KeyEntry> updatedKeys = new ArrayList<>();
            int maxVersion = 0;
            for (KeyEntry entry : doc.keys()) {
                KeyStatus newStatus = entry.status() == KeyStatus.ACTIVE ? KeyStatus.ROTATED : entry.status();
                updatedKeys.add(new KeyEntry(
                        entry.kid(), newStatus, entry.wrappedDek(), entry.wrappedHmac(),
                        entry.wrappingAlgorithm(),
                        entry.dekKcv(), entry.hmacKcv(), entry.binding(), entry.createdAt()));
                int ver = parseVersion(entry.kid());
                if (ver > maxVersion) maxVersion = ver;
            }

            // Create new key entry
            String newKid = generateKid(maxVersion + 1);
            KeyEntry newEntry = createKeyEntry(newKid);
            updatedKeys.add(newEntry);

            // Build updated document with incremented version
            VaultDocument updatedDoc = new VaultDocument(
                    namespace,
                    updatedKeys,
                    newKid,
                    expectedVersion + 1,
                    doc.cmkProvider(),
                    doc.cmkId(),
                    doc.createdAt(),
                    Instant.now());

            // Persist with optimistic locking
            try {
                vaultStore.rotate(updatedDoc);
            } catch (OptimisticLockException e) {
                throw new FatalCryptoException(
                        "Concurrent vault rotation detected for namespace: " + namespace + ". Please retry.", e);
            }

            verifyAndLoadKeys(updatedDoc, namespace);

            eventBus.emit(LclEvent.builder()
                    .event("lcl.rotation.execute.completed")
                    .tier(EventTier.L2)
                    .result("success")
                    .namespace(namespace)
                    .attribute("kid", newKid)
                    .build());
        }
    }

    /**
     * Re-wrap all key entries in the specified namespace under a new CMK provider.
     * <p>
     * This operation unwraps all entries (ACTIVE + ROTATED) with the current provider,
     * verifies KCV/binding invariance, re-wraps with the target provider, performs
     * post-rewrap roundtrip verification, and persists atomically with optimistic locking.
     * </p>
     *
     * @param namespace the canonical namespace to re-wrap
     * @param targetProvider the target CMK provider to re-wrap keys under
     * @return the result of the re-wrap operation
     */
    public RewrapResult rewrapVault(String namespace, CmkProvider targetProvider) {
        long startNanos = System.nanoTime();
        try {
            synchronized (this) {
                Optional<VaultDocument> optDoc = vaultStore.load(namespace);
                if (optDoc.isEmpty()) {
                    throw new FatalCryptoException("Vault not found for namespace: " + namespace);
                }

                VaultDocument doc = optDoc.get();

                // Skip if same provider AND same key (both providerId and publicReference match)
                if (targetProvider.getProviderId().equals(doc.cmkProvider())
                        && targetProvider.getPublicReference().equals(doc.cmkId())) {
                    long durationMicros = (System.nanoTime() - startNanos) / 1_000;
                    return RewrapResult.success(namespace, doc.keys().size(), durationMicros);
                }

                long expectedVersion = doc.version();
                List<KeyEntry> rewrappedKeys = new ArrayList<>();

                for (KeyEntry entry : doc.keys()) {
                    // Unwrap with current provider
                    byte[] rawDek = cmkProvider.unwrap(new WrappedKey(entry.wrappedDek(), entry.wrappingAlgorithm()));
                    byte[] rawHmac = cmkProvider.unwrap(new WrappedKey(entry.wrappedHmac(), entry.wrappingAlgorithm()));

                    // Verify KCV/binding invariance
                    String computedDekKcv = KeyCheckValue.computeDekKcv(rawDek, KCV_ALGORITHM);
                    if (!computedDekKcv.equals(entry.dekKcv())) {
                        throw new FatalCryptoException(
                                "DEK KCV mismatch for kid " + entry.kid() + " during re-wrap! Vault integrity compromised.");
                    }
                    String computedHmacKcv = KeyCheckValue.computeHmacKcv(rawHmac);
                    if (!computedHmacKcv.equals(entry.hmacKcv())) {
                        throw new FatalCryptoException(
                                "HMAC KCV mismatch for kid " + entry.kid() + " during re-wrap! Vault integrity compromised.");
                    }
                    String computedBinding = KeyCheckValue.computeBinding(rawHmac, rawDek);
                    if (!computedBinding.equals(entry.binding())) {
                        throw new FatalCryptoException(
                                "Key binding mismatch for kid " + entry.kid() + " during re-wrap! DEK/HMAC key pair corrupted.");
                    }

                    // Re-wrap with target provider
                    WrappedKey newWrappedDek = targetProvider.wrap(rawDek);
                    WrappedKey newWrappedHmac = targetProvider.wrap(rawHmac);

                    // Post-rewrap roundtrip verification
                    byte[] verifyDek = targetProvider.unwrap(newWrappedDek);
                    byte[] verifyHmac = targetProvider.unwrap(newWrappedHmac);
                    if (!Arrays.equals(rawDek, verifyDek) || !Arrays.equals(rawHmac, verifyHmac)) {
                        throw new FatalCryptoException(
                                "Post-rewrap roundtrip verification failed for kid " + entry.kid()
                                        + " with target provider " + targetProvider.getProviderId());
                    }

                    rewrappedKeys.add(new KeyEntry(
                            entry.kid(), entry.status(),
                            newWrappedDek.ciphertext(), newWrappedHmac.ciphertext(),
                            newWrappedDek.algorithm(),
                            entry.dekKcv(), entry.hmacKcv(), entry.binding(), entry.createdAt()));

                    // Securely clear raw key material
                    Arrays.fill(rawDek, (byte) 0);
                    Arrays.fill(rawHmac, (byte) 0);
                    Arrays.fill(verifyDek, (byte) 0);
                    Arrays.fill(verifyHmac, (byte) 0);
                }

                // Build updated document
                VaultDocument updatedDoc = new VaultDocument(
                        namespace,
                        rewrappedKeys,
                        doc.activeKid(),
                        expectedVersion + 1,
                        targetProvider.getProviderId(),
                        targetProvider.getPublicReference(),
                        doc.createdAt(),
                        Instant.now());

                // Persist with optimistic locking
                try {
                    vaultStore.rotate(updatedDoc);
                } catch (OptimisticLockException e) {
                    throw new FatalCryptoException(
                            "Concurrent modification detected during re-wrap for namespace: " + namespace + ". Please retry.", e);
                }

                // Evict DEK cache entry
                NamespaceKeyContext evicted = namespaceKeyContexts.remove(namespace);
                if (evicted != null) {
                    destroyKeyMaterial(evicted);
                }

                long durationMicros = (System.nanoTime() - startNanos) / 1_000;

                eventBus.emit(LclEvent.builder()
                        .event("lcl.rewrap.namespace.completed")
                        .tier(EventTier.L2)
                        .result("success")
                        .namespace(namespace)
                        .durationMicros(durationMicros)
                        .attribute("targetProviderId", targetProvider.getProviderId())
                        .attribute("keyCount", String.valueOf(rewrappedKeys.size()))
                        .build());

                return RewrapResult.success(namespace, rewrappedKeys.size(), durationMicros);
            }
        } catch (Exception e) {
            long durationMicros = (System.nanoTime() - startNanos) / 1_000;
            eventBus.emit(LclEvent.builder()
                    .event("lcl.rewrap.namespace.failed")
                    .tier(EventTier.L2)
                    .result("failure")
                    .namespace(namespace)
                    .durationMicros(durationMicros)
                    .errorType(e.getClass().getSimpleName())
                    .build());
            if (e instanceof FatalCryptoException fce) {
                throw fce;
            }
            throw new FatalCryptoException("Re-wrap failed for namespace: " + namespace, e);
        }
    }

    /**
     * Re-wrap all vaults under a new CMK provider with per-namespace error isolation.
     *
     * @param targetProvider the target CMK provider
     * @return list of results for each namespace
     */
    public List<RewrapResult> rewrapAllVaults(CmkProvider targetProvider) {
        long batchStartNanos = System.nanoTime();
        List<VaultDocument> allDocs = vaultStore.loadAll();
        List<RewrapResult> results = new ArrayList<>();

        for (VaultDocument doc : allDocs) {
            try {
                RewrapResult result = rewrapVault(doc.namespace(), targetProvider);
                results.add(result);
            } catch (Exception e) {
                long durationMicros = (System.nanoTime() - batchStartNanos) / 1_000;
                results.add(RewrapResult.failure(doc.namespace(), e.getMessage(), durationMicros));
                log.error("Re-wrap failed for namespace: {}", doc.namespace(), e);
            }
        }

        long totalDurationMicros = (System.nanoTime() - batchStartNanos) / 1_000;
        long successCount = results.stream().filter(RewrapResult::success).count();
        long failedCount = results.size() - successCount;

        eventBus.emit(LclEvent.builder()
                .event("lcl.rewrap.batch.completed")
                .tier(EventTier.L2)
                .result(failedCount == 0 ? "success" : "partial")
                .durationMicros(totalDurationMicros)
                .attribute("totalCount", String.valueOf(results.size()))
                .attribute("successCount", String.valueOf(successCount))
                .attribute("failedCount", String.valueOf(failedCount))
                .build());

        return results;
    }

    /**
     * Marks specified key entries as RETIRED for the given namespace.
     * <p>
     * Only keys with status ROTATED can be transitioned to RETIRED.
     * This operation is called by the re-encryption engine after all documents
     * have been migrated to the active DEK version.
     * </p>
     *
     * @param namespace the canonical namespace
     * @param kids      the set of key identifiers to mark as RETIRED
     */
    public void markKeysRetired(String namespace, Set<String> kids) {
        synchronized (this) {
            Optional<VaultDocument> optDoc = vaultStore.load(namespace);
            if (optDoc.isEmpty()) {
                throw new FatalCryptoException("Vault not found for namespace: " + namespace);
            }

            VaultDocument doc = optDoc.get();
            long expectedVersion = doc.version();

            List<KeyEntry> updatedKeys = new ArrayList<>();
            boolean changed = false;
            for (KeyEntry entry : doc.keys()) {
                if (kids.contains(entry.kid()) && entry.status() == KeyStatus.ROTATED) {
                    updatedKeys.add(new KeyEntry(
                            entry.kid(), KeyStatus.RETIRED, entry.wrappedDek(), entry.wrappedHmac(),
                            entry.wrappingAlgorithm(),
                            entry.dekKcv(), entry.hmacKcv(), entry.binding(), entry.createdAt()));
                    changed = true;
                } else {
                    updatedKeys.add(entry);
                }
            }

            if (!changed) {
                return;
            }

            VaultDocument updatedDoc = new VaultDocument(
                    namespace,
                    updatedKeys,
                    doc.activeKid(),
                    expectedVersion + 1,
                    doc.cmkProvider(),
                    doc.cmkId(),
                    doc.createdAt(),
                    Instant.now());

            try {
                vaultStore.rotate(updatedDoc);
            } catch (OptimisticLockException e) {
                throw new FatalCryptoException(
                        "Concurrent modification detected while marking keys retired for namespace: " + namespace, e);
            }

            // Refresh cached context
            NamespaceKeyContext evicted = namespaceKeyContexts.remove(namespace);
            if (evicted != null) {
                destroyKeyMaterial(evicted);
            }
            initForNamespace(namespace);

            eventBus.emit(LclEvent.builder()
                    .event("lcl.keyvault.keys.retired")
                    .tier(EventTier.L2)
                    .result("success")
                    .namespace(namespace)
                    .attribute("retiredKids", String.join(",", kids))
                    .build());
        }
    }

    /**
     * Removes all RETIRED key entries from the vault document for the given namespace.
     * <p>
     * This is a manual operation for ops to clean up retired key material after
     * verifying that re-encryption has completed successfully.
     * </p>
     *
     * @param namespace the canonical namespace
     * @return the number of RETIRED entries removed
     */
    public int pruneRetiredKeys(String namespace) {
        synchronized (this) {
            Optional<VaultDocument> optDoc = vaultStore.load(namespace);
            if (optDoc.isEmpty()) {
                throw new FatalCryptoException("Vault not found for namespace: " + namespace);
            }

            VaultDocument doc = optDoc.get();
            long expectedVersion = doc.version();

            List<KeyEntry> prunedKeys = new ArrayList<>();
            int removedCount = 0;
            for (KeyEntry entry : doc.keys()) {
                if (entry.status() == KeyStatus.RETIRED) {
                    removedCount++;
                } else {
                    prunedKeys.add(entry);
                }
            }

            if (removedCount == 0) {
                return 0;
            }

            VaultDocument updatedDoc = new VaultDocument(
                    namespace,
                    prunedKeys,
                    doc.activeKid(),
                    expectedVersion + 1,
                    doc.cmkProvider(),
                    doc.cmkId(),
                    doc.createdAt(),
                    Instant.now());

            try {
                vaultStore.rotate(updatedDoc);
            } catch (OptimisticLockException e) {
                throw new FatalCryptoException(
                        "Concurrent modification detected while pruning retired keys for namespace: " + namespace, e);
            }

            // Refresh cached context
            NamespaceKeyContext evicted = namespaceKeyContexts.remove(namespace);
            if (evicted != null) {
                destroyKeyMaterial(evicted);
            }
            initForNamespace(namespace);

            eventBus.emit(LclEvent.builder()
                    .event("lcl.keyvault.keys.pruned")
                    .tier(EventTier.L2)
                    .result("success")
                    .namespace(namespace)
                    .attribute("removedCount", String.valueOf(removedCount))
                    .build());

            return removedCount;
        }
    }

    // ===== Internal methods =====

    private void initForNamespace(String namespace) {
        Optional<VaultDocument> optDoc = vaultStore.load(namespace);
        VaultDocument doc;
        if (optDoc.isEmpty()) {
            doc = initializeVault(namespace);
        } else {
            doc = optDoc.get();
        }
        verifyAndLoadKeys(doc, namespace);
    }

    private VaultDocument initializeVault(String namespace) {
        eventBus.emit(LclEvent.builder()
                .event("lcl.keyvault.init.completed")
                .tier(EventTier.L2)
                .result("success")
                .namespace(namespace)
                .build());

        String kid = generateKid(1);
        KeyEntry entry = createKeyEntry(kid);

        Instant now = Instant.now();
        VaultDocument doc = new VaultDocument(
                namespace,
                new ArrayList<>(List.of(entry)),
                kid,
                1L,
                cmkProvider.getProviderId(),
                cmkProvider.getPublicReference(),
                now,
                now);

        try {
            vaultStore.save(doc);
        } catch (RuntimeException e) {
            // Handle concurrent initialization — another instance may have created the document
            log.warn("Vault document may already exist (concurrent init), attempting to load: {}", e.getMessage());
            Optional<VaultDocument> existing = vaultStore.load(namespace);
            if (existing.isPresent()) {
                return existing.get();
            }
            throw e;
        }
        return doc;
    }

    private KeyEntry createKeyEntry(String kid) {
        GeneratedKey dekPair = cmkProvider.generateKey(KEY_LENGTH);
        byte[] rawDek = dekPair.rawKey();
        WrappedKey wrappedDek = dekPair.wrappedKey();

        GeneratedKey hmacPair = cmkProvider.generateKey(KEY_LENGTH);
        byte[] rawHmac = hmacPair.rawKey();
        WrappedKey wrappedHmac = hmacPair.wrappedKey();

        String dekKcv = KeyCheckValue.computeDekKcv(rawDek, KCV_ALGORITHM);
        String hmacKcv = KeyCheckValue.computeHmacKcv(rawHmac);
        String binding = KeyCheckValue.computeBinding(rawHmac, rawDek);

        return new KeyEntry(
                kid,
                KeyStatus.ACTIVE,
                wrappedDek.ciphertext(),
                wrappedHmac.ciphertext(),
                wrappedDek.algorithm(),
                dekKcv,
                hmacKcv,
                binding,
                Instant.now());
    }

    private void verifyAndLoadKeys(VaultDocument doc, String namespace) {
        try {
            if (doc.keys() == null || doc.keys().isEmpty()) {
                throw new FatalCryptoException("Vault has no key entries for namespace: " + namespace);
            }

            Map<String, ResolvedKeyPair> resolvedKeys = new HashMap<>();
            Map<Integer, ResolvedKeyPair> resolvedKeysByVersion = new HashMap<>();
            String activeKid = null;
            int activeDekVersion = 0;
            int activeCount = 0;

            for (KeyEntry entry : doc.keys()) {
                // Unwrap DEK
                byte[] unwrappedDek = cmkProvider.unwrap(new WrappedKey(entry.wrappedDek(), entry.wrappingAlgorithm()));

                // Unwrap HMAC key
                byte[] unwrappedHmac = cmkProvider.unwrap(new WrappedKey(entry.wrappedHmac(), entry.wrappingAlgorithm()));

                // KCV verification
                String expectedDekKcv = KeyCheckValue.computeDekKcv(unwrappedDek, KCV_ALGORITHM);
                if (!expectedDekKcv.equals(entry.dekKcv())) {
                    throw new FatalCryptoException(
                            "DEK KCV mismatch for kid " + entry.kid() + "! Vault integrity compromised.");
                }
                String expectedHmacKcv = KeyCheckValue.computeHmacKcv(unwrappedHmac);
                if (!expectedHmacKcv.equals(entry.hmacKcv())) {
                    throw new FatalCryptoException(
                            "HMAC Key KCV mismatch for kid " + entry.kid() + "! Vault integrity compromised.");
                }

                // Binding verification
                String expectedBinding = KeyCheckValue.computeBinding(unwrappedHmac, unwrappedDek);
                if (!expectedBinding.equals(entry.binding())) {
                    throw new FatalCryptoException(
                            "Key binding mismatch for kid " + entry.kid() + "! DEK/HMAC key pair corrupted.");
                }

                ResolvedKeyPair pair = new ResolvedKeyPair(unwrappedDek, unwrappedHmac, entry.status());
                resolvedKeys.put(entry.kid(), pair);

                int version = parseVersion(entry.kid());
                resolvedKeysByVersion.put(version, pair);

                if (entry.status() == KeyStatus.ACTIVE) {
                    activeKid = entry.kid();
                    activeDekVersion = version;
                    activeCount++;
                }
            }

            if (activeCount == 0) {
                throw new FatalCryptoException("Vault has no ACTIVE key entry for namespace: " + namespace);
            }
            if (activeCount > 1) {
                throw new FatalCryptoException("Vault has multiple ACTIVE key entries for namespace: " + namespace);
            }

            NamespaceKeyContext ctx = new NamespaceKeyContext(activeKid, activeDekVersion, resolvedKeys, resolvedKeysByVersion, computeExpiresAt());
            if (!cacheTtl.isZero()) {
                namespaceKeyContexts.put(namespace, ctx);
            }

            eventBus.emit(LclEvent.builder()
                    .event("lcl.keyvault.load.completed")
                    .tier(EventTier.L2)
                    .result("success")
                    .namespace(namespace)
                    .attribute("activeKid", activeKid)
                    .attribute("dekVersion", String.valueOf(activeDekVersion))
                    .build());
        } catch (FatalCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new FatalCryptoException("Failed to verify key vault for namespace: " + namespace, e);
        }
    }

    /**
     * Generate a kid: v{version}-{8 hex chars}.
     */
    static String generateKid(int version) {
        byte[] suffix = CryptoUtils.generateRandomBytes(4);
        return "v" + version + "-" + HexFormat.of().formatHex(suffix);
    }

    /**
     * Parse version number from kid (e.g., "v1-a3b2c1d4" -> 1).
     */
    private static int parseVersion(String kid) {
        try {
            String verPart = kid.substring(1, kid.indexOf('-'));
            return Integer.parseInt(verPart);
        } catch (Exception e) {
            throw new FatalCryptoException("Invalid kid format: " + kid);
        }
    }

    // ===== Cache management =====

    /**
     * Flush the DEK cache, securely destroying all cached key material.
     */
    public void flushCache() {
        synchronized (this) {
            for (NamespaceKeyContext ctx : namespaceKeyContexts.values()) {
                destroyKeyMaterial(ctx);
            }
            namespaceKeyContexts.clear();
            eventBus.emit(LclEvent.builder()
                    .event("lcl.keyvault.cache.evicted")
                    .tier(EventTier.L1)
                    .result("success")
                    .build());
        }
    }

    private void destroyKeyMaterial(NamespaceKeyContext ctx) {
        for (ResolvedKeyPair pair : ctx.resolvedKeys.values()) {
            Arrays.fill(pair.dek, (byte) 0);
            Arrays.fill(pair.hmacKey, (byte) 0);
        }
    }

    private Instant computeExpiresAt() {
        if (cacheTtl.isZero()) {
            return Instant.EPOCH;
        }
        return Instant.now(clock).plus(cacheTtl);
    }

    // ===== Inner classes =====

    /** Holds unwrapped DEK and HMAC key pair with status. */
    static class ResolvedKeyPair {
        final byte[] dek;
        final byte[] hmacKey;
        final KeyStatus status;

        ResolvedKeyPair(byte[] dek, byte[] hmacKey, KeyStatus status) {
            this.dek = dek;
            this.hmacKey = hmacKey;
            this.status = status;
        }
    }

    /** Per-namespace key context with active kid, version, resolved key pairs, and TTL expiry. */
    static class NamespaceKeyContext {
        final String activeKid;
        final int activeDekVersion;
        final Map<String, ResolvedKeyPair> resolvedKeys;
        final Map<Integer, ResolvedKeyPair> resolvedKeysByVersion;
        final Instant expiresAt;

        NamespaceKeyContext(String activeKid, int activeDekVersion,
                           Map<String, ResolvedKeyPair> resolvedKeys,
                           Map<Integer, ResolvedKeyPair> resolvedKeysByVersion,
                           Instant expiresAt) {
            this.activeKid = activeKid;
            this.activeDekVersion = activeDekVersion;
            this.resolvedKeys = resolvedKeys;
            this.resolvedKeysByVersion = resolvedKeysByVersion;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return !Instant.now().isBefore(expiresAt);
        }
    }
}
