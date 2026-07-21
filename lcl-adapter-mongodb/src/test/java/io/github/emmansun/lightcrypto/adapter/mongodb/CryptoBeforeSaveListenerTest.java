package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.config.CryptographyProperties;
import io.github.emmansun.lightcrypto.config.TenantProperties;
import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.testmodel.TestArticle;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestPlainEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithWholeAddress;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithWholeAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestWholeSimpleCollections;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CryptoBeforeSaveListenerTest extends MongoAdapterTestBase {
    private CryptoBeforeSaveListener listener;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache(new CryptographyProperties(), new TenantProperties());
        TypeSerializer ser = createTestTypeSerializer();
        KeyVaultService vs = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        BlindIndexEngine blindIndexEngine = new BlindIndexEngine(TEST_HMAC_KEY);
        listener = new CryptoBeforeSaveListener(mc, ser, vs, blindIndexEngine, createTestStorageAdapter(), createTestStructuredValueCodec());
    }

    @Test
    void stringFieldWithBlindIndex() {
        TestUser user = new TestUser();
        user.setPhone("13800138000");
        Document doc = new Document();
        doc.put("phone", "13800138000");
        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));

        Document sub = (Document) doc.get("phone");
        assertThat(sub.get("c")).isInstanceOf(String.class); // Base64URL string
        assertThat(sub.get("b")).isInstanceOf(String.class);
        assertThat(sub.getInteger("_e")).isEqualTo(1);
        assertThat(sub.getString("_t")).isEqualTo("STR");
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
        assertThat(tagSubDoc.get("c")).isInstanceOf(String.class);
        assertThat(tagSubDoc.get("b")).isInstanceOf(String.class);
        assertThat(tagSubDoc.getString("_t")).isEqualTo("STR");

        Document settings = (Document) doc.get("settings");
        Document settingSubDoc = (Document) settings.get("theme");
        assertThat(settingSubDoc.get("c")).isInstanceOf(String.class);
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
        assertThat(streetSubDoc.get("c")).isInstanceOf(String.class);
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
        assertThat(encrypted.get("c")).isInstanceOf(String.class);
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
        assertThat(encrypted.get("c")).isInstanceOf(String.class);
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
        assertThat(tagsSub.get("c")).isInstanceOf(String.class);
        assertThat(tagsSub.getString("_t")).isEqualTo("COL");

        assertThat(doc.get("settings")).isInstanceOf(Document.class);
        Document settingsSub = (Document) doc.get("settings");
        assertThat(settingsSub.get("c")).isInstanceOf(String.class);
        assertThat(settingsSub.getString("_t")).isEqualTo("MAP");
    }

    @Test
    void entityWithoutEncryptedFieldsIsSkipped() {
        TestPlainEntity entity = new TestPlainEntity();
        entity.setName("plain");
        Document doc = new Document("name", "plain");
        listener.onBeforeSave(new BeforeSaveEvent<>(entity, doc, "col"));
        assertThat(doc.getString("name")).isEqualTo("plain");
    }

    @Test
    void listWithNullItemsSkipsNulls() {
        TestArticle article = new TestArticle();
        List<String> tags = new ArrayList<>();
        tags.add("java");
        tags.add(null);
        tags.add("spring");
        article.setTags(tags);

        Document doc = new Document();
        doc.put("tags", new ArrayList<>(List.of("java", "placeholder", "spring")));

        listener.onBeforeSave(new BeforeSaveEvent<>(article, doc, "col"));

        List<?> result = (List<?>) doc.get("tags");
        assertThat(result).hasSize(3);
        // First and third are encrypted sub-docs
        assertThat(result.get(0)).isInstanceOf(Document.class);
        assertThat(result.get(2)).isInstanceOf(Document.class);
        // Second (null java item) remains unchanged
        assertThat(result.get(1)).isEqualTo("placeholder");
    }

    @Test
    void mapWithNullValueSkipsEntry() {
        TestArticle article = new TestArticle();
        Map<String, String> settings = new HashMap<>();
        settings.put("theme", "dark");
        settings.put("empty", null);
        article.setSettings(settings);

        Document mapDoc = new Document();
        mapDoc.put("theme", "dark");
        mapDoc.put("empty", null);
        Document doc = new Document("settings", mapDoc);

        listener.onBeforeSave(new BeforeSaveEvent<>(article, doc, "col"));

        Document resultMap = (Document) doc.get("settings");
        // "theme" is encrypted
        assertThat(resultMap.get("theme")).isInstanceOf(Document.class);
        // "empty" (null value) is skipped
        assertThat(resultMap.get("empty")).isNull();
    }

    @Test
    void listIterWithNonListBsonValueIsSkipped() {
        TestArticle article = new TestArticle();
        article.setTags(List.of("java"));

        Document doc = new Document();
        doc.put("tags", "not-a-list"); // BSON is not a List

        listener.onBeforeSave(new BeforeSaveEvent<>(article, doc, "col"));
        // Should not throw, just skip
        assertThat(doc.get("tags")).isEqualTo("not-a-list");
    }

    @Test
    void mapIterWithNonDocumentBsonValueIsSkipped() {
        TestArticle article = new TestArticle();
        article.setSettings(Map.of("theme", "dark"));

        Document doc = new Document();
        doc.put("settings", "not-a-document"); // BSON is not a Document

        listener.onBeforeSave(new BeforeSaveEvent<>(article, doc, "col"));
        // Should not throw, just skip
        assertThat(doc.get("settings")).isEqualTo("not-a-document");
    }

    @Test
    void nestedListWithNonDocumentChildIsSkipped() {
        TestUserWithAddresses user = new TestUserWithAddresses();
        TestUserWithAddresses.Address address = new TestUserWithAddresses.Address();
        address.setStreet("xx-road");
        user.setAddresses(List.of(address));

        // BSON list contains a non-Document element
        Document doc = new Document("addresses", new ArrayList<>(List.of("not-a-doc")));

        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));
        // Should not throw; non-Document child is skipped
        List<?> result = (List<?>) doc.get("addresses");
        assertThat(result.get(0)).isEqualTo("not-a-doc");
    }

    @Test
    void wholeDocFieldWithNonDocumentBsonIsSkipped() {
        TestUserWithWholeAddress user = new TestUserWithWholeAddress();
        TestUserWithWholeAddress.Address address = new TestUserWithWholeAddress.Address();
        address.setCity("shanghai");
        user.setAddress(address);

        // BSON value is not a Document
        Document doc = new Document("address", "raw-string");

        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));
        // Should not throw; non-Document BSON for whole-object is skipped
        assertThat(doc.get("address")).isEqualTo("raw-string");
    }

    @Test
    void nestedFieldWithNullBsonContextIsSkipped() {
        TestUserWithAddresses user = new TestUserWithAddresses();
        TestUserWithAddresses.Address address = new TestUserWithAddresses.Address();
        address.setStreet("xx-road");
        user.setAddresses(List.of(address));

        // BSON list contains null
        ArrayList<Object> bsonList = new ArrayList<>();
        bsonList.add(null);
        Document doc = new Document("addresses", bsonList);

        listener.onBeforeSave(new BeforeSaveEvent<>(user, doc, "col"));
        // Should not throw
        List<?> result = (List<?>) doc.get("addresses");
        assertThat(result).hasSize(1);
    }
}
