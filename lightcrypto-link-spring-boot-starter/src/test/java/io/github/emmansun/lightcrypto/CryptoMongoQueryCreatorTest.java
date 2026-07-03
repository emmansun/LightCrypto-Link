package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.query.CryptoMongoQueryCreator;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CryptoMongoQueryCreatorTest extends LclTestBase {
    private CryptoMongoQueryCreator qc;
    private CryptoCodec codec;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache(new CryptoProperties());
        codec = createTestCryptoCodec();
        TypeSerializer ser = createTestTypeSerializer();
        KeyVaultService vs = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        qc = new CryptoMongoQueryCreator(mc, codec, ser, vs);
    }

    @Test
    void findByPhoneProducesBlindIndexQuery() {
        Query q = new Query(Criteria.where("phone").is("13800138000"));
        Query r = qc.rewrite(q, TestUser.class);
        Document doc = r.getQueryObject();
        String expected = codec.generateBlindIndex(TEST_HMAC_KEY, "phone", "13800138000".getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
}
