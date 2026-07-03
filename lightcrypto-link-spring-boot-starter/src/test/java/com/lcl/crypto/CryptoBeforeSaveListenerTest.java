package com.lcl.crypto;

import com.lcl.crypto.listener.CryptoBeforeSaveListener;
import com.lcl.crypto.listener.EntityMetadataCache;
import com.lcl.crypto.service.CryptoCodec;
import com.lcl.crypto.service.KeyVaultService;
import com.lcl.crypto.service.TypeSerializer;
import com.lcl.crypto.testmodel.TestEmployee;
import com.lcl.crypto.testmodel.TestUser;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

import static org.assertj.core.api.Assertions.*;

class CryptoBeforeSaveListenerTest extends LclTestBase {
    private CryptoBeforeSaveListener listener;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache();
        CryptoCodec codec = createTestCryptoCodec();
        TypeSerializer ser = createTestTypeSerializer();
        KeyVaultService vs = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        listener = new CryptoBeforeSaveListener(mc, codec, ser, vs);
    }

    @Test
    void stringFieldWithBlindIndex() {
        TestUser user = new TestUser();
        user.setPhone("13800138000");
        Document doc = new Document();
        doc.put("phone", "13800138000");
        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));

        Document sub = (Document) doc.get("phone");
        assertThat(sub.get("c")).isInstanceOf(Binary.class);
        assertThat(sub.get("b")).isInstanceOf(String.class);
        assertThat(sub.getInteger("_e")).isEqualTo(1);
        assertThat(sub.getString("_t")).isEqualTo("STR");
        assertThat(sub.getString("_k")).isNotNull(); // kid field
    }

    @Test
    void blindIndexFalseOmitsBField() {
        TestEmployee emp = new TestEmployee();
        emp.setAge(30);
        Document doc = new Document();
        doc.put("age", 30);
        listener.onBeforeSave(new BeforeSaveEvent<>(emp, doc, "col"));

        Document sub = (Document) doc.get("age");
        assertThat(sub.containsKey("b")).isFalse();
        assertThat(sub.getInteger("_e")).isEqualTo(1);
        assertThat(sub.getString("_t")).isEqualTo("INT");
    }

    @Test
    void nullFieldSkipped() {
        TestUser user = new TestUser();
        Document doc = new Document();
        doc.put("phone", null);
        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));
        assertThat(doc.get("phone")).isNull();
    }
}
