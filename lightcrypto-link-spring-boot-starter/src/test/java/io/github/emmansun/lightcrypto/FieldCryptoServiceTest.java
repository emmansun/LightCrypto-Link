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
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestPlainEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

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
}
