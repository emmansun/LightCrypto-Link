package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.service.CryptoCodec;
import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import io.github.emmansun.lightcrypto.service.ProgrammaticCryptoService;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import org.bson.BsonBinaryWriter;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgrammaticCryptoServiceTest extends LclTestBase {

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();

    private ProgrammaticCryptoService api;
    private CryptoCodec codec;
    private TypeSerializer serializer;
    private TypeDeserializer deserializer;
    private TestKeyVaultService keyVaultService;
    private FieldCryptoService fieldCryptoService;
    private String activeKid;

    @BeforeEach
    void setup() {
        codec = createTestCryptoCodec();
        serializer = createTestTypeSerializer();
        deserializer = createTestTypeDeserializer();
        keyVaultService = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        fieldCryptoService = new FieldCryptoService(
                new EntityMetadataCache(new CryptoProperties()),
                codec,
                deserializer,
                keyVaultService);
        keyVaultService.ensureVaultInitialized(TestUser.class);
        activeKid = keyVaultService.getActiveKid(TestUser.class);

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

    @Test
    void encryptValueRejectsInvalidArguments() {
        assertThatThrownBy(() -> api.encryptValue(null, TestUser.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value must not be null");

        assertThatThrownBy(() -> api.encryptValue("hello", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyScopeClass must not be null");

        assertThatThrownBy(() -> api.encryptValue("hello", TestUser.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm must not be null");
    }

    @Test
    void decryptValueRejectsNullAndMissingRequiredFields() {
        assertThatThrownBy(() -> api.decryptValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encryptedSubDocument must not be null");

        assertThatThrownBy(() -> api.decryptValue(new Document("_e", 1).append("_t", "STR").append("c", new Binary(new byte[]{1}))))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing '_k'");

        assertThatThrownBy(() -> api.decryptValue(new Document("_e", 1).append("_k", activeKid).append("c", new Binary(new byte[]{1}))))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing '_t'");

        assertThatThrownBy(() -> api.decryptValue(new Document("_e", 1).append("_k", activeKid).append("_t", "STR")))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing 'c'");
    }

    @Test
    void decryptValueRejectsUnsupportedAlgorithmName() {
        Document encrypted = api.encryptValue("hello", TestUser.class);
        encrypted.put("_a", "INVALID_ALGO");

        assertThatThrownBy(() -> api.decryptValue(encrypted))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Unsupported algorithm");
    }

    @Test
    void decryptValueFallsBackToDefaultAlgorithmWhenMissing() {
        Document encrypted = api.encryptValue("hello", TestUser.class);
        encrypted.remove("_a");

        assertThat(api.decryptValue(encrypted)).isEqualTo("hello");
    }

    @Test
    void decryptValueWrapsUnknownKidAsKeyManagementException() {
        Document encrypted = api.encryptValue("hello", TestUser.class);
        encrypted.put("_k", "v9-unknown");

        assertThatThrownBy(() -> api.decryptValue(encrypted))
                .isInstanceOf(KeyManagementException.class)
                .hasMessageContaining("Failed to resolve DEK");
    }

    @Test
    void encryptValueWrapsKeyVaultFailuresAsKeyManagementException() {
        TestKeyVaultService failingKeyVault = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY) {
            @Override
            public byte[] getDek(String kid) {
                throw new IllegalStateException("boom");
            }
        };
        ProgrammaticCryptoService failingApi = new ProgrammaticCryptoService(
                codec,
                serializer,
                deserializer,
                failingKeyVault,
                fieldCryptoService);

        assertThatThrownBy(() -> failingApi.encryptValue("hello", TestUser.class))
                .isInstanceOf(KeyManagementException.class)
                .hasMessageContaining("Failed to resolve DEK for key scope 'TestUser'");
    }

    @Test
    void decryptValueMasksShortKidInKeyManagementErrorMessage() {
        TestKeyVaultService failingKeyVault = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY) {
            @Override
            public byte[] getDek(String kid) {
                throw new IllegalStateException("boom");
            }
        };
        ProgrammaticCryptoService failingApi = new ProgrammaticCryptoService(
                codec,
                serializer,
                deserializer,
                failingKeyVault,
                fieldCryptoService);
        byte[] encrypted = codec.encrypt(TEST_DEK, "hello".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document subDoc = buildSubDoc(encrypted, "STR", "abc", SymmetricAlgorithm.AES_256_GCM.name());

        assertThatThrownBy(() -> failingApi.decryptValue(subDoc))
                .isInstanceOf(KeyManagementException.class)
                .hasMessageContaining("kid=****");
    }

    @Test
    void decryptValueWrapsCipherFailure() {
        ProgrammaticCryptoService failingApi = new ProgrammaticCryptoService(
                new CryptoCodec() {
                    @Override
                    public byte[] decrypt(byte[] dek, byte[] data, SymmetricAlgorithm algorithm) {
                        throw new IllegalStateException("boom");
                    }
                },
                serializer,
                deserializer,
                keyVaultService,
                fieldCryptoService);

        Document encrypted = api.encryptValue("hello", TestUser.class);

        assertThatThrownBy(() -> failingApi.decryptValue(encrypted))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to decrypt value");
    }

    @Test
    void decryptValueWrapsDeserializeFailure() {
        byte[] encrypted = codec.encrypt(TEST_DEK, "not-an-int".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document subDoc = buildSubDoc(encrypted, "INT", activeKid, SymmetricAlgorithm.AES_256_GCM.name());

        assertThatThrownBy(() -> api.decryptValue(subDoc))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to deserialize");
    }

    @Test
    void decryptValueSupportsStructuredDocMapAndCollectionMarkers() {
        Document payload = new Document("city", "shanghai");
        byte[] encryptedDoc = codec.encrypt(TEST_DEK, encodePayload(payload), SymmetricAlgorithm.AES_256_GCM);
        Document docSub = buildSubDoc(encryptedDoc, "DOC", activeKid, SymmetricAlgorithm.AES_256_GCM.name());
        Document mapSub = buildSubDoc(encryptedDoc, "MAP", activeKid, SymmetricAlgorithm.AES_256_GCM.name());

        Document listPayload = new Document("_v", List.of("a", "b"));
        byte[] encryptedList = codec.encrypt(TEST_DEK, encodePayload(listPayload), SymmetricAlgorithm.AES_256_GCM);
        Document colSub = buildSubDoc(encryptedList, "COL", activeKid, SymmetricAlgorithm.AES_256_GCM.name());

        assertThat(api.decryptValue(docSub)).isInstanceOf(Document.class);
        assertThat(((Document) api.decryptValue(docSub)).getString("city")).isEqualTo("shanghai");
        assertThat(((Document) api.decryptValue(mapSub)).getString("city")).isEqualTo("shanghai");
        assertThat(api.decryptValue(colSub)).isEqualTo(List.of("a", "b"));
    }

    @Test
    void decryptValueReportsStructuredPayloadDecodeFailure() {
        byte[] encrypted = codec.encrypt(TEST_DEK, "not-bson".getBytes(StandardCharsets.UTF_8), SymmetricAlgorithm.AES_256_GCM);
        Document subDoc = buildSubDoc(encrypted, "DOC", activeKid, SymmetricAlgorithm.AES_256_GCM.name());

        assertThatThrownBy(() -> api.decryptValue(subDoc))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to decode structured payload");
    }

    private Document buildSubDoc(byte[] cipherBytes, String typeMarker, String kid, String algorithm) {
        Document sub = new Document();
        sub.put("c", new Binary(cipherBytes));
        sub.put("_e", 1);
        sub.put("_t", typeMarker);
        sub.put("_k", kid);
        if (algorithm != null) {
            sub.put("_a", algorithm);
        }
        return sub;
    }

    private byte[] encodePayload(Document payload) {
        BasicOutputBuffer buffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
        DOCUMENT_CODEC.encode(writer, payload, EncoderContext.builder().build());
        writer.flush();
        return buffer.toByteArray();
    }
}
