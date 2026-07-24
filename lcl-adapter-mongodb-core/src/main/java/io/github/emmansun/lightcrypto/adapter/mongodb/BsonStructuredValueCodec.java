package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.spi.StructuredValueCodec;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

/**
 * BSON implementation of {@link StructuredValueCodec}.
 *
 * <p>Uses {@link DocumentCodec} + {@link RawBsonDocument} for encode/decode operations.
 * For encoding, structured values are serialized to BSON binary format.
 * For decoding, BSON binary is deserialized back to Document/List.
 *
 * @since 1.0.0
 */
public class BsonStructuredValueCodec implements StructuredValueCodec {

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();

    @Override
    public byte[] encode(Object structuredValue, String typeMarker) {
        Document payload = switch (typeMarker) {
            case "DOC", "MAP" -> (Document) structuredValue;
            case "COL" -> new Document("_v", structuredValue);
            default -> throw new IllegalArgumentException("Unsupported structured type marker: " + typeMarker);
        };

        try (BasicOutputBuffer buffer = new BasicOutputBuffer();
             BsonBinaryWriter writer = new BsonBinaryWriter(buffer)) {
            DOCUMENT_CODEC.encode(writer, payload, EncoderContext.builder().build());
            writer.flush();
            return buffer.toByteArray();
        }
    }

    @Override
    public Object decode(byte[] data, String typeMarker) {
        Document payload;
        try {
            payload = new RawBsonDocument(data).decode(DOCUMENT_CODEC);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Failed to decode structured payload for type marker: " + typeMarker, ex);
        }

        return switch (typeMarker) {
            case "DOC", "MAP" -> payload;
            case "COL" -> payload.getList("_v", Object.class);
            default -> throw new IllegalArgumentException("Unsupported structured type marker: " + typeMarker);
        };
    }
}
