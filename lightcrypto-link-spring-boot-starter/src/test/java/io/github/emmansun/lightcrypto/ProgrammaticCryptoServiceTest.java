package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import io.github.emmansun.lightcrypto.service.ProgrammaticCryptoService;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgrammaticCryptoServiceTest extends LclTestBase {

    private ProgrammaticCryptoService api;

    @BeforeEach
    void setup() {
        CryptoCodec codec = createTestCryptoCodec();
        TypeSerializer serializer = createTestTypeSerializer();
        TypeDeserializer deserializer = createTestTypeDeserializer();
        TestKeyVaultService keyVaultService = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        FieldCryptoService fieldCryptoService = new FieldCryptoService(
                new EntityMetadataCache(new CryptoProperties()),
                codec,
                deserializer,
                keyVaultService);

        api = new ProgrammaticCryptoService(codec, serializer, deserializer, keyVaultService, fieldCryptoService);
    }

    @Test
    void encryptAndDecryptValueWithDefaultAlgorithm() {
        Document subDoc = api.encryptValue("13800138000", TestUser.class);

        assertThat(subDoc.getInteger("_e")).isEqualTo(1);
        assertThat(subDoc.getString("_t")).isEqualTo("STR");
        assertThat(subDoc.getString("_a")).isEqualTo("AES_256_GCM");
        assertThat(subDoc.getString("_k")).isNotBlank();
        assertThat(subDoc.get("c", Binary.class)).isNotNull();

        Object plain = api.decryptValue(subDoc);
        assertThat(plain).isEqualTo("13800138000");
    }

    @Test
    void encryptAndDecryptValueWithExplicitAlgorithm() {
        Document subDoc = api.encryptValue("hello", TestUser.class, SymmetricAlgorithm.AES_256_CBC);

        assertThat(subDoc.getString("_a")).isEqualTo("AES_256_CBC");
        assertThat(api.decryptValue(subDoc)).isEqualTo("hello");
    }

    @Test
    void decryptValueRejectsNonEncryptedDocument() {
        Document doc = new Document("foo", "bar");

        assertThatThrownBy(() -> api.decryptValue(doc))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("_e=1");
    }

    @Test
    void decryptDocumentDelegatesToFieldCryptoService() {
        Document encryptedPhone = api.encryptValue("13800138000", TestUser.class);
        Document raw = new Document("name", "alice")
                .append("phone", encryptedPhone);

        Document decrypted = api.decryptDocument(raw, TestUser.class);
        assertThat(decrypted.getString("phone")).isEqualTo("13800138000");
        assertThat(decrypted.getString("name")).isEqualTo("alice");
    }
}
