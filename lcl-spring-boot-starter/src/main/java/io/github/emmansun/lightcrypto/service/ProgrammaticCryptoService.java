package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.format.WireFormatDecoder;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import io.github.emmansun.lightcrypto.spi.StructuredValueCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Programmatic crypto API for encrypt/decrypt operations outside annotation-driven persistence flow.
 *
 * <p>This service is intended for scenarios like DTO/message payload protection,
 * native query/aggregation results, migration scripts, and dynamic business rules
 * where annotation-based lifecycle encryption is not suitable.
 * <p>
 * Uses Wire Format V1: encrypted values are Base64URL-encoded self-describing blobs.
 */
public class ProgrammaticCryptoService {

    private final TypeSerializer typeSerializer;
    private final TypeDeserializer typeDeserializer;
    private final KeyVaultService keyVaultService;
    private final FieldCryptoService fieldCryptoService;
    private final StructuredValueCodec structuredValueCodec;

    public ProgrammaticCryptoService(TypeSerializer typeSerializer,
                                     TypeDeserializer typeDeserializer,
                                     KeyVaultService keyVaultService,
                                     FieldCryptoService fieldCryptoService,
                                     StructuredValueCodec structuredValueCodec) {
        this.typeSerializer = typeSerializer;
        this.typeDeserializer = typeDeserializer;
        this.keyVaultService = keyVaultService;
        this.fieldCryptoService = fieldCryptoService;
        this.structuredValueCodec = structuredValueCodec;
    }

    /**
     * Encrypt a single scalar value using the active DEK of the provided namespace.
     * Uses AES-256-GCM as the default algorithm.
     *
     * @param value     plaintext value to encrypt
     * @param namespace the namespace for key resolution and AAD binding
     * @return encrypted sub-document in Wire Format V1 canonical format (_e/_t/c)
     */
    public Object encryptValue(Object value, String namespace) {
        return encryptValue(value, namespace, AlgorithmId.AES_256_GCM);
    }

    /**
     * Encrypt a single scalar value using the active DEK of the provided namespace.
     *
     * @param value     plaintext value to encrypt
     * @param namespace the namespace for key resolution and AAD binding
     * @param algorithm symmetric algorithm for encryption
     * @return encrypted sub-document in Wire Format V1 canonical format (_e/_t/c)
     */
    public Object encryptValue(Object value, String namespace, AlgorithmId algorithm) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (namespace == null) {
            throw new IllegalArgumentException("namespace must not be null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }

        Namespace ns = Namespace.parse(namespace);
        String canonicalNs = ns.canonical();
        keyVaultService.ensureVaultInitialized(canonicalNs);
        int dekVersion = keyVaultService.getActiveDekVersion(canonicalNs);

        byte[] dek;
        try {
            dek = keyVaultService.getDek(keyVaultService.getActiveKid(canonicalNs));
        } catch (RuntimeException ex) {
            throw new KeyManagementException("Failed to resolve DEK for namespace '"
                    + canonicalNs + "'", ex);
        }

        String typeMarker = TypeSerializer.resolveTypeMarker(value.getClass());
        byte[] serialized = typeSerializer.serialize(value);
        String blob = CryptoCodec.encrypt(dek, serialized, algorithm, ns, dekVersion);

        Map<String, Object> subDoc = new LinkedHashMap<>();
        subDoc.put("c", blob);
        subDoc.put("_e", 1);
        subDoc.put("_t", typeMarker);
        return subDoc;
    }

    /**
     * Encrypt a single scalar value using a key scope class (constructs namespace automatically).
     */
    public Object encryptValue(Object value, Class<?> keyScopeClass) {
        String namespace = "default.default." + keyScopeClass.getSimpleName() + "#_default";
        return encryptValue(value, namespace);
    }

    /**
     * Decrypt one encrypted sub-document produced by LCL and return the plaintext object.
     *
     * @param encryptedSubDocument encrypted sub-document containing _e/_t/c
     * @return decrypted object (scalar, Document, or List depending on type marker)
     */
    public Object decryptValue(Object encryptedSubDocument) {
        if (encryptedSubDocument == null) {
            throw new IllegalArgumentException("encryptedSubDocument must not be null");
        }
        if (!(encryptedSubDocument instanceof Map<?, ?> subDoc)) {
            throw new DecryptionException("encryptedSubDocument must be a Map-like object");
        }

        Object eMarkerObj = subDoc.get("_e");
        if (!(eMarkerObj instanceof Integer eMarker) || eMarker != 1) {
            throw new DecryptionException("Document is not an encrypted sub-document (_e=1 missing)");
        }

        Object typeMarkerObj = subDoc.get("_t");
        if (!(typeMarkerObj instanceof String typeMarker)) {
            throw new DecryptionException("Encrypted sub-document is missing '_t' (type marker) field");
        }

        Object blobObj = subDoc.get("c");
        if (!(blobObj instanceof String blob)) {
            throw new DecryptionException("Encrypted sub-document is missing 'c' (ciphertext) field");
        }

        // Decode wire format to get namespace and dekVersion
        WireFormatDecoder.DecodedBlob decoded;
        try {
            decoded = WireFormatDecoder.decodeFromBase64Url(blob);
        } catch (IllegalArgumentException ex) {
            throw new DecryptionException("Invalid Wire Format blob", ex);
        }

        String namespace = decoded.namespace();
        int dekVersion = decoded.dekVersion();

        byte[] dek;
        try {
            keyVaultService.ensureVaultInitialized(namespace);
            dek = keyVaultService.getDekByVersion(namespace, dekVersion);
        } catch (RuntimeException ex) {
            throw new KeyManagementException("Failed to resolve DEK for namespace="
                    + namespace + ", dekVersion=" + dekVersion, ex);
        }

        byte[] plaintext;
        try {
            plaintext = CryptoCodec.decrypt(dek, blob);
        } catch (RuntimeException ex) {
            throw new DecryptionException("Failed to decrypt value (namespace="
                    + namespace + ", dekVersion=" + dekVersion + ")", ex);
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
     */
    public Object decryptDocument(Object rawDocument, Class<?> entityClass) {
        return fieldCryptoService.decryptDocument(rawDocument, entityClass);
    }

    private Object decodeStructuredValue(String typeMarker, byte[] plaintext) {
        try {
            return structuredValueCodec.decode(plaintext, typeMarker);
        } catch (RuntimeException ex) {
            throw new DecryptionException("Failed to decode structured payload for type marker: " + typeMarker, ex);
        }
    }
}
