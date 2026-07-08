package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key Vault service — manages per-entity-class DEK and HMAC keys stored in
 * the __lcl_keyvault collection.
 * <p>
 * Each entity class with @Encrypted fields gets its own vault document
 * (_id = lcl-dek-{entitySimpleName}). Each vault supports key versioning
 * via a keys[] array with kid-based lookup.
 * </p>
 */
@Slf4j
public class KeyVaultService {

    private static final String KEY_VAULT_COLLECTION = "__lcl_keyvault";
    private static final int KEY_LENGTH = 32;
    private static final String VAULT_ID_PREFIX = "lcl-dek-";

    private final MongoTemplate mongoTemplate;
    private final CmkProvider cmkProvider;
    private final CryptoProperties properties;
    private final CryptoCodec cryptoCodec;

    /** Per-entity-class key contexts: entityClassName -> EntityKeyContext. */
    private final ConcurrentHashMap<String, EntityKeyContext> entityKeyContexts = new ConcurrentHashMap<>();

    public KeyVaultService(MongoTemplate mongoTemplate, CmkProvider cmkProvider,
                           CryptoProperties properties, CryptoCodec cryptoCodec) {
        this.mongoTemplate = mongoTemplate;
        this.cmkProvider = cmkProvider;
        this.properties = properties;
        this.cryptoCodec = cryptoCodec;
    }

    /**
     * Ensure vault is initialized for the given entity class. Lazily initializes
     * if the vault does not yet exist.
     */
    public void ensureVaultInitialized(Class<?> entityClass) {
        String className = entityClass.getSimpleName();
        if (entityKeyContexts.containsKey(className)) return;
        synchronized (this) {
            if (entityKeyContexts.containsKey(className)) return;
            initForEntity(entityClass);
        }
    }

    /**
     * Get the active kid for the given entity class.
     */
    public String getActiveKid(Class<?> entityClass) {
        String className = entityClass.getSimpleName();
        EntityKeyContext ctx = entityKeyContexts.get(className);
        if (ctx == null) {
            throw new FatalCryptoException(
                    "Vault not initialized for entity: " + className +
                            ". Call ensureVaultInitialized() first.");
        }
        return ctx.activeKid;
    }

    /**
     * Get the unwrapped DEK for the given kid.
     */
    public byte[] getDek(String kid) {
        for (EntityKeyContext ctx : entityKeyContexts.values()) {
            ResolvedKeyPair pair = ctx.resolvedKeys.get(kid);
            if (pair != null) return pair.dek;
        }
        throw new FatalCryptoException("Unknown kid: " + kid);
    }

    /**
     * Get the unwrapped HMAC key for the given kid.
     */
    public byte[] getHmacKey(String kid) {
        for (EntityKeyContext ctx : entityKeyContexts.values()) {
            ResolvedKeyPair pair = ctx.resolvedKeys.get(kid);
            if (pair != null) return pair.hmacKey;
        }
        throw new FatalCryptoException("Unknown kid: " + kid);
    }

    /**
     * Rotate the DEK (Data Encryption Key) for the given entity class: marks the current
     * active key as ROTATED, generates a new DEK + HMAC key pair with incremented version,
     * updates activeKid, and persists to MongoDB.
     * <p>
     * Note: This only rotates the DEK, NOT the CMK. The new DEK is still wrapped
     * by the same CMK.
     * </p>
     */
    public void rotateDek(Class<?> entityClass) {
        String className = entityClass.getSimpleName();
        synchronized (this) {
            String vaultId = VAULT_ID_PREFIX + className;

            KeyVaultDocument doc = loadVaultDocument(vaultId);
            if (doc == null) {
                throw new FatalCryptoException("Vault not found for entity: " + className);
            }

            String expectedActiveKid = doc.getActiveKid();
            int expectedVersion = doc.getV();

            // Find current active entry and determine next version number
            int maxVersion = 0;
            for (KeyVersionEntry entry : doc.getKeys()) {
                if ("ACTIVE".equals(entry.getStatus())) {
                    entry.setStatus("ROTATED");
                }
                int ver = parseVersion(entry.getKid());
                if (ver > maxVersion) maxVersion = ver;
            }

            // Generate new key entry
            String newKid = generateKid(maxVersion + 1);
            KeyVersionEntry newEntry = createKeyEntry(newKid);

            doc.getKeys().add(newEntry);
            doc.setActiveKid(newKid);
            doc.setV(expectedVersion + 1);
            doc.setUpdatedAt(Instant.now());

            persistRotatedVault(doc, vaultId, expectedActiveKid, expectedVersion);

            // Reload into cache
            verifyAndLoadKeys(doc, className);

            log.info("DEK rotated for entity {}: new active kid = {}", className, newKid);
        }
    }

    // ===== Internal methods =====

    private void initForEntity(Class<?> entityClass) {
        String className = entityClass.getSimpleName();
        String vaultId = VAULT_ID_PREFIX + className;

        KeyVaultDocument doc = loadVaultDocument(vaultId);
        if (doc == null) {
            doc = initializeVault(vaultId);
        }
        verifyAndLoadKeys(doc, className);
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
                    "Concurrent vault rotation detected for entity vault: " + vaultId + ". Please retry.");
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
            // Concurrent initialization scenario: fall back to loading
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

        String dekKcv = cryptoCodec.computeKcv(rawDek);
        String hmacKcv = cryptoCodec.computeKcv(rawHmac);
        String binding = cryptoCodec.computeBinding(rawHmac, rawDek);

        WrappedKeyInfo dekInfo = new WrappedKeyInfo();
        dekInfo.setWrapped(wrappedDek.ciphertext());
        dekInfo.setAlgorithm(wrappedDek.algorithm());
        dekInfo.setKcv(dekKcv);

        WrappedKeyInfo hmacInfo = new WrappedKeyInfo();
        hmacInfo.setWrapped(wrappedHmac.ciphertext());
        hmacInfo.setAlgorithm(wrappedHmac.algorithm());
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

    private void verifyAndLoadKeys(KeyVaultDocument doc, String entityClassName) {
        try {
            if (doc.getKeys() == null || doc.getKeys().isEmpty()) {
                throw new FatalCryptoException("Vault has no key entries: " + doc.getId());
            }

            Map<String, ResolvedKeyPair> resolvedKeys = new HashMap<>();
            String activeKid = null;
            int activeCount = 0;

            for (KeyVersionEntry entry : doc.getKeys()) {
                byte[] unwrappedDek = cmkProvider.unwrap(
                        new WrappedKey(entry.getDek().getWrapped(), entry.getDek().getAlgorithm()));
                byte[] unwrappedHmac = cmkProvider.unwrap(
                        new WrappedKey(entry.getHmk().getWrapped(), entry.getHmk().getAlgorithm()));

                // KCV verification
                String expectedDekKcv = cryptoCodec.computeKcv(unwrappedDek);
                if (!expectedDekKcv.equals(entry.getDek().getKcv())) {
                    throw new FatalCryptoException(
                            "DEK KCV mismatch for kid " + entry.getKid() + "! Vault integrity compromised.");
                }
                String expectedHmacKcv = cryptoCodec.computeKcv(unwrappedHmac);
                if (!expectedHmacKcv.equals(entry.getHmk().getKcv())) {
                    throw new FatalCryptoException(
                            "HMAC Key KCV mismatch for kid " + entry.getKid() + "! Vault integrity compromised.");
                }

                // Binding verification
                String expectedBinding = cryptoCodec.computeBinding(unwrappedHmac, unwrappedDek);
                if (!expectedBinding.equals(entry.getBinding())) {
                    throw new FatalCryptoException(
                            "Key binding mismatch for kid " + entry.getKid() + "! DEK/HMAC key pair corrupted.");
                }

                resolvedKeys.put(entry.getKid(), new ResolvedKeyPair(unwrappedDek, unwrappedHmac));

                if ("ACTIVE".equals(entry.getStatus())) {
                    activeKid = entry.getKid();
                    activeCount++;
                }
            }

            if (activeCount == 0) {
                throw new FatalCryptoException("Vault has no ACTIVE key entry: " + doc.getId());
            }
            if (activeCount > 1) {
                throw new FatalCryptoException("Vault has multiple ACTIVE key entries: " + doc.getId());
            }

            EntityKeyContext ctx = new EntityKeyContext(activeKid, resolvedKeys);
            entityKeyContexts.put(entityClassName, ctx);

            log.info("Key vault loaded and verified: {} (active kid: {})", doc.getId(), activeKid);
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

    /** Per-entity-class key context with active kid and all resolved key pairs. */
    static class EntityKeyContext {
        final String activeKid;
        final Map<String, ResolvedKeyPair> resolvedKeys;

        EntityKeyContext(String activeKid, Map<String, ResolvedKeyPair> resolvedKeys) {
            this.activeKid = activeKid;
            this.resolvedKeys = resolvedKeys;
        }
    }
}
