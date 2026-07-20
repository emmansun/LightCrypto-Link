package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.listener.CryptoMappingMongoConverter;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CryptoMappingMongoConverter decryption logic.
 */
class CryptoBeforeConvertListenerTest extends LclTestBase {

    private TestableConverter converter;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache(new CryptoProperties());
        TypeDeserializer des = createTestTypeDeserializer();
        KeyVaultService vs = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        FieldCryptoService fieldCryptoService = new FieldCryptoService(mc, des, vs);
        converter = new TestableConverter(mc, fieldCryptoService);
    }

    private String encryptToBlob(byte[] plaintext, String namespace) {
        Namespace ns = Namespace.parse(namespace);
        return CryptoCodec.encrypt(TEST_DEK, plaintext, AlgorithmId.AES_256_GCM, ns, 1);
    }

    @Test
    void decryptStringSubDoc() {
        String blob = encryptToBlob("hello".getBytes(StandardCharsets.UTF_8), "default.default.TestUser#phone");
        Document sub = new Document();
        sub.put("c", blob);
        sub.put("_e", 1);
        sub.put("_t", "STR");

        Document doc = new Document();
        doc.put("phone", sub);
        doc.put("_class", TestUser.class.getName());

        converter.testDecryptFields(doc, TestUser.class);
        assertThat(doc.get("phone")).isEqualTo("hello");
    }

    @Test
    void missingFieldDoesNotThrow() {
        Document doc = new Document();
        doc.put("_class", TestUser.class.getName());
        assertThatNoException().isThrownBy(() ->
                converter.testDecryptFields(doc, TestUser.class));
    }

    @Test
    void fullRoundtrip() {
        TypeSerializer ser = createTestTypeSerializer();
        String original = "13800138000";
        byte[] serialized = ser.serialize(original);
        String blob = encryptToBlob(serialized, "default.default.TestUser#phone");

        Document sub = new Document();
        sub.put("c", blob);
        sub.put("_e", 1);
        sub.put("_t", "STR");

        Document doc = new Document();
        doc.put("phone", sub);
        doc.put("_class", TestUser.class.getName());

        converter.testDecryptFields(doc, TestUser.class);
        assertThat(doc.get("phone")).isEqualTo(original);
    }

    @Test
    void decryptIntSubDoc() {
        String blob = encryptToBlob("42".getBytes(StandardCharsets.UTF_8), "default.default.TestEmployee#age");
        Document sub = new Document();
        sub.put("c", blob);
        sub.put("_e", 1);
        sub.put("_t", "INT");

        Document doc = new Document();
        doc.put("age", sub);
        doc.put("_class", TestEmployee.class.getName());

        converter.testDecryptFields(doc, TestEmployee.class);
        assertThat(doc.get("age")).isEqualTo(42);
    }

    @Test
    void decryptLocalDateSubDoc() {
        String blob = encryptToBlob("2024-06-15".getBytes(StandardCharsets.UTF_8), "default.default.TestEmployee#birthDate");
        Document sub = new Document();
        sub.put("c", blob);
        sub.put("_e", 1);
        sub.put("_t", "LDATE");

        Document doc = new Document();
        doc.put("birthDate", sub);
        doc.put("_class", TestEmployee.class.getName());

        converter.testDecryptFields(doc, TestEmployee.class);
        assertThat(doc.get("birthDate")).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    /**
     * Test subclass that exposes decryptFields() for direct unit testing.
     */
    static class TestableConverter extends CryptoMappingMongoConverter {
        TestableConverter(EntityMetadataCache mc, FieldCryptoService fieldCryptoService) {
            super(createMockFactory(), mock(MappingContext.class), mc, fieldCryptoService);
        }

        private static MongoDatabaseFactory createMockFactory() {
            MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
            when(factory.getExceptionTranslator())
                    .thenReturn(new org.springframework.dao.support.PersistenceExceptionTranslator() {
                        @Override
                        public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException ex) {
                            return null;
                        }
                    });
            return factory;
        }

        void testDecryptFields(Document document, Class<?> entityClass) {
            decryptFields(document, entityClass);
        }
    }
}
