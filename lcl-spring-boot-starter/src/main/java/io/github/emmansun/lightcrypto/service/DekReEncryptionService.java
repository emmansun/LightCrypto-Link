package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.EventTier;
import io.github.emmansun.lightcrypto.core.event.LclEvent;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.core.format.WireFormatDecoder;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.spi.DocumentRewriteStore;
import io.github.emmansun.lightcrypto.spi.RawDocument;
import io.github.emmansun.lightcrypto.spi.ScanOptions;
import io.github.emmansun.lightcrypto.spi.StorageAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * DEK re-encryption orchestration engine.
 * <p>
 * Scans all documents for a given entity class, decrypts fields encrypted under
 * old DEK versions, re-encrypts with the active DEK, recomputes blind index values
 * with the active HMAC key, and writes back atomically via CAS.
 * <p>
 * This is the heaviest key lifecycle operation — O(all documents) and may run for
 * hours on large collections. Checkpoint-based resumability allows recovery from
 * interruptions.
 *
 * @since 1.1.0
 */
@Slf4j
public class DekReEncryptionService {

    private final EntityMetadataCache metadataCache;
    private final KeyVaultService keyVaultService;
    private final StorageAdapter storageAdapter;
    private final DocumentRewriteStore rewriteStore;
    private final TypeSerializer typeSerializer;
    private final EventBus eventBus;

    public DekReEncryptionService(EntityMetadataCache metadataCache,
                                  KeyVaultService keyVaultService,
                                  StorageAdapter storageAdapter,
                                  DocumentRewriteStore rewriteStore,
                                  TypeSerializer typeSerializer) {
        this(metadataCache, keyVaultService, storageAdapter, rewriteStore, typeSerializer, NoOpEventBus.INSTANCE);
    }

    public DekReEncryptionService(EntityMetadataCache metadataCache,
                                  KeyVaultService keyVaultService,
                                  StorageAdapter storageAdapter,
                                  DocumentRewriteStore rewriteStore,
                                  TypeSerializer typeSerializer,
                                  EventBus eventBus) {
        this.metadataCache = metadataCache;
        this.keyVaultService = keyVaultService;
        this.storageAdapter = storageAdapter;
        this.rewriteStore = rewriteStore;
        this.typeSerializer = typeSerializer;
        this.eventBus = eventBus != null ? eventBus : NoOpEventBus.INSTANCE;
    }

    /**
     * Re-encrypts all documents of the given entity class under the active DEK.
     *
     * @param entityClass the entity class to re-encrypt
     * @param options     re-encryption options
     * @return the result of the operation
     */
    public ReEncryptResult reEncrypt(Class<?> entityClass, ReEncryptOptions options) {
        long startNanos = System.nanoTime();

        List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityClass);
        if (fields.isEmpty()) {
            long durationMicros = (System.nanoTime() - startNanos) / 1_000;
            return new ReEncryptResult("none", 0, 0, 0, 0, durationMicros);
        }

        // Collect unique namespaces and ensure vaults are initialized
        Set<String> namespaces = new LinkedHashSet<>();
        for (EncryptedFieldMetadata meta : fields) {
            String namespace = meta.namespace().canonical();
            namespaces.add(namespace);
            keyVaultService.ensureVaultInitialized(namespace);
        }

        String primaryNamespace = namespaces.iterator().next();
        String taskId = options.taskId() != null ? options.taskId()
                : "reencrypt-" + entityClass.getSimpleName() + "-" + UUID.randomUUID();

        // Build collection hint from entity class name (Spring Data convention: decapitalize first letter)
        String simpleName = entityClass.getSimpleName();
        String collectionHint = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);

        // Load checkpoint for resume
        Optional<String> checkpoint = rewriteStore.loadCheckpoint(taskId);
        String resumeAfter = checkpoint.orElse(null);

        ScanOptions scanOptions = new ScanOptions(
                collectionHint,
                options.batchSize(),
                resumeAfter,
                null
        );

        long docsProcessed = 0;
        long docsSkipped = 0;
        long docsFailed = 0;
        long fieldsReEncrypted = 0;
        int batchCount = 0;
        String lastDocId = null;

        try (DocumentRewriteStore.CloseableIterator<RawDocument> iterator = rewriteStore.scan(scanOptions)) {
            List<RawDocument> batch = new ArrayList<>(options.batchSize());

            while (iterator.hasNext()) {
                RawDocument doc = iterator.next();
                lastDocId = doc.id();

                try {
                    ReEncryptDocResult docResult = reEncryptDocument(doc, fields, options.dryRun());
                    if (docResult.replaced()) {
                        batch.add(doc);
                        fieldsReEncrypted += docResult.fieldsReEncrypted();
                    } else {
                        docsSkipped++;
                    }
                } catch (Exception e) {
                    log.error("Failed to re-encrypt document {}: {}", doc.id(), e.getMessage(), e);
                    docsFailed++;
                }

                // Flush batch when full
                if (batch.size() >= options.batchSize()) {
                    int replaced = flushBatch(batch, options.dryRun());
                    docsProcessed += replaced;
                    docsSkipped += (batch.size() - replaced);
                    batch.clear();
                    batchCount++;

                    // Checkpoint
                    if (batchCount % options.checkpointInterval() == 0 && lastDocId != null) {
                        rewriteStore.saveCheckpoint(taskId, lastDocId);
                    }

                    // Emit batch event
                    emitBatchEvent(primaryNamespace, docsProcessed, docsSkipped, docsFailed);
                }
            }

            // Flush remaining
            if (!batch.isEmpty()) {
                int replaced = flushBatch(batch, options.dryRun());
                docsProcessed += replaced;
                docsSkipped += (batch.size() - replaced);
                batchCount++;
            }

        } catch (Exception e) {
            log.error("Re-encryption scan failed for {}: {}", entityClass.getSimpleName(), e.getMessage(), e);
        }

        // Final checkpoint
        if (lastDocId != null) {
            rewriteStore.saveCheckpoint(taskId, lastDocId);
        }

        long durationMicros = (System.nanoTime() - startNanos) / 1_000;

        // Mark keys retired if all docs migrated
        if (docsFailed == 0 && !options.dryRun()) {
            markOldKeysRetired(namespaces);
        }

        // Emit completion event
        emitCompletionEvent(primaryNamespace, docsProcessed, docsSkipped, docsFailed, fieldsReEncrypted, durationMicros);

        return new ReEncryptResult(primaryNamespace, docsProcessed, docsSkipped, docsFailed, fieldsReEncrypted, durationMicros);
    }

    /**
     * Re-encrypts all registered entity classes.
     *
     * @param options re-encryption options (entityClass is ignored)
     * @return list of results for each entity class
     */
    public List<ReEncryptResult> reEncryptAll(ReEncryptOptions options) {
        // This requires EntityMetadataCache to expose registered classes
        // For now, this is a placeholder that would need adapter-specific implementation
        log.warn("reEncryptAll requires adapter-specific entity class discovery. " +
                "Use reEncrypt(Class, options) for each entity class instead.");
        return Collections.emptyList();
    }

    // ===== Internal methods =====

    private ReEncryptDocResult reEncryptDocument(RawDocument doc, List<EncryptedFieldMetadata> fields, boolean dryRun) {
        boolean anyFieldReEncrypted = false;
        int fieldsReEncrypted = 0;

        for (EncryptedFieldMetadata meta : fields) {
            String fieldName = meta.bsonFieldName();
            Object rawValue = doc.fields().get(fieldName);

            if (rawValue == null || !storageAdapter.isEncryptedPayload(rawValue)) {
                continue;
            }

            String blob = storageAdapter.extractBlob(rawValue);
            if (blob == null) {
                continue;
            }

            // Decode wire format to get dekVersion
            WireFormatDecoder.DecodedBlob decoded;
            try {
                decoded = WireFormatDecoder.decodeFromBase64Url(blob);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid wire format for field {} in doc {}: {}", fieldName, doc.id(), e.getMessage());
                continue;
            }

            String namespace = meta.namespace().canonical();
            int activeDekVersion = keyVaultService.getActiveDekVersion(namespace);

            // Skip if already at active version
            if (decoded.dekVersion() == activeDekVersion) {
                continue;
            }

            if (dryRun) {
                anyFieldReEncrypted = true;
                fieldsReEncrypted++;
                continue;
            }

            // Decrypt with old DEK
            byte[] oldDek = keyVaultService.getDekByVersion(namespace, decoded.dekVersion());
            byte[] plaintext = CryptoCodec.decrypt(oldDek, blob);

            // Re-encrypt with active DEK
            byte[] activeDek = keyVaultService.getDek(keyVaultService.getActiveKid(namespace));
            String newBlob = CryptoCodec.encrypt(activeDek, plaintext, meta.algorithmId(), meta.namespace(), activeDekVersion);

            // Recompute blind index if enabled
            String blindIndex = null;
            if (meta.blindIndex()) {
                byte[] activeHmacKey = keyVaultService.getActiveHmacKey(namespace);
                BlindIndexEngine engine = new BlindIndexEngine(activeHmacKey);
                String typeMarker = storageAdapter.extractTypeMarker(rawValue);
                // For blind index, we need the plaintext value - use type deserializer
                // This is simplified; full implementation would deserialize based on typeMarker
                blindIndex = recomputeBlindIndex(engine, meta, plaintext);
            }

            // Build new payload
            String typeMarker = storageAdapter.extractTypeMarker(rawValue);
            Object newPayload = storageAdapter.buildEncryptedPayload(newBlob, typeMarker, blindIndex);
            doc.fields().put(fieldName, newPayload);

            anyFieldReEncrypted = true;
            fieldsReEncrypted++;
        }

        return new ReEncryptDocResult(anyFieldReEncrypted, fieldsReEncrypted);
    }

    private String recomputeBlindIndex(BlindIndexEngine engine, EncryptedFieldMetadata meta, byte[] plaintext) {
        // For string-like types, compute directly; for others, use the raw bytes
        // This is a simplified implementation
        try {
            String value = new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
            return engine.computeBlindIndex(meta.namespace(), meta.blindIndexFieldName(), value);
        } catch (Exception e) {
            // Fallback to byte-based computation
            return engine.computeBlindIndex(meta.namespace(), meta.blindIndexFieldName(), plaintext);
        }
    }

    private int flushBatch(List<RawDocument> batch, boolean dryRun) {
        if (dryRun || batch.isEmpty()) {
            return batch.size();
        }
        return rewriteStore.replaceBatch(batch);
    }

    private void markOldKeysRetired(Set<String> namespaces) {
        for (String namespace : namespaces) {
            try {
                // Get all ROTATED kids for this namespace
                // This requires access to vault document - simplified here
                // Full implementation would query vault for ROTATED entries
                log.info("Re-encryption completed for namespace {}. Old keys can be marked RETIRED.", namespace);
            } catch (Exception e) {
                log.warn("Failed to mark keys retired for namespace {}: {}", namespace, e.getMessage());
            }
        }
    }

    private void emitBatchEvent(String namespace, long docsProcessed, long docsSkipped, long docsFailed) {
        eventBus.emit(LclEvent.builder()
                .event("lcl.reencrypt.batch.completed")
                .tier(EventTier.L2)
                .result("success")
                .namespace(namespace)
                .attribute("docsProcessed", String.valueOf(docsProcessed))
                .attribute("docsSkipped", String.valueOf(docsSkipped))
                .attribute("docsFailed", String.valueOf(docsFailed))
                .build());
    }

    private void emitCompletionEvent(String namespace, long docsProcessed, long docsSkipped,
                                     long docsFailed, long fieldsReEncrypted, long durationMicros) {
        eventBus.emit(LclEvent.builder()
                .event("lcl.reencrypt.namespace.completed")
                .tier(EventTier.L2)
                .result(docsFailed == 0 ? "success" : "partial")
                .namespace(namespace)
                .durationMicros(durationMicros)
                .attribute("docsProcessed", String.valueOf(docsProcessed))
                .attribute("docsSkipped", String.valueOf(docsSkipped))
                .attribute("docsFailed", String.valueOf(docsFailed))
                .attribute("fieldsReEncrypted", String.valueOf(fieldsReEncrypted))
                .build());
    }

    /** Internal result for single document re-encryption. */
    private record ReEncryptDocResult(boolean replaced, int fieldsReEncrypted) {
    }
}
