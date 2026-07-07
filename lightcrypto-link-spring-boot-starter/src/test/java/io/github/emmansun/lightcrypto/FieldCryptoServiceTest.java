package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import io.github.emmansun.lightcrypto.testmodel.MultiAlgoEntity;
import io.github.emmansun.lightcrypto.testmodel.TestArticle;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestPlainEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithWholeAddress;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithWholeAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestWholeSimpleCollections;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FieldCryptoService.
 */
class FieldCryptoServiceTest extends LclTestBase {

    private static final String TEST_KID = "v1-test0001";

    private FieldCryptoService fieldCryptoService;
    private CryptoCodec codec;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache(new CryptoProperties());
        codec = createTestCryptoCodec();
        TypeDeserializer des = createTestTypeDeserializer();
        KeyVaultService vs = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        fieldCryptoService = new FieldCryptoService(mc, codec, des, vs);
    }

    // --- 4.2: Normal String field decryption (AES-256-GCM) ---

    @Test
    void decryptStringField_aesGcm() {
        byte[] enc = codec.encrypt(TEST_DEK, "hello".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR", TEST_KID, "AES_256_GCM"));

        Document result = fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(result.get("phone")).isEqualTo("hello");
    }

    // --- 4.3: Multi-field decryption ---

    @Test
    void decryptMultipleFields() {
        byte[] encPhone = codec.encrypt(TEST_DEK, "13800138000".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        byte[] encIdCard = codec.encrypt(TEST_DEK, "ID12345".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);

        // TestUser only has phone as encrypted; use TestEmployee which has age + birthDate
        byte[] encAge = codec.encrypt(TEST_DEK, "30".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        byte[] encBirth = codec.encrypt(TEST_DEK, "1995-01-15".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);

        Document doc = new Document();
        doc.put("age", buildSubDoc(encAge, "INT", TEST_KID, "AES_256_GCM"));
        doc.put("birthDate", buildSubDoc(encBirth, "LDATE", TEST_KID, "AES_256_GCM"));

        fieldCryptoService.decryptDocument(doc, TestEmployee.class);

        assertThat(doc.get("age")).isEqualTo(30);
        assertThat(doc.get("birthDate")).isEqualTo(LocalDate.of(1995, 1, 15));
    }

    // --- 4.4: Typed field decryption (Integer / LocalDate) ---

    @Test
    void decryptIntegerField() {
        byte[] enc = codec.encrypt(TEST_DEK, "42".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document doc = new Document();
        doc.put("age", buildSubDoc(enc, "INT", TEST_KID, "AES_256_GCM"));

        fieldCryptoService.decryptDocument(doc, TestEmployee.class);

        assertThat(doc.get("age")).isEqualTo(42);
    }

    @Test
    void decryptLocalDateField() {
        byte[] enc = codec.encrypt(TEST_DEK, "2024-06-15".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document doc = new Document();
        doc.put("birthDate", buildSubDoc(enc, "LDATE", TEST_KID, "AES_256_GCM"));

        fieldCryptoService.decryptDocument(doc, TestEmployee.class);

        assertThat(doc.get("birthDate")).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    // --- 4.5: Multi-algorithm dispatch ---

    @Test
    void decryptWithSm4GcmAlgorithm() {
        byte[] enc = codec.encrypt(TEST_DEK, "secret".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.SM4_GCM);
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR", TEST_KID, "SM4_GCM"));

        fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(doc.get("phone")).isEqualTo("secret");
    }

    @Test
    void decryptWithAesCbcAlgorithm() {
        byte[] enc = codec.encrypt(TEST_DEK, "ciphertext".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_CBC);
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR", TEST_KID, "AES_256_CBC"));

        fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(doc.get("phone")).isEqualTo("ciphertext");
    }

    @Test
    void decryptWithSm4CbcAlgorithm() {
        byte[] enc = codec.encrypt(TEST_DEK, "data".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.SM4_CBC);
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR", TEST_KID, "SM4_CBC"));

        fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(doc.get("phone")).isEqualTo("data");
    }

    // --- 4.6: Null parameter validation ---

    @Test
    void nullDocumentThrows() {
        assertThatThrownBy(() -> fieldCryptoService.decryptDocument(null, TestUser.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullEntityClassThrows() {
        Document doc = new Document();
        assertThatThrownBy(() -> fieldCryptoService.decryptDocument(doc, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- 4.7: Entity without encrypted fields ---

    @Test
    void plainEntityNoOp() {
        Document doc = new Document();
        doc.put("name", "test");
        doc.put("value", 42);

        Document result = fieldCryptoService.decryptDocument(doc, TestPlainEntity.class);

        assertThat(result.get("name")).isEqualTo("test");
        assertThat(result.get("value")).isEqualTo(42);
    }

    // --- 4.8: Field absent / null / non-Document type ---

    @Test
    void missingFieldSkipped() {
        Document doc = new Document();
        doc.put("name", "Alice");
        // phone is missing

        assertThatNoException().isThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestUser.class));
        assertThat(doc.get("name")).isEqualTo("Alice");
    }

    @Test
    void nullFieldValueSkipped() {
        Document doc = new Document();
        doc.put("phone", null);

        assertThatNoException().isThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestUser.class));
        assertThat(doc.get("phone")).isNull();
    }

    @Test
    void nonDocumentFieldValueSkipped() {
        Document doc = new Document();
        doc.put("phone", "plaintext-string"); // already a plain string

        assertThatNoException().isThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestUser.class));
        assertThat(doc.get("phone")).isEqualTo("plaintext-string");
    }

    // --- 4.9: Idempotency ---

    @Test
    void idempotentDecryption() {
        byte[] enc = codec.encrypt(TEST_DEK, "hello".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR", TEST_KID, "AES_256_GCM"));

        // First call decrypts
        fieldCryptoService.decryptDocument(doc, TestUser.class);
        assertThat(doc.get("phone")).isEqualTo("hello");

        // Second call is a no-op (phone is now a String, not a sub-doc)
        fieldCryptoService.decryptDocument(doc, TestUser.class);
        assertThat(doc.get("phone")).isEqualTo("hello");
    }

    // --- 4.10: Missing _k throws FatalCryptoException ---

    @Test
    void missingKidThrowsFatalCryptoException() {
        byte[] enc = codec.encrypt(TEST_DEK, "hello".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document subDoc = new Document();
        subDoc.put("c", new Binary(enc));
        subDoc.put("_e", 1);
        subDoc.put("_t", "STR");
        // _k is missing

        Document doc = new Document();
        doc.put("phone", subDoc);

        assertThatThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestUser.class))
                .isInstanceOf(FatalCryptoException.class)
                .hasMessageContaining("_k")
                .hasMessageContaining("phone");
    }

    // --- 4.11: Missing _a defaults to AES_256_GCM ---

    @Test
    void missingAlgorithmDefaultsToAesGcm() {
        // Encrypt with AES-256-GCM
        byte[] enc = codec.encrypt(TEST_DEK, "default-algo".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document subDoc = new Document();
        subDoc.put("c", new Binary(enc));
        subDoc.put("_e", 1);
        subDoc.put("_t", "STR");
        subDoc.put("_k", TEST_KID);
        // _a is intentionally absent

        Document doc = new Document();
        doc.put("phone", subDoc);

        fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(doc.get("phone")).isEqualTo("default-algo");
    }

    // --- 4.12: In-place modification (same reference) ---

    @Test
    void returnsSameDocumentReference() {
        Document doc = new Document();
        doc.put("name", "Alice");

        Document result = fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(result).isSameAs(doc);
    }

    @Test
    void inPlaceModificationWithEncryptedField() {
        byte[] enc = codec.encrypt(TEST_DEK, "hello".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR", TEST_KID, "AES_256_GCM"));

        Document result = fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(result).isSameAs(doc);
        assertThat(result.get("phone")).isEqualTo("hello");
    }

    @Test
    void decryptCollectionListAndMapValues() {
        byte[] encJava = codec.encrypt(TEST_DEK, "java".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        byte[] encSpring = codec.encrypt(TEST_DEK, "spring".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        byte[] encDark = codec.encrypt(TEST_DEK, "dark".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);

        Document doc = new Document();
        doc.put("tags", new ArrayList<>(List.of(
                buildSubDoc(encJava, "STR", TEST_KID, "AES_256_GCM"),
                buildSubDoc(encSpring, "STR", TEST_KID, "AES_256_GCM")
        )));
        doc.put("settings", new Document("theme", buildSubDoc(encDark, "STR", TEST_KID, "AES_256_GCM")));

        fieldCryptoService.decryptDocument(doc, TestArticle.class);

        assertThat((List<Object>) doc.get("tags")).containsExactly("java", "spring");
        assertThat(((Document) doc.get("settings")).get("theme")).isEqualTo("dark");
    }

    @Test
    void decryptNestedPojoInsideList() {
        byte[] encStreet = codec.encrypt(TEST_DEK, "xx-road".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);

        Document address = new Document();
        address.put("street", buildSubDoc(encStreet, "STR", TEST_KID, "AES_256_GCM"));
        address.put("city", "shanghai");

        Document doc = new Document("addresses", new ArrayList<>(List.of(address)));
        fieldCryptoService.decryptDocument(doc, TestUserWithAddresses.class);

        Document first = (Document) ((List<?>) doc.get("addresses")).get(0);
        assertThat(first.get("street")).isEqualTo("xx-road");
        assertThat(first.get("city")).isEqualTo("shanghai");
    }

    @Test
    void decryptWholeNestedObjectPayload() {
        Document addressDoc = new Document();
        addressDoc.put("street", "xx-road");
        addressDoc.put("city", "shanghai");
        byte[] payload = encodePayload(addressDoc);
        byte[] enc = codec.encrypt(TEST_DEK, payload, SymmetricAlgorithm.AES_256_GCM);

        Document doc = new Document();
        doc.put("address", buildSubDoc(enc, "DOC", TEST_KID, "AES_256_GCM"));

        fieldCryptoService.decryptDocument(doc, TestUserWithWholeAddress.class);

        assertThat(doc.get("address")).isInstanceOf(Document.class);
        Document address = (Document) doc.get("address");
        assertThat(address.get("street")).isEqualTo("xx-road");
        assertThat(address.get("city")).isEqualTo("shanghai");
    }

    @Test
    void decryptWholeCollectionPayload() {
        Document element = new Document();
        element.put("street", "xx-road");
        element.put("city", "shanghai");
        Document payloadDoc = new Document("_v", new ArrayList<>(List.of(element)));
        byte[] payload = encodePayload(payloadDoc);
        byte[] enc = codec.encrypt(TEST_DEK, payload, SymmetricAlgorithm.AES_256_GCM);

        Document doc = new Document();
        doc.put("addresses", buildSubDoc(enc, "COL", TEST_KID, "AES_256_GCM"));

        fieldCryptoService.decryptDocument(doc, TestUserWithWholeAddresses.class);

        assertThat(doc.get("addresses")).isInstanceOf(List.class);
        List<?> addresses = (List<?>) doc.get("addresses");
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0)).isInstanceOf(Document.class);
        Document first = (Document) addresses.get(0);
        assertThat(first.get("street")).isEqualTo("xx-road");
        assertThat(first.get("city")).isEqualTo("shanghai");
    }

    @Test
    void decryptWholeSimpleCollectionAndMapPayload() {
        Document listPayload = new Document("_v", new ArrayList<>(List.of("java", "spring")));
        byte[] listEncoded = encodePayload(listPayload);
        byte[] listEncrypted = codec.encrypt(TEST_DEK, listEncoded, SymmetricAlgorithm.AES_256_GCM);

        Document mapPayload = new Document("theme", "dark");
        byte[] mapEncoded = encodePayload(mapPayload);
        byte[] mapEncrypted = codec.encrypt(TEST_DEK, mapEncoded, SymmetricAlgorithm.AES_256_GCM);

        Document doc = new Document();
        doc.put("tags", buildSubDoc(listEncrypted, "COL", TEST_KID, "AES_256_GCM"));
        doc.put("settings", buildSubDoc(mapEncrypted, "MAP", TEST_KID, "AES_256_GCM"));

        fieldCryptoService.decryptDocument(doc, TestWholeSimpleCollections.class);

        assertThat(doc.get("tags")).isInstanceOf(List.class);
        assertThat((List<Object>) doc.get("tags")).containsExactly("java", "spring");
        assertThat(doc.get("settings")).isInstanceOf(Document.class);
        assertThat(((Document) doc.get("settings")).getString("theme")).isEqualTo("dark");
    }

    // --- 4.13 is run separately ---

    // Helper: build an encrypted sub-document
    private Document buildSubDoc(byte[] cipherBytes, String typeMarker, String kid, String algorithm) {
        Document sub = new Document();
        sub.put("c", new Binary(cipherBytes));
        sub.put("_e", 1);
        sub.put("_t", typeMarker);
        sub.put("_k", kid);
        sub.put("_a", algorithm);
        return sub;
    }

    private byte[] encodePayload(Document payload) {
        BasicOutputBuffer buffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
        new DocumentCodec().encode(writer, payload, EncoderContext.builder().build());
        writer.flush();
        return buffer.toByteArray();
    }
}
