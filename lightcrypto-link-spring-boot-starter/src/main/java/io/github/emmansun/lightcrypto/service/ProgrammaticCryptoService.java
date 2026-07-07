package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.bson.types.Binary;

/**
 * Programmatic crypto API for encrypt/decrypt operations outside annotation-driven persistence flow.
 *
 * <p>This service is intended for scenarios like DTO/message payload protection,
 * native query/aggregation results, migration scripts, and dynamic business rules
 * where annotation-based lifecycle encryption is not suitable.
 */
public class ProgrammaticCryptoService {

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();

    private final CryptoCodec cryptoCodec;
    private final TypeSerializer typeSerializer;
    private final TypeDeserializer typeDeserializer;
    private final KeyVaultService keyVaultService;
    private final FieldCryptoService fieldCryptoService;

    public ProgrammaticCryptoService(CryptoCodec cryptoCodec,
                                     TypeSerializer typeSerializer,
                                     TypeDeserializer typeDeserializer,
                                     KeyVaultService keyVaultService,
                                     FieldCryptoService fieldCryptoService) {
        this.cryptoCodec = cryptoCodec;
        this.typeSerializer = typeSerializer;
        this.typeDeserializer = typeDeserializer;
        this.keyVaultService = keyVaultService;
        this.fieldCryptoService = fieldCryptoService;
    }

    /**
     * Encrypt a single scalar value using the active DEK of the provided key scope class.
     * Uses {@link SymmetricAlgorithm#AES_256_GCM} as the default algorithm.
     *
     * @param value         plaintext value to encrypt (must be serializer-supported type)
     * @param keyScopeClass entity class used to resolve active key scope and DEK
     * @return encrypted sub-document in canonical format (_e/_k/_a/_t/c)
     */
    public Document encryptValue(Object value, Class<?> keyScopeClass) {
        return encryptValue(value, keyScopeClass, SymmetricAlgorithm.AES_256_GCM);
    }

    /**
     * Encrypt a single scalar value using the active DEK of the provided key scope class.
     *
     * @param value         plaintext value to encrypt (must be serializer-supported type)
     * @param keyScopeClass entity class used to resolve active key scope and DEK
     * @param algorithm     symmetric algorithm for encryption
     * @return encrypted sub-document in canonical format (_e/_k/_a/_t/c)
     */
    public Document encryptValue(Object value, Class<?> keyScopeClass, SymmetricAlgorithm algorithm) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (keyScopeClass == null) {
            throw new IllegalArgumentException("keyScopeClass must not be null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }

        keyVaultService.ensureVaultInitialized(keyScopeClass);
        String kid = keyVaultService.getActiveKid(keyScopeClass);

        byte[] dek;
        try {
            dek = keyVaultService.getDek(kid);
        } catch (RuntimeException ex) {
            throw new KeyManagementException("Failed to resolve DEK for key scope '"
                    + keyScopeClass.getSimpleName() + "' (kid=" + maskKid(kid) + ")", ex);
        }

        String typeMarker = TypeSerializer.resolveTypeMarker(value.getClass());
        byte[] serialized = typeSerializer.serialize(value);
        byte[] encrypted = cryptoCodec.encrypt(dek, serialized, algorithm);

        Document subDoc = new Document();
        subDoc.put("c", new Binary(encrypted));
        subDoc.put("_e", 1);
        subDoc.put("_t", typeMarker);
        subDoc.put("_k", kid);
        subDoc.put("_a", algorithm.name());
        return subDoc;
    }

    /**
     * Decrypt one encrypted sub-document produced by LCL and return the plaintext object.
     *
     * @param encryptedSubDocument encrypted sub-document containing _e/_k/_a/_t/c
     * @return decrypted object (scalar, Document, or List depending on type marker)
     */
    public Object decryptValue(Document encryptedSubDocument) {
        if (encryptedSubDocument == null) {
            throw new IllegalArgumentException("encryptedSubDocument must not be null");
        }

        Integer eMarker = encryptedSubDocument.getInteger("_e");
        if (eMarker == null || eMarker != 1) {
            throw new DecryptionException("Document is not an encrypted sub-document (_e=1 missing)");
        }

        String kid = encryptedSubDocument.getString("_k");
        if (kid == null) {
            throw new DecryptionException("Encrypted sub-document is missing '_k' (kid) field");
        }

        String typeMarker = encryptedSubDocument.getString("_t");
        if (typeMarker == null) {
            throw new DecryptionException("Encrypted sub-document is missing '_t' (type marker) field");
        }

        Binary cipherBinary = encryptedSubDocument.get("c", Binary.class);
        if (cipherBinary == null) {
            throw new DecryptionException("Encrypted sub-document is missing 'c' (ciphertext) field");
        }

        SymmetricAlgorithm algorithm = resolveAlgorithm(encryptedSubDocument.getString("_a"));

        byte[] dek;
        try {
            dek = keyVaultService.getDek(kid);
        } catch (RuntimeException ex) {
            throw new KeyManagementException("Failed to resolve DEK for kid=" + maskKid(kid), ex);
        }

        byte[] plaintext;
        try {
            plaintext = cryptoCodec.decrypt(dek, cipherBinary.getData(), algorithm);
        } catch (RuntimeException ex) {
            throw new DecryptionException("Failed to decrypt value with algorithm " + algorithm
                    + " (kid=" + maskKid(kid) + ")", ex);
        }

        if ("DOC".equals(typeMarker) || "COL".equals(typeMarker) || "MAP".equals(typeMarker)) {
            return decodeStructuredValue(typeMarker, plaintext);
        }

        try {
            return typeDeserializer.deserialize(typeMarker, plaintext);
        } catch (RuntimeException ex) {
            throw new DecryptionException("Failed to deserialize decrypted value with type marker '"
                    + typeMarker + "'", ex);
        }
    }

    /**
     * Decrypt all annotated encrypted fields in a raw document for the given entity class.
     * This delegates to {@link FieldCryptoService} and mutates the input document in-place.
     *
     * @param rawDocument raw BSON document read outside converter/repository flow
     * @param entityClass entity class metadata used to locate encrypted fields
     * @return the same document reference with decrypted field values
     */
    public Document decryptDocument(Document rawDocument, Class<?> entityClass) {
        return fieldCryptoService.decryptDocument(rawDocument, entityClass);
    }

    private SymmetricAlgorithm resolveAlgorithm(String algorithmName) {
        if (algorithmName == null) {
            return SymmetricAlgorithm.AES_256_GCM;
        }
        try {
            return SymmetricAlgorithm.valueOf(algorithmName);
        } catch (IllegalArgumentException ex) {
            throw new DecryptionException("Unsupported algorithm '" + algorithmName + "'", ex);
        }
    }

    private Object decodeStructuredValue(String typeMarker, byte[] plaintext) {
        Document payload;
        try {
            payload = new RawBsonDocument(plaintext).decode(DOCUMENT_CODEC);
        } catch (RuntimeException ex) {
            throw new DecryptionException("Failed to decode structured payload for type marker: " + typeMarker, ex);
        }

        return switch (typeMarker) {
            case "DOC", "MAP" -> payload;
            case "COL" -> payload.getList("_v", Object.class);
            default -> throw new DecryptionException("Unsupported structured type marker: " + typeMarker);
        };
    }

    private String maskKid(String kid) {
        if (kid == null || kid.isEmpty()) {
            return "N/A";
        }
        if (kid.length() <= 4) {
            return "****";
        }
        return kid.substring(0, 4) + "****";
    }
}
