package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import io.github.emmansun.lightcrypto.exception.FatalCryptoException;
import io.github.emmansun.lightcrypto.exception.KeyManagementException;
import io.github.emmansun.lightcrypto.spi.StructuredValueCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgrammaticCryptoServiceUnitTest {

        private static final String NAMESPACE = "default.default.Test#field";
        private static final byte[] DEK = fixedKey((byte) 0x2A);

    @Test
    void encryptValueRejectsInvalidArguments() {
                ProgrammaticCryptoService service = createService(new FakeKeyVaultService(), new NoOpStructuredValueCodec());

        assertThatThrownBy(() -> service.encryptValue(null, "default.default.Test#field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value must not be null");

        assertThatThrownBy(() -> service.encryptValue("v", (String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace must not be null");

        assertThatThrownBy(() -> service.encryptValue("v", "default.default.Test#field", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm must not be null");
    }

    @Test
    void decryptValueRejectsNullAndNonMap() {
                ProgrammaticCryptoService service = createService(new FakeKeyVaultService(), new NoOpStructuredValueCodec());

        assertThatThrownBy(() -> service.decryptValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encryptedSubDocument must not be null");

        assertThatThrownBy(() -> service.decryptValue("not-a-map"))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("must be a Map-like object");
    }

    @Test
    void decryptValueRejectsMissingRequiredFields() {
                ProgrammaticCryptoService service = createService(new FakeKeyVaultService(), new NoOpStructuredValueCodec());

        assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 1, "c", "abc")))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing '_t'");

        assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 1, "_t", "STR")))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing 'c'");
    }

        @Test
        void encryptValueSuccessProducesExpectedEnvelope() {
                FakeKeyVaultService keyVault = new FakeKeyVaultService();
                keyVault.activeDek = DEK;
                keyVault.activeKid = "kid-1";
                keyVault.activeDekVersion = 3;
                ProgrammaticCryptoService service = createService(keyVault, new NoOpStructuredValueCodec());

                Object encrypted = service.encryptValue("alice", NAMESPACE, AlgorithmId.AES_256_GCM);

                assertThat(encrypted).isInstanceOf(Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> out = (Map<String, Object>) encrypted;
                assertThat(out.get("_e")).isEqualTo(1);
                assertThat(out.get("_t")).isEqualTo("STR");
                assertThat(out.get("c")).isInstanceOf(String.class);
                assertThat(keyVault.lastEnsuredNamespace).isEqualTo(NAMESPACE);
                assertThat(keyVault.lastRequestedKid).isEqualTo("kid-1");
        }

        @Test
        void encryptValueWithScopeClassUsesDefaultNamespaceFormat() {
                FakeKeyVaultService keyVault = new FakeKeyVaultService();
                keyVault.activeDek = DEK;
                keyVault.activeKid = "kid-1";
                ProgrammaticCryptoService service = createService(keyVault, new NoOpStructuredValueCodec());

                service.encryptValue("alice", DemoScope.class);

                assertThat(keyVault.lastEnsuredNamespace).isEqualTo("default.default.DemoScope#_default");
        }

        @Test
        void encryptValueWrapsDekResolutionFailure() {
                FakeKeyVaultService keyVault = new FakeKeyVaultService();
                keyVault.failGetDek = true;
                ProgrammaticCryptoService service = createService(keyVault, new NoOpStructuredValueCodec());

                assertThatThrownBy(() -> service.encryptValue("alice", NAMESPACE, AlgorithmId.AES_256_GCM))
                                .isInstanceOf(KeyManagementException.class)
                                .hasMessageContaining("Failed to resolve DEK for namespace");
        }

        @Test
        void decryptValueRoundTripScalarSuccess() {
                FakeKeyVaultService keyVault = new FakeKeyVaultService();
                keyVault.dekByVersion.put(5, DEK);
                ProgrammaticCryptoService service = createService(keyVault, new NoOpStructuredValueCodec());

                byte[] serialized = new TypeSerializer().serialize("alice");
                String blob = CryptoCodec.encrypt(DEK, serialized, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), 5);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("_e", 1);
                payload.put("_t", "STR");
                payload.put("c", blob);

                Object plaintext = service.decryptValue(payload);

                assertThat(plaintext).isEqualTo("alice");
                assertThat(keyVault.lastEnsuredNamespace).isEqualTo(NAMESPACE);
                assertThat(keyVault.lastRequestedDekVersion).isEqualTo(5);
        }

        @Test
        void decryptValueRejectsNonEncryptedMarker() {
                ProgrammaticCryptoService service = createService(new FakeKeyVaultService(), new NoOpStructuredValueCodec());

                assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 0, "_t", "STR", "c", "abc")))
                                .isInstanceOf(DecryptionException.class)
                                .hasMessageContaining("_e=1 missing");
        }

        @Test
        void decryptValueWrapsInvalidWireBlob() {
                ProgrammaticCryptoService service = createService(new FakeKeyVaultService(), new NoOpStructuredValueCodec());

                assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 1, "_t", "STR", "c", "bad-blob")))
                                .isInstanceOf(DecryptionException.class)
                                .hasMessageContaining("Invalid Wire Format blob");
        }

        @Test
        void decryptValueWrapsDekLookupFailure() {
                FakeKeyVaultService keyVault = new FakeKeyVaultService();
                keyVault.failGetDekByVersion = true;
                ProgrammaticCryptoService service = createService(keyVault, new NoOpStructuredValueCodec());

                byte[] serialized = new TypeSerializer().serialize("alice");
                String blob = CryptoCodec.encrypt(DEK, serialized, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), 8);

                assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 1, "_t", "STR", "c", blob)))
                                .isInstanceOf(KeyManagementException.class)
                                .hasMessageContaining("Failed to resolve DEK for namespace");
        }

        @Test
        void decryptValueWrapsCryptoFailureWhenDekIsWrong() {
                FakeKeyVaultService keyVault = new FakeKeyVaultService();
                keyVault.dekByVersion.put(3, fixedKey((byte) 0x11));
                ProgrammaticCryptoService service = createService(keyVault, new NoOpStructuredValueCodec());

                byte[] serialized = new TypeSerializer().serialize("alice");
                String blob = CryptoCodec.encrypt(DEK, serialized, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), 3);

                assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 1, "_t", "STR", "c", blob)))
                                .isInstanceOf(DecryptionException.class)
                                .hasMessageContaining("Failed to decrypt value");
        }

        @Test
        void decryptValueUsesStructuredCodecForDocType() {
                FakeKeyVaultService keyVault = new FakeKeyVaultService();
                keyVault.dekByVersion.put(6, DEK);
                CapturingStructuredCodec codec = new CapturingStructuredCodec();
                ProgrammaticCryptoService service = createService(keyVault, codec);

                byte[] plaintextDoc = "{\"name\":\"alice\"}".getBytes(StandardCharsets.UTF_8);
                String blob = CryptoCodec.encrypt(DEK, plaintextDoc, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), 6);

                Object out = service.decryptValue(Map.of("_e", 1, "_t", "DOC", "c", blob));

                assertThat(out).isEqualTo(Map.of("decoded", "DOC"));
                assertThat(codec.lastTypeMarker).isEqualTo("DOC");
                assertThat(codec.lastData).containsExactly(plaintextDoc);
        }

        @Test
        void decryptValueWrapsStructuredCodecFailure() {
                FakeKeyVaultService keyVault = new FakeKeyVaultService();
                keyVault.dekByVersion.put(6, DEK);
                ProgrammaticCryptoService service = createService(keyVault, new FailingStructuredCodec());

                byte[] plaintextDoc = "{\"name\":\"alice\"}".getBytes(StandardCharsets.UTF_8);
                String blob = CryptoCodec.encrypt(DEK, plaintextDoc, AlgorithmId.AES_256_GCM, Namespace.parse(NAMESPACE), 6);

                assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 1, "_t", "DOC", "c", blob)))
                                .isInstanceOf(DecryptionException.class)
                                .hasMessageContaining("Failed to decode structured payload");
        }

        private static ProgrammaticCryptoService createService(KeyVaultService keyVaultService,
                                                                                                                   StructuredValueCodec structuredValueCodec) {
        return new ProgrammaticCryptoService(
                new TypeSerializer(),
                new TypeDeserializer(),
                                keyVaultService,
                                null,
                                structuredValueCodec);
        }

        private static byte[] fixedKey(byte value) {
                byte[] out = new byte[32];
                java.util.Arrays.fill(out, value);
                return out;
        }

        private static final class DemoScope {
                private DemoScope() {
                }
        }

        private static final class FakeKeyVaultService extends KeyVaultService {
                private String lastEnsuredNamespace;
                private String lastRequestedKid;
                private int lastRequestedDekVersion;

                private String activeKid = "kid-1";
                private int activeDekVersion = 1;
                private byte[] activeDek = DEK;
                private boolean failGetDek;
                private boolean failGetDekByVersion;
                private final Map<Integer, byte[]> dekByVersion = new java.util.HashMap<>();

                private FakeKeyVaultService() {
                        super(null, null, null);
                }

                @Override
                public void ensureVaultInitialized(String namespace) {
                        this.lastEnsuredNamespace = namespace;
                }

                @Override
                public int getActiveDekVersion(String namespace) {
                        return activeDekVersion;
                }

                @Override
                public String getActiveKid(String namespace) {
                        return activeKid;
                }

                @Override
                public byte[] getDek(String kid) {
                        this.lastRequestedKid = kid;
                        if (failGetDek) {
                                throw new FatalCryptoException("dek missing");
                        }
                        return activeDek;
                }

                @Override
                public byte[] getDekByVersion(String namespace, int dekVersion) {
                        this.lastRequestedDekVersion = dekVersion;
                        if (failGetDekByVersion) {
                                throw new FatalCryptoException("dek version missing");
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
                        return Map.of("decoded", typeMarker);
                }
        }

        private static final class CapturingStructuredCodec extends NoOpStructuredValueCodec {
                private String lastTypeMarker;
                private byte[] lastData;

                @Override
                public Object decode(byte[] data, String typeMarker) {
                        this.lastTypeMarker = typeMarker;
                        this.lastData = data.clone();
                        return super.decode(data, typeMarker);
                }
        }

        private static final class FailingStructuredCodec extends NoOpStructuredValueCodec {
                @Override
                public Object decode(byte[] data, String typeMarker) {
                        throw new IllegalStateException("decode failed");
                }
    }
}
