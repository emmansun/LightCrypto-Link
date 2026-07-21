package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.testmodel.TestArticle;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CryptoMongoQueryCreatorTest extends MongoAdapterTestBase {
    private CryptoMongoQueryCreator qc;
    private BlindIndexEngine blindIndexEngine;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache(new CryptoProperties());
        TypeSerializer ser = createTestTypeSerializer();
        KeyVaultService vs = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        qc = new CryptoMongoQueryCreator(mc, ser, vs, new TestQueryTransformer(TEST_HMAC_KEY));
        blindIndexEngine = new BlindIndexEngine(TEST_HMAC_KEY);
    }

    @Test
    void findByPhoneProducesBlindIndexQuery() {
        Query q = new Query(Criteria.where("phone").is("13800138000"));
        Query r = qc.rewrite(q, TestUser.class);
        Document doc = r.getQueryObject();
        Namespace ns = Namespace.parse("default.default.TestUser#phone");
        String expected = blindIndexEngine.computeBlindIndex(ns, "phone", "13800138000".getBytes(StandardCharsets.UTF_8));
        assertThat(doc.getString("phone.b")).isEqualTo(expected);
        assertThat(doc.containsKey("phone")).isFalse();
        // Verify base64url output
        assertThat(expected).hasSize(43);
    }

    @Test
    void findByPhoneAndNameMixed() {
        Query q = new Query(new Criteria().andOperator(
                Criteria.where("phone").is("13800138000"),
                Criteria.where("name").is("John")));
        Query r = qc.rewrite(q, TestUser.class);
        Document doc = r.getQueryObject();
        assertThat(doc.toString()).contains("phone.b");
        assertThat(doc.toString()).contains("name");
    }

    @Test
    void findByPhoneInHashesEach() {
        List<String> phones = List.of("13800138000", "13900139000");
        Query q = new Query(Criteria.where("phone").in(phones));
        Query r = qc.rewrite(q, TestUser.class);
        Document doc = r.getQueryObject();
        Document inDoc = (Document) doc.get("phone.b");
        assertThat(inDoc).isNotNull();
        List<String> hashed = (List<String>) inDoc.get("$in");
        assertThat(hashed).hasSize(2);
    }

    @Test
    void findByPhoneIsNullNoHash() {
        Query q = new Query(Criteria.where("phone").is(null));
        Query r = qc.rewrite(q, TestUser.class);
        Document doc = r.getQueryObject();
        assertThat(doc.containsKey("phone")).isTrue();
    }

    @Test
    void blindIndexFalseThrows() {
        Query q = new Query(Criteria.where("age").is(30));
        assertThatThrownBy(() -> qc.rewrite(q, TestEmployee.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("blindIndex=false");
    }

    @Test
    void findByPhoneStartingWithThrows() {
        Query q = new Query(Criteria.where("phone").regex("^138"));
        assertThatThrownBy(() -> qc.rewrite(q, TestUser.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Pattern");
    }

    @Test
    void findByTagsRewritesToArrayBlindIndex() {
        Query q = new Query(Criteria.where("tags").is("java"));
        Query r = qc.rewrite(q, TestArticle.class);
        Document doc = r.getQueryObject();

        Namespace ns = Namespace.parse("default.default.TestArticle#tags");
        String expected = blindIndexEngine.computeBlindIndex(
                ns,
                "tags",
                "java".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(doc.getString("tags.b")).isEqualTo(expected);
        assertThat(doc.containsKey("tags")).isFalse();
    }

    @Test
    void findByTagsInRewritesEachToArrayBlindIndex() {
        Query q = new Query(Criteria.where("tags").in(List.of("java", "spring")));
        Query r = qc.rewrite(q, TestArticle.class);
        Document doc = r.getQueryObject();
        Document inDoc = (Document) doc.get("tags.b");
        assertThat(inDoc).isNotNull();
        List<String> hashed = (List<String>) inDoc.get("$in");
        assertThat(hashed).hasSize(2);
    }

    @Test
    void rewritePreservesSortSkipAndLimit() {
        Query q = new Query(Criteria.where("phone").is("13800138000"));
        q.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        q.skip(5);
        q.limit(10);

        Query r = qc.rewrite(q, TestUser.class);

        assertThat(r.getSortObject()).isEqualTo(new Document("createdAt", -1));
        assertThat(r.getSkip()).isEqualTo(5);
        assertThat(r.getLimit()).isEqualTo(10);
        assertThat(r.getQueryObject().containsKey("phone.b")).isTrue();
        assertThat(r.getQueryObject().containsKey("phone")).isFalse();
    }

    @Test
    void orOperatorRewritesEncryptedFieldsInside() {
        Query q = new Query(new Criteria().orOperator(
                Criteria.where("phone").is("13800138000"),
                Criteria.where("phone").is("13900139000")));
        Query r = qc.rewrite(q, TestUser.class);
        Document doc = r.getQueryObject();
        List<?> orList = (List<?>) doc.get("$or");
        assertThat(orList).hasSize(2);
        Document first = (Document) orList.get(0);
        assertThat(first.containsKey("phone.b")).isTrue();
        assertThat(first.containsKey("phone")).isFalse();
    }

    @Test
    void unsupportedOperatorOnEncryptedFieldThrows() {
        Query q = new Query(Criteria.where("phone").gt("138"));
        assertThatThrownBy(() -> qc.rewrite(q, TestUser.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported query operation");
    }

    @Test
    void nonEncryptedEntityQueryPassesThrough() {
        Query q = new Query(Criteria.where("name").is("John"));
        Query r = qc.rewrite(q, io.github.emmansun.lightcrypto.testmodel.TestPlainEntity.class);
        assertThat(r.getQueryObject()).isEqualTo(q.getQueryObject());
    }

    @Test
    void numericValueOnEncryptedFieldIsHashed() {
        Query q = new Query(Criteria.where("phone").is(13800138000L));
        Query r = qc.rewrite(q, TestUser.class);
        Document doc = r.getQueryObject();
        assertThat(doc.containsKey("phone.b")).isTrue();
        assertThat(doc.get("phone.b")).isInstanceOf(String.class);
    }

    @Test
    void booleanValueOnEncryptedFieldIsHashed() {
        Query q = new Query(Criteria.where("phone").is(true));
        Query r = qc.rewrite(q, TestUser.class);
        Document doc = r.getQueryObject();
        assertThat(doc.containsKey("phone.b")).isTrue();
    }

    @Test
    void ascendingSortIsPreserved() {
        Query q = new Query(Criteria.where("phone").is("13800138000"));
        q.with(Sort.by(Sort.Direction.ASC, "name"));

        Query r = qc.rewrite(q, TestUser.class);
        assertThat(r.getSortObject()).isEqualTo(new Document("name", 1));
    }

    @Test
    void unsortedQueryHasNoSort() {
        Query q = new Query(Criteria.where("phone").is("13800138000"));
        Query r = qc.rewrite(q, TestUser.class);
        assertThat(r.getSortObject().isEmpty()).isTrue();
    }
}
