package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgrammaticCryptoServiceTest extends MongoAdapterTestBase {

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();
    private static final String TEST_NAMESPACE = "default.default.TestUser#_default";

    private ProgrammaticCryptoService api;
    private TypeSerializer serializer;
    private TypeDeserializer deserializer;
    private TestKeyVaultService keyVaultService;
    private FieldCryptoService fieldCryptoService;

    @BeforeEach
    void setup() {
        serializer = createTestTypeSerializer();
        deserializer = createTestTypeDeserializer();
        keyVaultService = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY);
        fieldCryptoService = new FieldCryptoService(
                new EntityMetadataCache(new CryptoProperties()),
                deserializer,
                keyVaultService,
                createTestStorageAdapter(),
                createTestDocumentAccessor(),
                createTestStructuredValueCodec());

        api = new ProgrammaticCryptoService(serializer, deserializer, keyVaultService, fieldCryptoService, createTestStructuredValueCodec());
    }

    @Test
    void encryptAndDecryptValueWithDefaultAlgorithm() {
        Map<String, Object> subDoc = (Map<String, Object>) api.encryptValue("13800138000", TestUser.class);

        assertThat(subDoc.get("_e")).isEqualTo(1);
        assertThat(subDoc.get("_t")).isEqualTo("STR");
        assertThat((String) subDoc.get("c")).isNotBlank();
        // Wire Format V1: no _k or _a fields
        assertThat(subDoc.containsKey("_k")).isFalse();
        assertThat(subDoc.containsKey("_a")).isFalse();

        Object plain = api.decryptValue(subDoc);
        assertThat(plain).isEqualTo("13800138000");
    }

    @Test
    void encryptAndDecryptValueWithExplicitAlgorithm() {
        Map<String, Object> subDoc = (Map<String, Object>) api.encryptValue("hello", TEST_NAMESPACE, AlgorithmId.AES_256_CBC);

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
        Map<String, Object> encryptedPhone = (Map<String, Object>) api.encryptValue("13800138000", TestUser.class);
        Document raw = new Document("name", "alice")
                .append("phone", new Document(encryptedPhone));

        Document decrypted = (Document) api.decryptDocument(raw, TestUser.class);
        assertThat(decrypted.getString("phone")).isEqualTo("13800138000");
        assertThat(decrypted.getString("name")).isEqualTo("alice");
    }

    @Test
    void encryptValueRejectsInvalidArguments() {
        assertThatThrownBy(() -> api.encryptValue(null, TestUser.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value must not be null");

        assertThatThrownBy(() -> api.encryptValue("hello", (String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace must not be null");

        assertThatThrownBy(() -> api.encryptValue("hello", TEST_NAMESPACE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm must not be null");
    }

    @Test
    void decryptValueRejectsNullAndMissingRequiredFields() {
        assertThatThrownBy(() -> api.decryptValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encryptedSubDocument must not be null");

        // Missing _t
        assertThatThrownBy(() -> api.decryptValue(new Document("_e", 1).append("c", "some-blob")))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing '_t'");

        // Missing c
        assertThatThrownBy(() -> api.decryptValue(new Document("_e", 1).append("_t", "STR")))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing 'c'");
    }

    @Test
    void decryptValueRejectsInvalidWireFormatBlob() {
        Document subDoc = new Document("_e", 1).append("_t", "STR").append("c", "not-valid-base64url!!!");

        assertThatThrownBy(() -> api.decryptValue(subDoc))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Invalid Wire Format");
    }

    @Test
    void decryptValueWrapsKeyVaultFailureAsKeyManagementException() {
        TestKeyVaultService failingKeyVault = new TestKeyVaultService(TEST_DEK, TEST_HMAC_KEY) {
            @Override
            public byte[] getDekByVersion(String namespace, int dekVersion) {
                throw new IllegalStateException("boom");
            }
        };
        ProgrammaticCryptoService failingApi = new ProgrammaticCryptoService(
                serializer, deserializer, failingKeyVault, fieldCryptoService, createTestStructuredValueCodec());

        // Encrypt with working service
        Map<String, Object> encrypted = (Map<String, Object>) api.encryptValue("hello", TestUser.class);

        assertThatThrownBy(() -> failingApi.decryptValue(encrypted))
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
                serializer, deserializer, failingKeyVault, fieldCryptoService, createTestStructuredValueCodec());

        assertThatThrownBy(() -> failingApi.encryptValue("hello", TestUser.class))
                .isInstanceOf(KeyManagementException.class)
                .hasMessageContaining("Failed to resolve DEK for namespace");
    }

    @Test
    void decryptValueWrapsCipherFailure() {
        // Encrypt with a different DEK so decryption with TEST_DEK fails
        Namespace ns = Namespace.parse(TEST_NAMESPACE);
        byte[] wrongDek = new byte[32]; // all zeros
        String blob = CryptoCodec.encrypt(wrongDek, "hello".getBytes(StandardCharsets.UTF_8),
                AlgorithmId.AES_256_GCM, ns, 1);
        // Corrupt the ciphertext to ensure decryption failure
        String corruptedBlob = blob.substring(0, blob.length() - 4) + "AAAA";
        Document subDoc = new Document("_e", 1).append("_t", "STR").append("c", corruptedBlob);

        assertThatThrownBy(() -> api.decryptValue(subDoc))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to decrypt value");
    }

    @Test
    void decryptValueWrapsDeserializeFailure() {
        // Encrypt "not-an-int" but mark as INT type
        Namespace ns = Namespace.parse(TEST_NAMESPACE);
        String blob = CryptoCodec.encrypt(TEST_DEK, "not-an-int".getBytes(StandardCharsets.UTF_8),
                AlgorithmId.AES_256_GCM, ns, 1);
        Document subDoc = new Document("_e", 1).append("_t", "INT").append("c", blob);

        assertThatThrownBy(() -> api.decryptValue(subDoc))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to deserialize");
    }

    @Test
    void decryptValueSupportsStructuredDocMapAndCollectionMarkers() {
        Document payload = new Document("city", "shanghai");
        byte[] payloadBytes = encodePayload(payload);
        Namespace ns = Namespace.parse(TEST_NAMESPACE);
        String blob = CryptoCodec.encrypt(TEST_DEK, payloadBytes, AlgorithmId.AES_256_GCM, ns, 1);

        Document docSub = new Document("_e", 1).append("_t", "DOC").append("c", blob);
        Document mapSub = new Document("_e", 1).append("_t", "MAP").append("c", blob);

        Document listPayload = new Document("_v", List.of("a", "b"));
        byte[] listBytes = encodePayload(listPayload);
        String listBlob = CryptoCodec.encrypt(TEST_DEK, listBytes, AlgorithmId.AES_256_GCM, ns, 1);
        Document colSub = new Document("_e", 1).append("_t", "COL").append("c", listBlob);

        assertThat(api.decryptValue(docSub)).isInstanceOf(Document.class);
        assertThat(((Document) api.decryptValue(docSub)).getString("city")).isEqualTo("shanghai");
        assertThat(((Document) api.decryptValue(mapSub)).getString("city")).isEqualTo("shanghai");
        assertThat(api.decryptValue(colSub)).isEqualTo(List.of("a", "b"));
    }

    @Test
    void decryptValueReportsStructuredPayloadDecodeFailure() {
        Namespace ns = Namespace.parse(TEST_NAMESPACE);
        String blob = CryptoCodec.encrypt(TEST_DEK, "not-bson".getBytes(StandardCharsets.UTF_8),
                AlgorithmId.AES_256_GCM, ns, 1);
        Document subDoc = new Document("_e", 1).append("_t", "DOC").append("c", blob);

        assertThatThrownBy(() -> api.decryptValue(subDoc))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("Failed to decode structured payload");
    }

    private byte[] encodePayload(Document payload) {
        BasicOutputBuffer buffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
        DOCUMENT_CODEC.encode(writer, payload, EncoderContext.builder().build());
        writer.flush();
        return buffer.toByteArray();
    }
}
