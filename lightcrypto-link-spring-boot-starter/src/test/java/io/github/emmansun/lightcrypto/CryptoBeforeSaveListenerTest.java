package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.listener.CryptoBeforeSaveListener;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.testmodel.TestArticle;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithWholeAddress;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithWholeAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestWholeSimpleCollections;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CryptoBeforeSaveListenerTest extends LclTestBase {
    private CryptoBeforeSaveListener listener;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache(new CryptoProperties());
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

    @Test
    void encryptedListAndMapValuesAreTransformedToSubDocuments() {
        TestArticle article = new TestArticle();
        article.setTags(List.of("java", "spring"));
        article.setSettings(Map.of("theme", "dark"));

        Document doc = new Document();
        doc.put("tags", List.of("java", "spring"));
        doc.put("settings", new Document("theme", "dark"));

        listener.onBeforeSave(new BeforeSaveEvent<>(article, doc, "col"));

        List<?> tags = (List<?>) doc.get("tags");
        assertThat(tags).hasSize(2);
        assertThat(tags.get(0)).isInstanceOf(Document.class);
        Document tagSubDoc = (Document) tags.get(0);
        assertThat(tagSubDoc.get("c")).isInstanceOf(Binary.class);
        assertThat(tagSubDoc.get("b")).isInstanceOf(String.class);
        assertThat(tagSubDoc.getString("_t")).isEqualTo("STR");

        Document settings = (Document) doc.get("settings");
        Document settingSubDoc = (Document) settings.get("theme");
        assertThat(settingSubDoc.get("c")).isInstanceOf(Binary.class);
        assertThat(settingSubDoc.getString("_t")).isEqualTo("STR");
    }

    @Test
    void nestedPojoInsideListEncryptsLeafFieldOnly() {
        TestUserWithAddresses user = new TestUserWithAddresses();
        TestUserWithAddresses.Address address = new TestUserWithAddresses.Address();
        address.setStreet("xx-road");
        address.setCity("shanghai");
        user.setAddresses(List.of(address));

        Document addressDoc = new Document();
        addressDoc.put("street", "xx-road");
        addressDoc.put("city", "shanghai");
        Document doc = new Document("addresses", new java.util.ArrayList<>(List.of(addressDoc)));

        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));

        List<?> addresses = (List<?>) doc.get("addresses");
        Document encryptedAddress = (Document) addresses.get(0);
        assertThat(encryptedAddress.get("street")).isInstanceOf(Document.class);
        Document streetSubDoc = (Document) encryptedAddress.get("street");
        assertThat(streetSubDoc.get("c")).isInstanceOf(Binary.class);
        assertThat(encryptedAddress.get("city")).isEqualTo("shanghai");
    }

    @Test
    void wholeNestedObjectFieldEncryptsAsSingleDocBlob() {
        TestUserWithWholeAddress user = new TestUserWithWholeAddress();
        TestUserWithWholeAddress.Address address = new TestUserWithWholeAddress.Address();
        address.setCity("shanghai");
        address.setStreet("xx-road");
        address.setZipCode("200001");
        user.setAddress(address);

        Document addressDoc = new Document();
        addressDoc.put("city", "shanghai");
        addressDoc.put("street", "xx-road");
        addressDoc.put("zipCode", "200001");
        Document doc = new Document("address", addressDoc);

        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));

        Object raw = doc.get("address");
        assertThat(raw).isInstanceOf(Document.class);
        Document encrypted = (Document) raw;
        assertThat(encrypted.get("c")).isInstanceOf(Binary.class);
        assertThat(encrypted.getInteger("_e")).isEqualTo(1);
        assertThat(encrypted.getString("_t")).isEqualTo("DOC");
    }

    @Test
    void wholeCollectionFieldEncryptsAsSingleCollectionBlob() {
        TestUserWithWholeAddresses user = new TestUserWithWholeAddresses();
        TestUserWithWholeAddresses.Address address = new TestUserWithWholeAddresses.Address();
        address.setCity("shanghai");
        address.setStreet("xx-road");
        user.setAddresses(List.of(address));

        Document addressDoc = new Document();
        addressDoc.put("city", "shanghai");
        addressDoc.put("street", "xx-road");
        Document doc = new Document("addresses", new java.util.ArrayList<>(List.of(addressDoc)));

        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));

        Object raw = doc.get("addresses");
        assertThat(raw).isInstanceOf(Document.class);
        Document encrypted = (Document) raw;
        assertThat(encrypted.get("c")).isInstanceOf(Binary.class);
        assertThat(encrypted.getInteger("_e")).isEqualTo(1);
        assertThat(encrypted.getString("_t")).isEqualTo("COL");
    }

    @Test
    void wholeModeSimpleListAndMapEncryptAsSingleBlobs() {
        TestWholeSimpleCollections entity = new TestWholeSimpleCollections();
        entity.setTags(List.of("java", "spring"));
        entity.setSettings(Map.of("theme", "dark"));

        Document doc = new Document();
        doc.put("tags", new java.util.ArrayList<>(List.of("java", "spring")));
        doc.put("settings", new Document("theme", "dark"));

        listener.onBeforeSave(new BeforeSaveEvent<>(entity, doc, "col"));

        assertThat(doc.get("tags")).isInstanceOf(Document.class);
        Document tagsSub = (Document) doc.get("tags");
        assertThat(tagsSub.get("c")).isInstanceOf(Binary.class);
        assertThat(tagsSub.getString("_t")).isEqualTo("COL");

        assertThat(doc.get("settings")).isInstanceOf(Document.class);
        Document settingsSub = (Document) doc.get("settings");
        assertThat(settingsSub.get("c")).isInstanceOf(Binary.class);
        assertThat(settingsSub.getString("_t")).isEqualTo("MAP");
    }
}
