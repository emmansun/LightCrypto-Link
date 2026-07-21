package io.github.emmansun.lightcrypto.spi;

/**
 * Abstraction for serialization/deserialization of structured values
 * (DOC, COL, MAP type markers) during encrypt/decrypt operations.
 *
 * <p>Implementations convert structured objects (nested documents, collections, maps)
 * to/from byte arrays suitable for encryption by the crypto codec.
 *
 * <p>Implementations MUST be stateless and thread-safe.
 *
 * @since 1.0.0
 */
public interface StructuredValueCodec {

    /**
     * Serialize a structured value to bytes for encryption.
     *
     * @param structuredValue the structured value (document, collection, or map)
     * @param typeMarker      the type marker indicating the structure type ("DOC", "COL", "MAP")
     * @return serialized byte array
     */
    byte[] encode(Object structuredValue, String typeMarker);

    /**
     * Deserialize decrypted bytes back to a structured value.
     *
     * @param data       the decrypted byte array
     * @param typeMarker the type marker indicating the structure type ("DOC", "COL", "MAP")
     * @return the deserialized structured value
     */
    Object decode(byte[] data, String typeMarker);
}
