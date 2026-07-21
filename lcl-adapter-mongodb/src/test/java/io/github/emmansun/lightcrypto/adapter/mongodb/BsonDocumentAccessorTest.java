package io.github.emmansun.lightcrypto.adapter.mongodb;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class BsonDocumentAccessorTest {

    private final BsonDocumentAccessor accessor = new BsonDocumentAccessor();

    @Test
    void getFieldReturnsValue() {
        Document doc = new Document();
        doc.put("phone", "13800138000");
        doc.put("age", 30);

        assertThat(accessor.getField(doc, "phone")).isEqualTo("13800138000");
        assertThat(accessor.getField(doc, "age")).isEqualTo(30);
    }

    @Test
    void getFieldReturnsNullForMissingField() {
        Document doc = new Document("phone", "13800138000");
        assertThat(accessor.getField(doc, "missing")).isNull();
    }

    @Test
    void getFieldReturnsNullForNullDocument() {
        assertThat(accessor.getField(null, "field")).isNull();
    }

    @Test
    void setFieldModifiesDocument() {
        Document doc = new Document();
        doc.put("phone", "old");

        accessor.setField(doc, "phone", "new");
        assertThat(doc.getString("phone")).isEqualTo("new");
    }

    @Test
    void setFieldAddsNewField() {
        Document doc = new Document();
        accessor.setField(doc, "name", "test");
        assertThat(doc.getString("name")).isEqualTo("test");
    }

    @Test
    void isDocumentLikeReturnsTrueForDocument() {
        Document doc = new Document("key", "value");
        assertThat(accessor.isDocumentLike(doc)).isTrue();
    }

    @Test
    void isDocumentLikeReturnsFalseForScalar() {
        assertThat(accessor.isDocumentLike("string")).isFalse();
        assertThat(accessor.isDocumentLike(123)).isFalse();
        assertThat(accessor.isDocumentLike(null)).isFalse();
    }

    @Test
    void asListReturnsListForListValue() {
        List<String> list = List.of("a", "b", "c");
        Iterable<?> result = accessor.asList(list);
        assertThat(result).isNotNull();
        int count = 0;
        for (Object item : result) {
            assertThat(item).isIn("a", "b", "c");
            count++;
        }
        assertThat(count).isEqualTo(3);
    }

    @Test
    void asListReturnsNullForNonList() {
        assertThat(accessor.asList("not-a-list")).isNull();
        assertThat(accessor.asList(new Document())).isNull();
    }

    @Test
    void asMapReturnsEntriesForDocument() {
        Document doc = new Document();
        doc.put("key1", "value1");
        doc.put("key2", "value2");

        Iterable<Map.Entry<String, Object>> result = accessor.asMap(doc);
        assertThat(result).isNotNull();

        int count = 0;
        for (Map.Entry<String, Object> entry : result) {
            count++;
            assertThat(entry.getKey()).isIn("key1", "key2");
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void asMapReturnsNullForNonDocument() {
        assertThat(accessor.asMap("not-a-document")).isNull();
        assertThat(accessor.asMap(List.of("a", "b"))).isNull();
    }
}
