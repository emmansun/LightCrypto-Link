package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FieldCryptoService (Wire Format V1).
 */
class FieldCryptoServiceTest extends LclTestBase {

    private static final int TEST_DEK_VERSION = 1;

    private FieldCryptoService fieldCryptoService;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache(new CryptoProperties());
        TypeDeserializer des = createTestTypeDeserializer();
        KeyVaultService vs = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        fieldCryptoService = new FieldCryptoService(mc, des, vs);
    }

    // --- Normal String field decryption (AES-256-GCM) ---

    @Test
    void decryptStringField_aesGcm() {
        String blob = encryptForField("hello", AlgorithmId.AES_256_GCM, "TestUser", "phone");
        Document doc = new Document();
        doc.put("phone", buildSubDoc(blob, "STR"));

        Document result = fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(result.get("phone")).isEqualTo("hello");
    }

    // --- Multi-field decryption ---

    @Test
    void decryptMultipleFields() {
        String encAge = encryptForField("30", AlgorithmId.AES_256_GCM, "TestEmployee", "age");
        String encBirth = encryptForField("1995-01-15", AlgorithmId.AES_256_GCM, "TestEmployee", "birthDate");

        Document doc = new Document();
        doc.put("age", buildSubDoc(encAge, "INT"));
        doc.put("birthDate", buildSubDoc(encBirth, "LDATE"));

        fieldCryptoService.decryptDocument(doc, TestEmployee.class);

        assertThat(doc.get("age")).isEqualTo(30);
        assertThat(doc.get("birthDate")).isEqualTo(LocalDate.of(1995, 1, 15));
    }

    // --- Typed field decryption (Integer / LocalDate) ---

    @Test
    void decryptIntegerField() {
        String enc = encryptForField("42", AlgorithmId.AES_256_GCM, "TestEmployee", "age");
        Document doc = new Document();
        doc.put("age", buildSubDoc(enc, "INT"));

        fieldCryptoService.decryptDocument(doc, TestEmployee.class);

        assertThat(doc.get("age")).isEqualTo(42);
    }

    @Test
    void decryptLocalDateField() {
        String enc = encryptForField("2024-06-15", AlgorithmId.AES_256_GCM, "TestEmployee", "birthDate");
        Document doc = new Document();
        doc.put("birthDate", buildSubDoc(enc, "LDATE"));

        fieldCryptoService.decryptDocument(doc, TestEmployee.class);

        assertThat(doc.get("birthDate")).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    // --- Multi-algorithm dispatch ---

    @Test
    void decryptWithSm4GcmAlgorithm() {
        String enc = encryptForField("secret", AlgorithmId.SM4_GCM, "TestUser", "phone");
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR"));

        fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(doc.get("phone")).isEqualTo("secret");
    }

    @Test
    void decryptWithAesCbcAlgorithm() {
        String enc = encryptForField("ciphertext", AlgorithmId.AES_256_CBC, "TestUser", "phone");
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR"));

        fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(doc.get("phone")).isEqualTo("ciphertext");
    }

    @Test
    void decryptWithSm4CbcAlgorithm() {
        String enc = encryptForField("data", AlgorithmId.SM4_CBC, "TestUser", "phone");
        Document doc = new Document();
        doc.put("phone", buildSubDoc(enc, "STR"));

        fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(doc.get("phone")).isEqualTo("data");
    }

    // --- Null parameter validation ---

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

    // --- Entity without encrypted fields ---

    @Test
    void plainEntityNoOp() {
        Document doc = new Document();
        doc.put("name", "test");
        doc.put("value", 42);

        Document result = fieldCryptoService.decryptDocument(doc, TestPlainEntity.class);

        assertThat(result.get("name")).isEqualTo("test");
        assertThat(result.get("value")).isEqualTo(42);
    }

    // --- Field absent / null / non-Document type ---

    @Test
    void missingFieldSkipped() {
        Document doc = new Document();
        doc.put("name", "Alice");

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
        doc.put("phone", "plaintext-string");

        assertThatNoException().isThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestUser.class));
        assertThat(doc.get("phone")).isEqualTo("plaintext-string");
    }

    // --- Idempotency ---

    @Test
    void idempotentDecryption() {
        String blob = encryptForField("hello", AlgorithmId.AES_256_GCM, "TestUser", "phone");
        Document doc = new Document();
        doc.put("phone", buildSubDoc(blob, "STR"));

        fieldCryptoService.decryptDocument(doc, TestUser.class);
        assertThat(doc.get("phone")).isEqualTo("hello");

        // Second call is a no-op (phone is now a String, not a sub-doc)
        fieldCryptoService.decryptDocument(doc, TestUser.class);
        assertThat(doc.get("phone")).isEqualTo("hello");
    }

    // --- In-place modification (same reference) ---

    @Test
    void returnsSameDocumentReference() {
        Document doc = new Document();
        doc.put("name", "Alice");

        Document result = fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(result).isSameAs(doc);
    }

    @Test
    void inPlaceModificationWithEncryptedField() {
        String blob = encryptForField("hello", AlgorithmId.AES_256_GCM, "TestUser", "phone");
        Document doc = new Document();
        doc.put("phone", buildSubDoc(blob, "STR"));

        Document result = fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(result).isSameAs(doc);
        assertThat(result.get("phone")).isEqualTo("hello");
    }

    // --- Collection / Map / Nested object decryption ---

    @Test
    void decryptCollectionListAndMapValues() {
        String encJava = encryptForField("java", AlgorithmId.AES_256_GCM, "TestArticle", "tags");
        String encSpring = encryptForField("spring", AlgorithmId.AES_256_GCM, "TestArticle", "tags");
        String encDark = encryptForField("dark", AlgorithmId.AES_256_GCM, "TestArticle", "settings.theme");

        Document doc = new Document();
        doc.put("tags", new ArrayList<>(List.of(
                buildSubDoc(encJava, "STR"),
                buildSubDoc(encSpring, "STR")
        )));
        doc.put("settings", new Document("theme", buildSubDoc(encDark, "STR")));

        fieldCryptoService.decryptDocument(doc, TestArticle.class);

        assertThat((List<Object>) doc.get("tags")).containsExactly("java", "spring");
        assertThat(((Document) doc.get("settings")).get("theme")).isEqualTo("dark");
    }

    @Test
    void decryptNestedPojoInsideList() {
        String encStreet = encryptForField("xx-road", AlgorithmId.AES_256_GCM, "TestUserWithAddresses", "addresses.street");

        Document address = new Document();
        address.put("street", buildSubDoc(encStreet, "STR"));
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
        String enc = encryptBytesForField(payload, AlgorithmId.AES_256_GCM, "TestUserWithWholeAddress", "address");

        Document doc = new Document();
        doc.put("address", buildSubDoc(enc, "DOC"));

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
        String enc = encryptBytesForField(payload, AlgorithmId.AES_256_GCM, "TestUserWithWholeAddresses", "addresses");

        Document doc = new Document();
        doc.put("addresses", buildSubDoc(enc, "COL"));

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
        String listEncrypted = encryptBytesForField(listEncoded, AlgorithmId.AES_256_GCM, "TestWholeSimpleCollections", "tags");

        Document mapPayload = new Document("theme", "dark");
        byte[] mapEncoded = encodePayload(mapPayload);
        String mapEncrypted = encryptBytesForField(mapEncoded, AlgorithmId.AES_256_GCM, "TestWholeSimpleCollections", "settings");

        Document doc = new Document();
        doc.put("tags", buildSubDoc(listEncrypted, "COL"));
        doc.put("settings", buildSubDoc(mapEncrypted, "MAP"));

        fieldCryptoService.decryptDocument(doc, TestWholeSimpleCollections.class);

        assertThat(doc.get("tags")).isInstanceOf(List.class);
        assertThat((List<Object>) doc.get("tags")).containsExactly("java", "spring");
        assertThat(doc.get("settings")).isInstanceOf(Document.class);
        assertThat(((Document) doc.get("settings")).getString("theme")).isEqualTo("dark");
    }

    // --- Error handling ---

    @Test
    void keyVaultFailureIsWrappedAsKeyManagementException() {
        KeyVaultService failingVault = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY) {
            @Override
            public byte[] getDekByVersion(String namespace, int dekVersion) {
                throw new FatalCryptoException("fail");
            }
        };
        fieldCryptoService = new FieldCryptoService(
                new EntityMetadataCache(new CryptoProperties()), createTestTypeDeserializer(), failingVault);
        String blob = encryptForField("hello", AlgorithmId.AES_256_GCM, "TestUser", "phone");
        Document doc = new Document("phone", buildSubDoc(blob, "STR"));

        assertThatThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestUser.class))
                .isInstanceOf(KeyManagementException.class)
                .hasMessageContaining("Failed to resolve DEK");
    }

    @Test
    void decryptFailureIsWrappedAsDecryptionException() {
        // Create a valid wire format blob but with wrong DEK so decryption fails
        Namespace ns = Namespace.parse("default.default.TestUser#phone");
        byte[] wrongDek = new byte[32]; // all zeros — wrong key
        String blob = CryptoCodec.encrypt(wrongDek, "hello".getBytes(StandardCharsets.UTF_8),
                AlgorithmId.AES_256_GCM, ns, TEST_DEK_VERSION);
        // Now decrypt with the correct DEK in the vault — will fail because blob was encrypted with wrong DEK
        // Actually, we need the blob to be decryptable by format but fail at cipher level.
        // Use correct format but corrupt the ciphertext portion:
        String corruptedBlob = blob.substring(0, blob.length() - 4) + "AAAA";

        Document doc = new Document("phone", buildSubDoc(corruptedBlob, "STR"));

        assertThatThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestUser.class))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to decrypt field 'phone'");
    }

    @Test
    void deserializeFailureIsWrappedAsDecryptionException() {
        String enc = encryptForField("not-an-int", AlgorithmId.AES_256_GCM, "TestEmployee", "age");
        Document doc = new Document("age", buildSubDoc(enc, "INT"));

        assertThatThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestEmployee.class))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to deserialize field 'age'");
    }

    @Test
    void structuredPayloadDecodeFailureIsWrapped() {
        String enc = encryptForField("not-bson", AlgorithmId.AES_256_GCM, "TestUserWithWholeAddress", "address");
        Document doc = new Document("address", buildSubDoc(enc, "DOC"));

        assertThatThrownBy(() -> fieldCryptoService.decryptDocument(doc, TestUserWithWholeAddress.class))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to decode structured payload");
    }

    @Test
    void decodeStructuredValueUnknownTypeThrowsViaReflection() throws Exception {
        Method method = FieldCryptoService.class.getDeclaredMethod("decodeStructuredValue", String.class, byte[].class);
        method.setAccessible(true);
        byte[] payload = encodePayload(new Document("k", "v"));

        assertThatThrownBy(() -> method.invoke(fieldCryptoService, "UNKNOWN", payload))
                .hasCauseInstanceOf(DecryptionException.class)
                .hasRootCauseMessage("Unsupported structured type marker: UNKNOWN");
    }

    // --- Malformed sub-documents are left untouched ---

    @Test
    void malformedEncryptedFieldSubDocumentIsLeftUntouched() {
        // Has _e and _t but no "c" field
        Document malformed = new Document("_e", 1)
                .append("_t", "STR");
        Document doc = new Document("phone", malformed);

        fieldCryptoService.decryptDocument(doc, TestUser.class);

        assertThat(doc.get("phone")).isSameAs(malformed);
    }

    @Test
    void malformedEncryptedListElementIsLeftUntouched() {
        Document malformed = new Document("_e", 1)
                .append("_t", "STR");
        Document doc = new Document("tags", new ArrayList<>(List.of(malformed)));

        fieldCryptoService.decryptDocument(doc, TestArticle.class);

        assertThat(((List<?>) doc.get("tags")).get(0)).isSameAs(malformed);
    }

    @Test
    void malformedEncryptedMapEntryIsLeftUntouched() {
        Document malformed = new Document("_e", 1)
                .append("_t", "STR");
        Document doc = new Document("settings", new Document("theme", malformed));

        fieldCryptoService.decryptDocument(doc, TestArticle.class);

        assertThat(((Document) doc.get("settings")).get("theme")).isSameAs(malformed);
    }

    // --- Helpers ---

    private String encryptForField(String plaintext, AlgorithmId algorithm, String entityName, String fieldPath) {
        return encryptBytesForField(plaintext.getBytes(StandardCharsets.UTF_8), algorithm, entityName, fieldPath);
    }

    private String encryptBytesForField(byte[] plaintext, AlgorithmId algorithm, String entityName, String fieldPath) {
        Namespace ns = Namespace.parse("default.default." + entityName + "#" + fieldPath);
        return CryptoCodec.encrypt(TEST_DEK, plaintext, algorithm, ns, TEST_DEK_VERSION);
    }

    private Document buildSubDoc(String blob, String typeMarker) {
        Document sub = new Document();
        sub.put("c", blob);
        sub.put("_e", 1);
        sub.put("_t", typeMarker);
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
