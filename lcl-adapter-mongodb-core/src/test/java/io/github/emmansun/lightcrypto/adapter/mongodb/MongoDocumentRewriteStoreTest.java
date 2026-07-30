package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.spi.RawDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MongoDocumentRewriteStore} CAS filter construction.
 */
class MongoDocumentRewriteStoreTest {

    private final MongoDocumentRewriteStore store = new MongoDocumentRewriteStore(null);

    @Test
    void buildCasFilterSingleField() {
        // Given: document with fieldKids containing one encrypted field
        Map<String, Object> fields = baseFields();
        Map<String, String> fieldKids = Map.of("phone", "v1-kid-abc");
        RawDocument doc = new RawDocument("doc1", fields, fieldKids);

        // When
        Document filter = store.buildCasFilter("raw-id-1", doc);

        // Then: dot-notation filter with _id + phone._k
        assertThat(filter.get("_id")).isEqualTo("raw-id-1");
        assertThat(filter.get("phone._k")).isEqualTo("v1-kid-abc");
        assertThat(filter.size()).isEqualTo(2);
    }

    @Test
    void buildCasFilterMultipleFields() {
        // Given: document with multiple encrypted fields
        Map<String, Object> fields = baseFields();
        Map<String, String> fieldKids = new LinkedHashMap<>();
        fieldKids.put("phone", "v1-kid");
        fieldKids.put("email", "v2-kid");
        RawDocument doc = new RawDocument("doc2", fields, fieldKids);

        // When
        Document filter = store.buildCasFilter("raw-id-2", doc);

        // Then: compound filter with all field kids
        assertThat(filter.get("_id")).isEqualTo("raw-id-2");
        assertThat(filter.get("phone._k")).isEqualTo("v1-kid");
        assertThat(filter.get("email._k")).isEqualTo("v2-kid");
        assertThat(filter.size()).isEqualTo(3);
    }

    @Test
    void buildCasFilterEmptyFieldKidsUsesIdOnly() {
        // Given: no fieldKids (legacy blob or no encrypted fields)
        Map<String, Object> fields = baseFields();
        RawDocument doc = new RawDocument("doc3", fields, Map.of());

        // When
        Document filter = store.buildCasFilter("raw-id-3", doc);

        // Then: _id-only filter (no CAS protection)
        assertThat(filter.get("_id")).isEqualTo("raw-id-3");
        assertThat(filter.size()).isEqualTo(1);
    }

    @Test
    void buildCasFilterNullFieldKidsUsesIdOnly() {
        // Given: null fieldKids (defensive)
        Map<String, Object> fields = baseFields();
        RawDocument doc = new RawDocument("doc4", fields, null);

        // When
        Document filter = store.buildCasFilter("raw-id-4", doc);

        // Then: _id-only filter
        assertThat(filter.get("_id")).isEqualTo("raw-id-4");
        assertThat(filter.size()).isEqualTo(1);
    }

    @Test
    void buildCasFilterPreservesKidOrder() {
        // Given: ordered fieldKids
        Map<String, Object> fields = baseFields();
        Map<String, String> fieldKids = new LinkedHashMap<>();
        fieldKids.put("address.city", "kid-a");
        fieldKids.put("phone", "kid-b");
        RawDocument doc = new RawDocument("doc5", fields, fieldKids);

        // When
        Document filter = store.buildCasFilter("raw-id-5", doc);

        // Then: nested path dot-notation works
        assertThat(filter.get("address.city._k")).isEqualTo("kid-a");
        assertThat(filter.get("phone._k")).isEqualTo("kid-b");
    }

    // ===== Helpers =====

    private Map<String, Object> baseFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("_id", "raw-id-1");
        fields.put("name", "test");
        fields.put("_lcl_collection", "testCollection");
        fields.put("_lcl_rawId", "raw-id-1");
        return fields;
    }
}
