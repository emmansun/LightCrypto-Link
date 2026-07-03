package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.listener.CryptoMappingMongoConverter;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.bson.Document;
import org.bson.types.Binary;
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
 * Uses TestableConverter to expose decryptFields() without depending on a real MappingContext.
 */
class CryptoBeforeConvertListenerTest extends LclTestBase {

    private TestableConverter converter;
    private CryptoCodec codec;

    @BeforeEach
    void setup() {
        EntityMetadataCache mc = new EntityMetadataCache(new CryptoProperties());
        codec = createTestCryptoCodec();
        TypeDeserializer des = createTestTypeDeserializer();
        KeyVaultService vs = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        converter = new TestableConverter(mc, codec, des, vs);
    }

    private static final String TEST_KID = "v1-test0001";

    @Test
    void decryptStringSubDoc() {
        byte[] enc = codec.encrypt(TEST_DEK, "hello".getBytes(StandardCharsets.UTF_8));
        Document sub = new Document();
        sub.put("c", new Binary(enc));
        sub.put("_e", 1);
        sub.put("_t", "STR");
        sub.put("_k", TEST_KID);

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
        byte[] enc = codec.encrypt(TEST_DEK, serialized);

        Document sub = new Document();
        sub.put("c", new Binary(enc));
        sub.put("_e", 1);
        sub.put("_t", "STR");
        sub.put("_k", TEST_KID);

        Document doc = new Document();
        doc.put("phone", sub);
        doc.put("_class", TestUser.class.getName());

        converter.testDecryptFields(doc, TestUser.class);
        assertThat(doc.get("phone")).isEqualTo(original);
    }

    @Test
    void decryptIntSubDoc() {
        byte[] enc = codec.encrypt(TEST_DEK, "42".getBytes(StandardCharsets.UTF_8));
        Document sub = new Document();
        sub.put("c", new Binary(enc));
        sub.put("_e", 1);
        sub.put("_t", "INT");
        sub.put("_k", TEST_KID);

        Document doc = new Document();
        doc.put("age", sub);
        doc.put("_class", TestEmployee.class.getName());

        converter.testDecryptFields(doc, TestEmployee.class);
        assertThat(doc.get("age")).isEqualTo(42);
    }

    @Test
    void decryptLocalDateSubDoc() {
        byte[] enc = codec.encrypt(TEST_DEK, "2024-06-15".getBytes(StandardCharsets.UTF_8));
        Document sub = new Document();
        sub.put("c", new Binary(enc));
        sub.put("_e", 1);
        sub.put("_t", "LDATE");
        sub.put("_k", TEST_KID);

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
        TestableConverter(EntityMetadataCache mc, CryptoCodec codec,
                          TypeDeserializer des, KeyVaultService vs) {
            super(createMockFactory(), mock(MappingContext.class), mc, codec, des, vs);
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
