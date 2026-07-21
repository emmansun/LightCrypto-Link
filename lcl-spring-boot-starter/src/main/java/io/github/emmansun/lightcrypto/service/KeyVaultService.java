package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
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
    private final CryptoProperties properties;
    private final Duration cacheTtl;
    private final Clock clock;

    /** Per-namespace key contexts: canonicalNamespace -> NamespaceKeyContext. */
    private final ConcurrentHashMap<String, NamespaceKeyContext> namespaceKeyContexts = new ConcurrentHashMap<>();

    public KeyVaultService(VaultStore vaultStore, CmkProvider cmkProvider,
                           CryptoProperties properties) {
        this(vaultStore, cmkProvider, properties, Clock.systemUTC());
    }

    /**
     * Constructor for testing with a custom {@link Clock} to control time-based expiry.
     */
    public KeyVaultService(VaultStore vaultStore, CmkProvider cmkProvider,
                    CryptoProperties properties, Clock clock) {
        this.vaultStore = vaultStore;
        this.cmkProvider = cmkProvider;
        this.properties = properties;
        this.cacheTtl = properties != null ? properties.getCacheTtl() : Duration.ofHours(1);
        this.clock = clock;
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

            log.info("DEK rotated for namespace {}: new active kid = {}", namespace, newKid);
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
        log.info("Initializing key vault for namespace: {}", namespace);

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

                ResolvedKeyPair pair = new ResolvedKeyPair(unwrappedDek, unwrappedHmac);
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

            log.info("Key vault loaded and verified: {} (active kid: {}, version: {})", namespace, activeKid, activeDekVersion);
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
            log.info("DEK cache flushed; all key material securely zeroed");
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

    /** Holds unwrapped DEK and HMAC key pair. */
    static class ResolvedKeyPair {
        final byte[] dek;
        final byte[] hmacKey;

        ResolvedKeyPair(byte[] dek, byte[] hmacKey) {
            this.dek = dek;
            this.hmacKey = hmacKey;
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
