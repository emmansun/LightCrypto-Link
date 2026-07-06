package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.Binary;

import java.util.List;

/**
 * Manual decryption service — allows users to decrypt @Encrypted fields
 * in a raw BSON Document obtained outside the transparent converter path
 * (e.g. aggregation pipelines, native driver queries, data migration scripts).
 * <p>
 * Encapsulates sub-document format parsing (_e/_k/_a/_t/c), DEK lookup by kid,
 * multi-algorithm dispatch, and type deserialization.
 */
@Slf4j
public class FieldCryptoService {

    private final EntityMetadataCache metadataCache;
    private final CryptoCodec cryptoCodec;
    private final TypeDeserializer typeDeserializer;
    private final KeyVaultService keyVaultService;

    public FieldCryptoService(EntityMetadataCache metadataCache,
                              CryptoCodec cryptoCodec,
                              TypeDeserializer typeDeserializer,
                              KeyVaultService keyVaultService) {
        this.metadataCache = metadataCache;
        this.cryptoCodec = cryptoCodec;
        this.typeDeserializer = typeDeserializer;
        this.keyVaultService = keyVaultService;
    }

    /**
     * Decrypt all @Encrypted fields in the given raw Document for the specified entity class.
     * Modifies the Document in-place and returns the same reference.
     *
     * @param rawDocument the raw BSON Document containing encrypted sub-documents
     * @param entityClass the entity class whose @Encrypted fields should be decrypted
     * @return the same Document instance with encrypted sub-documents replaced by plaintext values
     * @throws IllegalArgumentException if rawDocument or entityClass is null
     * @throws FatalCryptoException     if an encrypted sub-document is missing the required _k (kid) field
     */
    public Document decryptDocument(Document rawDocument, Class<?> entityClass) {
        if (rawDocument == null) {
            throw new IllegalArgumentException("rawDocument must not be null");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }

        List<EncryptedFieldMetadata> fields = metadataCache.getEncryptedFields(entityClass);
        if (fields.isEmpty()) {
            return rawDocument;
        }

        for (EncryptedFieldMetadata meta : fields) {
            decryptField(rawDocument, meta);
        }
        return rawDocument;
    }

    private void decryptField(Document document, EncryptedFieldMetadata meta) {
        Object raw = document.get(meta.fieldName());
        if (!(raw instanceof Document subDoc)) {
            return;
        }

        Integer eMarker = subDoc.getInteger("_e");
        if (eMarker == null || eMarker != 1) {
            return;
        }

        String typeMarker = subDoc.getString("_t");
        Binary cipherBinary = subDoc.get("c", Binary.class);
        if (cipherBinary == null) {
            return;
        }

        // Read kid from sub-document
        String kid = subDoc.getString("_k");
        if (kid == null) {
            throw new FatalCryptoException(
                    "Encrypted sub-document for field '" + meta.fieldName() +
                            "' is missing '_k' (kid) field. Incompatible with multi-DEK format.");
        }

        // Read algorithm from sub-document, default to AES_256_GCM for backward compatibility
        String algorithmName = subDoc.getString("_a");
        SymmetricAlgorithm algorithm = algorithmName != null
                ? SymmetricAlgorithm.valueOf(algorithmName)
                : SymmetricAlgorithm.AES_256_GCM;

        // Decrypt using kid-specific DEK and algorithm
        byte[] dek = keyVaultService.getDek(kid);
        byte[] plaintext = cryptoCodec.decrypt(dek, cipherBinary.getData(), algorithm);

        // Deserialize
        Object value = typeDeserializer.deserialize(typeMarker, plaintext);

        // Replace encrypted sub-document with original value
        document.put(meta.fieldName(), value);

        log.debug("Decrypted field '{}' using kid {} and algorithm {}",
                meta.fieldName(), kid, algorithm);
    }
}
