package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.model.GeneratedKey;
import io.github.emmansun.lightcrypto.model.KeyVaultDocument;
import io.github.emmansun.lightcrypto.model.KeyVaultDocument.WrappedKeyInfo;
import io.github.emmansun.lightcrypto.model.KeyVaultDocument.KeyVersionEntry;
import io.github.emmansun.lightcrypto.model.KeyVaultDocument.CmkInfo;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.util.CryptoUtils;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key Vault service — manages per-namespace DEK and HMAC keys stored in
 * the __lcl_keyvault collection.
 * <p>
 * Each namespace (tenant.realm.entity#field) gets its own vault document
 * (_id = lcl-dek-{canonicalNamespace}). Each vault supports key versioning
 * via a keys[] array with kid-based lookup.
 * </p>
 */
@Slf4j
public class KeyVaultService {

    private static final String KEY_VAULT_COLLECTION = "__lcl_keyvault";
    private static final int KEY_LENGTH = 32;
    private static final String VAULT_ID_PREFIX = "lcl-dek-";
    private static final AlgorithmId KCV_ALGORITHM = AlgorithmId.AES_256_GCM;

    private final MongoTemplate mongoTemplate;
    private final CmkProvider cmkProvider;
    private final CryptoProperties properties;
    private final Duration cacheTtl;
    private final Clock clock;

    /** Per-namespace key contexts: canonicalNamespace -> NamespaceKeyContext. */
    private final ConcurrentHashMap<String, NamespaceKeyContext> namespaceKeyContexts = new ConcurrentHashMap<>();

    public KeyVaultService(MongoTemplate mongoTemplate, CmkProvider cmkProvider,
                           CryptoProperties properties) {
        this(mongoTemplate, cmkProvider, properties, Clock.systemUTC());
    }

    /**
     * Constructor for testing with a custom {@link Clock} to control time-based expiry.
     */
    public KeyVaultService(MongoTemplate mongoTemplate, CmkProvider cmkProvider,
                    CryptoProperties properties, Clock clock) {
        this.mongoTemplate = mongoTemplate;
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
            String vaultId = VAULT_ID_PREFIX + namespace;

            KeyVaultDocument doc = loadVaultDocument(vaultId);
            if (doc == null) {
                throw new FatalCryptoException("Vault not found for namespace: " + namespace);
            }

            String expectedActiveKid = doc.getActiveKid();
            int expectedVersion = doc.getV();

            int maxVersion = 0;
            for (KeyVersionEntry entry : doc.getKeys()) {
                if ("ACTIVE".equals(entry.getStatus())) {
                    entry.setStatus("ROTATED");
                }
                int ver = parseVersion(entry.getKid());
                if (ver > maxVersion) maxVersion = ver;
            }

            String newKid = generateKid(maxVersion + 1);
            KeyVersionEntry newEntry = createKeyEntry(newKid);

            doc.getKeys().add(newEntry);
            doc.setActiveKid(newKid);
            doc.setV(expectedVersion + 1);
            doc.setUpdatedAt(Instant.now());

            persistRotatedVault(doc, vaultId, expectedActiveKid, expectedVersion);

            verifyAndLoadKeys(doc, namespace);

            log.info("DEK rotated for namespace {}: new active kid = {}", namespace, newKid);
        }
    }

    // ===== Internal methods =====

    private void initForNamespace(String namespace) {
        String vaultId = VAULT_ID_PREFIX + namespace;

        KeyVaultDocument doc = loadVaultDocument(vaultId);
        if (doc == null) {
            doc = initializeVault(vaultId);
        }
        verifyAndLoadKeys(doc, namespace);
    }

    private KeyVaultDocument loadVaultDocument(String vaultId) {
        Query query = new Query(Criteria.where("_id").is(vaultId));
        return mongoTemplate.findOne(query, KeyVaultDocument.class);
    }

    private void persistRotatedVault(KeyVaultDocument doc, String vaultId, String expectedActiveKid, int expectedVersion) {
        Document replacement = new Document();
        mongoTemplate.getConverter().write(doc, replacement);

        Document filter = new Document("_id", vaultId)
                .append("activeKid", expectedActiveKid)
                .append("v", expectedVersion);

        UpdateResult result = mongoTemplate.getDb()
                .getCollection(KEY_VAULT_COLLECTION)
                .replaceOne(filter, replacement);

        if (result.getMatchedCount() == 0) {
            throw new FatalCryptoException(
                    "Concurrent vault rotation detected for vault: " + vaultId + ". Please retry.");
        }
    }

    private KeyVaultDocument initializeVault(String vaultId) {
        log.info("Initializing key vault: {}", vaultId);

        String kid = generateKid(1);
        KeyVersionEntry entry = createKeyEntry(kid);

        KeyVaultDocument doc = new KeyVaultDocument();
        doc.setId(vaultId);
        doc.setV(1);
        doc.setStatus("ACTIVE");
        doc.setActiveKid(kid);
        doc.setKeys(new ArrayList<>(List.of(entry)));

        CmkInfo cmkInfo = new CmkInfo();
        cmkInfo.setProvider(cmkProvider.getProviderId());
        cmkInfo.setId(cmkProvider.getPublicReference());
        doc.setCmk(cmkInfo);

        Instant now = Instant.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);

        try {
            mongoTemplate.insert(doc);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.warn("Vault document already exists (concurrent init), loading instead.");
            return loadVaultDocument(vaultId);
        }
        return doc;
    }

    private KeyVersionEntry createKeyEntry(String kid) {
        GeneratedKey dekPair = cmkProvider.generateKey(KEY_LENGTH);
        byte[] rawDek = dekPair.rawKey();
        WrappedKey wrappedDek = dekPair.wrappedKey();

        GeneratedKey hmacPair = cmkProvider.generateKey(KEY_LENGTH);
        byte[] rawHmac = hmacPair.rawKey();
        WrappedKey wrappedHmac = hmacPair.wrappedKey();

        String dekKcv = KeyCheckValue.computeDekKcv(rawDek, KCV_ALGORITHM);
        String hmacKcv = KeyCheckValue.computeHmacKcv(rawHmac);
        String binding = KeyCheckValue.computeBinding(rawHmac, rawDek);

        WrappedKeyInfo dekInfo = new WrappedKeyInfo();
        dekInfo.setWrapped(wrappedDek.ciphertext());
        dekInfo.setAlgorithm(wrappedDek.algorithm());
        dekInfo.setCmkVersion(wrappedDek.metadata().get(CmkProvider.META_CMK_VERSION));
        dekInfo.setKcv(dekKcv);

        WrappedKeyInfo hmacInfo = new WrappedKeyInfo();
        hmacInfo.setWrapped(wrappedHmac.ciphertext());
        hmacInfo.setAlgorithm(wrappedHmac.algorithm());
        hmacInfo.setCmkVersion(wrappedHmac.metadata().get(CmkProvider.META_CMK_VERSION));
        hmacInfo.setKcv(hmacKcv);

        KeyVersionEntry entry = new KeyVersionEntry();
        entry.setKid(kid);
        entry.setStatus("ACTIVE");
        entry.setDek(dekInfo);
        entry.setHmk(hmacInfo);
        entry.setBinding(binding);
        entry.setCreatedAt(Instant.now());

        return entry;
    }

    private void verifyAndLoadKeys(KeyVaultDocument doc, String namespace) {
        try {
            if (doc.getKeys() == null || doc.getKeys().isEmpty()) {
                throw new FatalCryptoException("Vault has no key entries: " + doc.getId());
            }

            Map<String, ResolvedKeyPair> resolvedKeys = new HashMap<>();
            Map<Integer, ResolvedKeyPair> resolvedKeysByVersion = new HashMap<>();
            String activeKid = null;
            int activeDekVersion = 0;
            int activeCount = 0;

            for (KeyVersionEntry entry : doc.getKeys()) {
                String dekCmkVersion = entry.getDek().getCmkVersion();
                byte[] unwrappedDek = cmkProvider.unwrap(
                        (dekCmkVersion == null || dekCmkVersion.isEmpty()) ? new WrappedKey(entry.getDek().getWrapped(), entry.getDek().getAlgorithm()) : new WrappedKey(entry.getDek().getWrapped(), entry.getDek().getAlgorithm(), Map.of(CmkProvider.META_CMK_VERSION, dekCmkVersion)));

                String hmacCmkVersion = entry.getHmk().getCmkVersion();
                byte[] unwrappedHmac = cmkProvider.unwrap(
                        (hmacCmkVersion == null || hmacCmkVersion.isEmpty()) ? new WrappedKey(entry.getHmk().getWrapped(), entry.getHmk().getAlgorithm()) : new WrappedKey(entry.getHmk().getWrapped(), entry.getHmk().getAlgorithm(), Map.of(CmkProvider.META_CMK_VERSION, hmacCmkVersion)));

                // KCV verification
                String expectedDekKcv = KeyCheckValue.computeDekKcv(unwrappedDek, KCV_ALGORITHM);
                if (!expectedDekKcv.equals(entry.getDek().getKcv())) {
                    throw new FatalCryptoException(
                            "DEK KCV mismatch for kid " + entry.getKid() + "! Vault integrity compromised.");
                }
                String expectedHmacKcv = KeyCheckValue.computeHmacKcv(unwrappedHmac);
                if (!expectedHmacKcv.equals(entry.getHmk().getKcv())) {
                    throw new FatalCryptoException(
                            "HMAC Key KCV mismatch for kid " + entry.getKid() + "! Vault integrity compromised.");
                }

                // Binding verification
                String expectedBinding = KeyCheckValue.computeBinding(unwrappedHmac, unwrappedDek);
                if (!expectedBinding.equals(entry.getBinding())) {
                    throw new FatalCryptoException(
                            "Key binding mismatch for kid " + entry.getKid() + "! DEK/HMAC key pair corrupted.");
                }

                ResolvedKeyPair pair = new ResolvedKeyPair(unwrappedDek, unwrappedHmac);
                resolvedKeys.put(entry.getKid(), pair);

                int version = parseVersion(entry.getKid());
                resolvedKeysByVersion.put(version, pair);

                if ("ACTIVE".equals(entry.getStatus())) {
                    activeKid = entry.getKid();
                    activeDekVersion = version;
                    activeCount++;
                }
            }

            if (activeCount == 0) {
                throw new FatalCryptoException("Vault has no ACTIVE key entry: " + doc.getId());
            }
            if (activeCount > 1) {
                throw new FatalCryptoException("Vault has multiple ACTIVE key entries: " + doc.getId());
            }

            NamespaceKeyContext ctx = new NamespaceKeyContext(activeKid, activeDekVersion, resolvedKeys, resolvedKeysByVersion, computeExpiresAt());
            if (!cacheTtl.isZero()) {
                namespaceKeyContexts.put(namespace, ctx);
            }

            log.info("Key vault loaded and verified: {} (active kid: {}, version: {})", doc.getId(), activeKid, activeDekVersion);
        } catch (FatalCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new FatalCryptoException("Failed to verify key vault: " + doc.getId(), e);
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
