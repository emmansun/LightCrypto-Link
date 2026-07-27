package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.config.CryptographyProperties;
import io.github.emmansun.lightcrypto.config.TenantProperties;
import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import io.github.emmansun.lightcrypto.spi.DocumentAccessor;
import io.github.emmansun.lightcrypto.spi.StorageAdapter;
import io.github.emmansun.lightcrypto.spi.StructuredValueCodec;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCryptoServiceUnitTest {

    private static final String NAMESPACE = "default.default.DemoEntity#secret";
    private static final byte[] DEK = fixedKey((byte) 0x2A);

    @Test
    void decryptDocumentRejectsNullInputs() {
        FieldCryptoService service = createServiceWithEmptyMetadata();

        assertThatThrownBy(() -> service.decryptDocument(null, DemoEntity.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawDocument must not be null");

        assertThatThrownBy(() -> service.decryptDocument(new HashMap<>(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityClass must not be null");
    }

    @Test
    void decryptDocumentReturnsInputWhenNoEncryptedFields() {
        FieldCryptoService service = createServiceWithEmptyMetadata();

        Map<String, Object> raw = new HashMap<>();
        raw.put("name", "alice");
        Object out = service.decryptDocument(raw, DemoEntity.class);

        assertThat(out).isSameAs(raw);
    }

        @Test
        void decryptDocumentDecryptsFieldLeaf() {
        EncryptedFieldMetadata meta = metadata(
            List.of("secret"),
            List.of(PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(3, DEK);
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("secret", encryptedPayload("alice", "STR", 3));

        Object out = service.decryptDocument(raw, DemoEntity.class);

        assertThat(out).isSameAs(raw);
        assertThat(raw.get("secret")).isEqualTo("alice");
        assertThat(keyVault.lastNamespace).isEqualTo(NAMESPACE);
        assertThat(keyVault.lastDekVersion).isEqualTo(3);
        }

        @Test
        void decryptDocumentDecryptsNestedFieldLeaf() {
        EncryptedFieldMetadata meta = metadata(
            List.of("profile", "secret"),
            List.of(PathSegmentType.FIELD, PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(4, DEK);
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> profile = new HashMap<>();
        profile.put("secret", encryptedPayload("alice", "STR", 4));
        Map<String, Object> raw = new HashMap<>();
        raw.put("profile", profile);

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(((Map<?, ?>) raw.get("profile")).get("secret")).isEqualTo("alice");
        }

        @Test
        void decryptDocumentDecryptsListElementsAndSkipsPlaintext() {
        EncryptedFieldMetadata meta = metadata(
            List.of("tags"),
            List.of(PathSegmentType.LIST_ITER),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(5, DEK);
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        List<Object> tags = new ArrayList<>();
        tags.add(encryptedPayload("a", "STR", 5));
        tags.add("plain");
        tags.add(encryptedPayload("b", "STR", 5));
        Map<String, Object> raw = new HashMap<>();
        raw.put("tags", tags);

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(tags).containsExactly("a", "plain", "b");
        }

        @Test
        void decryptDocumentDecryptsMapValuesAndSkipsPlaintext() {
        EncryptedFieldMetadata meta = metadata(
            List.of("attrs"),
            List.of(PathSegmentType.MAP_ITER),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(6, DEK);
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("x", encryptedPayload("xv", "STR", 6));
        attrs.put("y", "plain");
        Map<String, Object> raw = new HashMap<>();
        raw.put("attrs", attrs);

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(((Map<?, ?>) raw.get("attrs")).get("x")).isEqualTo("xv");
        assertThat(((Map<?, ?>) raw.get("attrs")).get("y")).isEqualTo("plain");
        }

        @Test
        void decryptDocumentDecryptsWholeObjectListFieldThroughStructuredCodec() {
        EncryptedFieldMetadata meta = metadata(
            List.of("items"),
            List.of(PathSegmentType.LIST_ITER),
            true,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(7, DEK);
        CapturingStructuredValueCodec codec = new CapturingStructuredValueCodec();
        FieldCryptoService service = createService(List.of(meta), keyVault, codec);

        byte[] plaintext = "[1,2,3]".getBytes(StandardCharsets.UTF_8);
        String blob = CryptoCodec.encrypt(DEK, plaintext, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), 7);
        Map<String, Object> payload = new HashMap<>();
        payload.put("_e", 1);
        payload.put("_t", "COL");
        payload.put("c", blob);

        Map<String, Object> raw = new HashMap<>();
        raw.put("items", payload);

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(raw.get("items")).isEqualTo(List.of("decoded", "COL"));
        assertThat(codec.lastTypeMarker).isEqualTo("COL");
        assertThat(codec.lastData).containsExactly(plaintext);
        }

        @Test
        void decryptDocumentWrapsInvalidBlobAsDecryptionException() {
        EncryptedFieldMetadata meta = metadata(
            List.of("secret"),
            List.of(PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FieldCryptoService service = createService(List.of(meta), new FakeKeyVaultService(), new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("secret", Map.of("_e", 1, "_t", "STR", "c", "bad-blob"));

        assertThatThrownBy(() -> service.decryptDocument(raw, DemoEntity.class))
            .isInstanceOf(DecryptionException.class)
            .hasMessageContaining("Invalid Wire Format blob for field 'secret'");
        }

        @Test
        void decryptDocumentWrapsDekResolutionFailureAsKeyManagementException() {
        EncryptedFieldMetadata meta = metadata(
            List.of("secret"),
            List.of(PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.failGetDekByVersion = true;
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("secret", encryptedPayload("alice", "STR", 8));

        assertThatThrownBy(() -> service.decryptDocument(raw, DemoEntity.class))
            .isInstanceOf(KeyManagementException.class)
            .hasMessageContaining("Failed to resolve DEK for field 'secret'");
        }

        @Test
        void decryptDocumentWrapsStructuredDecodeFailure() {
        EncryptedFieldMetadata meta = metadata(
            List.of("secret"),
            List.of(PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(9, DEK);
        FieldCryptoService service = createService(List.of(meta), keyVault, new FailingStructuredValueCodec());

        byte[] plaintext = "{\"name\":\"alice\"}".getBytes(StandardCharsets.UTF_8);
        String blob = CryptoCodec.encrypt(DEK, plaintext, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), 9);

        Map<String, Object> raw = new HashMap<>();
        raw.put("secret", Map.of("_e", 1, "_t", "DOC", "c", blob));

        assertThatThrownBy(() -> service.decryptDocument(raw, DemoEntity.class))
            .isInstanceOf(DecryptionException.class)
            .hasMessageContaining("Failed to decode structured payload for type marker: DOC");
        }

        @Test
        void decryptDocumentSkipsNonEncryptedFieldPayload() {
        EncryptedFieldMetadata meta = metadata(
            List.of("secret"),
            List.of(PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("secret", "plain-text");

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(raw.get("secret")).isEqualTo("plain-text");
        assertThat(keyVault.lastNamespace).isNull();
        }

        @Test
        void decryptDocumentSkipsEncryptedPayloadWhenBlobMissing() {
        EncryptedFieldMetadata meta = metadata(
            List.of("secret"),
            List.of(PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("secret", Map.of("_e", 1, "_t", "STR"));

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(raw.get("secret")).isEqualTo(Map.of("_e", 1, "_t", "STR"));
        assertThat(keyVault.lastNamespace).isNull();
        }

        @Test
        void decryptDocumentSkipsListIterWhenTargetIsNotList() {
        EncryptedFieldMetadata meta = metadata(
            List.of("tags"),
            List.of(PathSegmentType.LIST_ITER),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("tags", "not-a-list");

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(raw.get("tags")).isEqualTo("not-a-list");
        }

        @Test
        void decryptDocumentSkipsMapIterWhenTargetIsNotDocumentLike() {
        EncryptedFieldMetadata meta = metadata(
            List.of("attrs"),
            List.of(PathSegmentType.MAP_ITER),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("attrs", "not-a-map");

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(raw.get("attrs")).isEqualTo("not-a-map");
        }

        @Test
        void decryptDocumentSkipsNestedFieldWhenIntermediateIsNotDocumentLike() {
        EncryptedFieldMetadata meta = metadata(
            List.of("profile", "secret"),
            List.of(PathSegmentType.FIELD, PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("profile", "plain-profile");

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(raw.get("profile")).isEqualTo("plain-profile");
        assertThat(keyVault.lastNamespace).isNull();
        }

        @Test
        void decryptDocumentDecryptsNestedListElementsAndSkipsNonDocumentElements() {
        EncryptedFieldMetadata meta = metadata(
            List.of("items", "secret"),
            List.of(PathSegmentType.LIST_ITER, PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(13, DEK);
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> item0 = new HashMap<>();
        item0.put("secret", encryptedPayload("alice", "STR", 13));
        List<Object> items = new ArrayList<>();
        items.add(item0);
        items.add("plain-item");

        Map<String, Object> raw = new HashMap<>();
        raw.put("items", items);

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(((Map<?, ?>) items.get(0)).get("secret")).isEqualTo("alice");
        assertThat(items.get(1)).isEqualTo("plain-item");
        }

        @Test
        void decryptDocumentDecryptsNestedMapValuesAndSkipsNonDocumentValues() {
        EncryptedFieldMetadata meta = metadata(
            List.of("attrs", "secret"),
            List.of(PathSegmentType.MAP_ITER, PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(14, DEK);
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> nested = new HashMap<>();
        nested.put("secret", encryptedPayload("xv", "STR", 14));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("x", nested);
        attrs.put("y", "plain-value");

        Map<String, Object> raw = new HashMap<>();
        raw.put("attrs", attrs);

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(((Map<?, ?>) ((Map<?, ?>) raw.get("attrs")).get("x")).get("secret")).isEqualTo("xv");
        assertThat(((Map<?, ?>) raw.get("attrs")).get("y")).isEqualTo("plain-value");
        }

        @Test
        void decryptDocumentSkipsMapIterWhenAsMapReturnsNull() {
        EncryptedFieldMetadata meta = metadata(
            List.of("attrs"),
            List.of(PathSegmentType.MAP_ITER),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        FieldCryptoService service = createService(
            List.of(meta),
            keyVault,
            new NoOpStructuredValueCodec(),
            new NullAsMapDocumentAccessor());

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("x", encryptedPayload("xv", "STR", 14));
        Map<String, Object> raw = new HashMap<>();
        raw.put("attrs", attrs);

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(((Map<?, ?>) raw.get("attrs")).get("x")).isEqualTo(attrs.get("x"));
        }

        @Test
        void decryptDocumentDecryptsWholeObjectMapFieldThroughStructuredCodec() {
        EncryptedFieldMetadata meta = metadata(
            List.of("attrs"),
            List.of(PathSegmentType.MAP_ITER),
            true,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(10, DEK);
        CapturingStructuredValueCodec codec = new CapturingStructuredValueCodec();
        FieldCryptoService service = createService(List.of(meta), keyVault, codec);

        byte[] plaintext = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        String blob = CryptoCodec.encrypt(DEK, plaintext, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), 10);
        Map<String, Object> payload = new HashMap<>();
        payload.put("_e", 1);
        payload.put("_t", "MAP");
        payload.put("c", blob);

        Map<String, Object> raw = new HashMap<>();
        raw.put("attrs", payload);

        service.decryptDocument(raw, DemoEntity.class);

        assertThat(raw.get("attrs")).isEqualTo(List.of("decoded", "MAP"));
        assertThat(codec.lastTypeMarker).isEqualTo("MAP");
        assertThat(codec.lastData).containsExactly(plaintext);
        }

        @Test
        void decryptDocumentWrapsDecryptFailureAsDecryptionException() {
        EncryptedFieldMetadata meta = metadata(
            List.of("secret"),
            List.of(PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        byte[] wrongDek = fixedKey((byte) 0x3B);
        keyVault.dekByVersion.put(11, wrongDek);
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("secret", encryptedPayload("alice", "STR", 11));

        assertThatThrownBy(() -> service.decryptDocument(raw, DemoEntity.class))
            .isInstanceOf(DecryptionException.class)
            .hasMessageContaining("Failed to decrypt field 'secret'");
        }

        @Test
        void decryptDocumentWrapsDeserializeFailureAsDecryptionException() {
        EncryptedFieldMetadata meta = metadata(
            List.of("secret"),
            List.of(PathSegmentType.FIELD),
            false,
            NAMESPACE);
        FakeKeyVaultService keyVault = new FakeKeyVaultService();
        keyVault.dekByVersion.put(12, DEK);
        FieldCryptoService service = createService(List.of(meta), keyVault, new NoOpStructuredValueCodec());

        Map<String, Object> raw = new HashMap<>();
        raw.put("secret", encryptedPayload("alice", "UNKNOWN", 12));

        assertThatThrownBy(() -> service.decryptDocument(raw, DemoEntity.class))
            .isInstanceOf(DecryptionException.class)
            .hasMessageContaining("Failed to deserialize field 'secret'")
            .hasMessageContaining("type marker 'UNKNOWN'");
        }

    private static FieldCryptoService createServiceWithEmptyMetadata() {
        EntityMetadataCache metadataCache = new EntityMetadataCache(
            new CryptographyProperties(),
            new TenantProperties());

        return new FieldCryptoService(
                metadataCache,
                new TypeDeserializer(),
            null,
            null,
            null,
            null);
    }

    private static FieldCryptoService createService(List<EncryptedFieldMetadata> metadata,
                                                    KeyVaultService keyVaultService,
                                                    StructuredValueCodec structuredValueCodec) {
        EntityMetadataCache metadataCache = new StubEntityMetadataCache(metadata);
        return new FieldCryptoService(
                metadataCache,
                new TypeDeserializer(),
                keyVaultService,
                new MapStorageAdapter(),
                new MapDocumentAccessor(),
                structuredValueCodec);
    }

    private static FieldCryptoService createService(List<EncryptedFieldMetadata> metadata,
                                                    KeyVaultService keyVaultService,
                                                    StructuredValueCodec structuredValueCodec,
                                                    DocumentAccessor documentAccessor) {
        EntityMetadataCache metadataCache = new StubEntityMetadataCache(metadata);
        return new FieldCryptoService(
                metadataCache,
                new TypeDeserializer(),
                keyVaultService,
                new MapStorageAdapter(),
                documentAccessor,
                structuredValueCodec);
    }

    private static EncryptedFieldMetadata metadata(List<String> path,
                                                   List<PathSegmentType> pathTypes,
                                                   boolean wholeObject,
                                                   String namespace) {
        return new EncryptedFieldMetadata(
                List.<MethodHandle>of(),
                path,
                pathTypes,
                String.class,
                SymmetricAlgorithm.AES_256_GCM,
                false,
                wholeObject,
                String.join(".", path),
                Namespace.parse(namespace));
    }

    private static Map<String, Object> encryptedPayload(String value, String typeMarker, int dekVersion) {
        TypeSerializer serializer = new TypeSerializer();
        byte[] serialized = serializer.serialize(value);
        String blob = CryptoCodec.encrypt(DEK, serialized, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), dekVersion);
        Map<String, Object> payload = new HashMap<>();
        payload.put("_e", 1);
        payload.put("_t", typeMarker);
        payload.put("c", blob);
        return payload;
    }

    private static byte[] fixedKey(byte value) {
        byte[] out = new byte[32];
        Arrays.fill(out, value);
        return out;
    }

    private static final class StubEntityMetadataCache extends EntityMetadataCache {
        private final List<EncryptedFieldMetadata> metadata;

        private StubEntityMetadataCache(List<EncryptedFieldMetadata> metadata) {
            super(new CryptographyProperties(), new TenantProperties());
            this.metadata = metadata;
        }

        @Override
        public List<EncryptedFieldMetadata> getEncryptedFields(Class<?> entityClass) {
            return metadata;
        }
    }

    private static final class MapStorageAdapter implements StorageAdapter {
        @Override
        public Object buildEncryptedPayload(String blob, String typeMarker, String blindIndex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("_e", 1);
            payload.put("_t", typeMarker);
            payload.put("c", blob);
            if (blindIndex != null) {
                payload.put("b", blindIndex);
            }
            return payload;
        }

        @Override
        public String extractBlob(Object payload) {
            if (!(payload instanceof Map<?, ?> map)) {
                return null;
            }
            Object blob = map.get("c");
            return blob instanceof String ? (String) blob : null;
        }

        @Override
        public String extractTypeMarker(Object payload) {
            if (!(payload instanceof Map<?, ?> map)) {
                return null;
            }
            Object marker = map.get("_t");
            return marker instanceof String ? (String) marker : null;
        }

        @Override
        public String extractBlindIndex(Object payload) {
            if (!(payload instanceof Map<?, ?> map)) {
                return null;
            }
            Object blindIndex = map.get("b");
            return blindIndex instanceof String ? (String) blindIndex : null;
        }

        @Override
        public boolean isEncryptedPayload(Object value) {
            return value instanceof Map<?, ?> map && Integer.valueOf(1).equals(map.get("_e"));
        }
    }

    private static final class MapDocumentAccessor implements DocumentAccessor {
        @Override
        public Object getField(Object document, String field) {
            if (!(document instanceof Map<?, ?> map)) {
                return null;
            }
            return map.get(field);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void setField(Object document, String field, Object value) {
            if (document instanceof Map<?, ?> map) {
                ((Map<String, Object>) map).put(field, value);
            }
        }

        @Override
        public boolean isDocumentLike(Object value) {
            return value instanceof Map<?, ?>;
        }

        @Override
        public Iterable<?> asList(Object value) {
            return value instanceof List<?> list ? list : null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Iterable<Map.Entry<String, Object>> asMap(Object value) {
            if (value instanceof Map<?, ?> map) {
                return ((Map<String, Object>) map).entrySet();
            }
            return null;
        }
    }

    private static final class NullAsMapDocumentAccessor implements DocumentAccessor {
        @Override
        public Object getField(Object document, String field) {
            if (!(document instanceof Map<?, ?> map)) {
                return null;
            }
            return map.get(field);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void setField(Object document, String field, Object value) {
            if (document instanceof Map<?, ?> map) {
                ((Map<String, Object>) map).put(field, value);
            }
        }

        @Override
        public boolean isDocumentLike(Object value) {
            return value instanceof Map<?, ?>;
        }

        @Override
        public Iterable<?> asList(Object value) {
            return value instanceof List<?> list ? list : null;
        }

        @Override
        public Iterable<Map.Entry<String, Object>> asMap(Object value) {
            return null;
        }
    }

    private static final class FakeKeyVaultService extends KeyVaultService {
        private final Map<Integer, byte[]> dekByVersion = new HashMap<>();
        private String lastNamespace;
        private int lastDekVersion;
        private boolean failGetDekByVersion;

        private FakeKeyVaultService() {
            super(null, null, null);
        }

        @Override
        public void ensureVaultInitialized(String namespace) {
            this.lastNamespace = namespace;
        }

        @Override
        public byte[] getDekByVersion(String namespace, int dekVersion) {
            this.lastNamespace = namespace;
            this.lastDekVersion = dekVersion;
            if (failGetDekByVersion) {
                throw new FatalCryptoException("dek lookup failed");
            }
            byte[] dek = dekByVersion.get(dekVersion);
            if (dek == null) {
                throw new FatalCryptoException("No key found");
            }
            return dek;
        }
    }

    private static class NoOpStructuredValueCodec implements StructuredValueCodec {
        @Override
        public byte[] encode(Object structuredValue, String typeMarker) {
            return new byte[0];
        }

        @Override
        public Object decode(byte[] data, String typeMarker) {
            return List.of("decoded", typeMarker);
        }
    }

    private static final class CapturingStructuredValueCodec extends NoOpStructuredValueCodec {
        private byte[] lastData;
        private String lastTypeMarker;

        @Override
        public Object decode(byte[] data, String typeMarker) {
            this.lastData = data.clone();
            this.lastTypeMarker = typeMarker;
            return super.decode(data, typeMarker);
        }
    }

    private static final class FailingStructuredValueCodec extends NoOpStructuredValueCodec {
        @Override
        public Object decode(byte[] data, String typeMarker) {
            throw new IllegalStateException("decode failed");
        }
    }

    private static final class DemoEntity {
        private DemoEntity() {
        }
    }
}
