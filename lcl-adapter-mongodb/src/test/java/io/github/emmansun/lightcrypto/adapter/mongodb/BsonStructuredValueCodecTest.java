package io.github.emmansun.lightcrypto.adapter.mongodb;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class BsonStructuredValueCodecTest {

    private final BsonStructuredValueCodec codec = new BsonStructuredValueCodec();

    @Test
    void encodeAndDecodeDoc() {
        Document doc = new Document();
        doc.put("name", "test");
        doc.put("value", 42);

        byte[] encoded = codec.encode(doc, "DOC");
        assertThat(encoded).isNotEmpty();

        Object decoded = codec.decode(encoded, "DOC");
        assertThat(decoded).isInstanceOf(Document.class);
        Document decodedDoc = (Document) decoded;
        assertThat(decodedDoc.getString("name")).isEqualTo("test");
        assertThat(decodedDoc.getInteger("value")).isEqualTo(42);
    }

    @Test
    void encodeAndDecodeCol() {
        List<Object> list = List.of("a", "b", "c");

        byte[] encoded = codec.encode(list, "COL");
        assertThat(encoded).isNotEmpty();

        Object decoded = codec.decode(encoded, "COL");
        assertThat(decoded).isInstanceOf(List.class);
        List<?> decodedList = (List<?>) decoded;
        assertThat(decodedList).hasSize(3);
        assertThat(decodedList.get(0)).isEqualTo("a");
        assertThat(decodedList.get(1)).isEqualTo("b");
        assertThat(decodedList.get(2)).isEqualTo("c");
    }

    @Test
    void encodeAndDecodeMap() {
        Document map = new Document();
        map.put("key1", "value1");
        map.put("key2", "value2");

        byte[] encoded = codec.encode(map, "MAP");
        assertThat(encoded).isNotEmpty();

        Object decoded = codec.decode(encoded, "MAP");
        assertThat(decoded).isInstanceOf(Document.class);
        Document decodedMap = (Document) decoded;
        assertThat(decodedMap.getString("key1")).isEqualTo("value1");
        assertThat(decodedMap.getString("key2")).isEqualTo("value2");
    }

    @Test
    void encodeUnsupportedTypeMarkerThrows() {
        Document doc = new Document("test", "value");
        assertThatThrownBy(() -> codec.encode(doc, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported structured type marker");
    }

    @Test
    void decodeUnsupportedTypeMarkerThrows() {
        Document doc = new Document("test", "value");
        byte[] encoded = codec.encode(doc, "DOC");
        assertThatThrownBy(() -> codec.decode(encoded, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported structured type marker");
    }
}
