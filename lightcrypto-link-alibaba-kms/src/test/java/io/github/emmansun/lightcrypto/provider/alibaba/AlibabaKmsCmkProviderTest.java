package io.github.emmansun.lightcrypto.provider.alibaba;

import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AlibabaKmsCmkProvider} and {@link PublicKeyLoader}.
 * Uses a locally generated RSA key pair — no real KMS calls needed for wrap tests.
 */
class AlibabaKmsCmkProviderTest {

    private static KeyPair rsaKeyPair;
    private static String rsaPublicKeyPem;

    @BeforeAll
    static void generateTestKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        rsaKeyPair = kpg.generateKeyPair();
        rsaPublicKeyPem = toPem(rsaKeyPair.getPublic());
    }

    @Test
    void rsaWrap_shouldReturnCorrectAlgorithmAndNonEmptyCiphertext() throws Exception {
        AlibabaKmsCmkProvider provider = createProvider();
        byte[] plaintextKey = new byte[32];

        WrappedKey wrapped = provider.wrap(plaintextKey);

        assertThat(wrapped.algorithm()).isEqualTo("RSAES-OAEP-SHA256");
        assertThat(wrapped.ciphertext()).isNotEmpty();
        // RSA-2048 OAEP ciphertext is 256 bytes
        assertThat(wrapped.ciphertext()).hasSize(256);
    }

    @Test
    void rsaWrap_shouldProduceDifferentCiphertextsForSameInput() throws Exception {
        AlibabaKmsCmkProvider provider = createProvider();
        byte[] plaintextKey = new byte[32];

        WrappedKey wrapped1 = provider.wrap(plaintextKey);
        WrappedKey wrapped2 = provider.wrap(plaintextKey);

        assertThat(wrapped1.ciphertext()).isNotEqualTo(wrapped2.ciphertext());
    }

    @Test
    void constructor_shouldRejectUnsupportedKeyType() throws Exception {
        com.aliyun.kms20160120.Client dummyClient = createDummyClient();
        // Generate a DSA key which is not supported
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");
        kpg.initialize(1024);
        KeyPair dsaKeyPair = kpg.generateKeyPair();

        assertThatThrownBy(() -> new AlibabaKmsCmkProvider(
                "key-1", "ver-1", dsaKeyPair.getPublic(), dummyClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported public key algorithm");
    }

    @Test
    void getProviderId_shouldReturnAlibabaKms() throws Exception {
        AlibabaKmsCmkProvider provider = createProvider();
        assertThat(provider.getProviderId()).isEqualTo("alibaba-kms");
    }

    @Test
    void getPublicReference_shouldReturnKeyId() throws Exception {
        AlibabaKmsCmkProvider provider = createProvider();
        assertThat(provider.getPublicReference()).isEqualTo("key-test");
    }

        @Test
        void getPublicReference_shouldReturnKeyIdWhenVersionMissing() throws Exception {
        AlibabaKmsCmkProvider provider = new AlibabaKmsCmkProvider(
            "key-test", "  ", rsaKeyPair.getPublic(), createDummyClient());

        assertThat(provider.getPublicReference()).isEqualTo("key-test");
        }

        @Test
        void constructor_shouldRejectNullInputs() {
        com.aliyun.kms20160120.Client dummyClient;
        try {
            dummyClient = createDummyClient();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThatThrownBy(() -> new AlibabaKmsCmkProvider(null, "v1", rsaKeyPair.getPublic(), dummyClient))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("keyId must not be null");
        assertThatThrownBy(() -> new AlibabaKmsCmkProvider("k", "v1", null, dummyClient))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("publicKey must not be null");
        assertThatThrownBy(() -> new AlibabaKmsCmkProvider("k", "v1", rsaKeyPair.getPublic(), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("kmsClient must not be null");
        }

        @Test
        void wrap_shouldThrowUnsupportedWhenPublicKeyIsEc() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        PublicKey ecPublicKey = kpg.generateKeyPair().getPublic();
        AlibabaKmsCmkProvider provider = new AlibabaKmsCmkProvider(
            "key-test", "ver-test", ecPublicKey, createDummyClient());

        assertThatThrownBy(() -> provider.wrap(new byte[32]))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("SM2 wrap is not yet implemented");
        }

        @Test
        void unwrap_shouldReturnDecodedPlaintext() throws Exception {
        AlibabaKmsCmkProvider provider = new AlibabaKmsCmkProvider(
                "key-test", "ver-test", rsaKeyPair.getPublic(), new SuccessfulDecryptClient());

        byte[] plaintext = provider.unwrap(new WrappedKey(new byte[]{9, 9, 9}, "RSAES-OAEP-SHA256"));

        assertThat(plaintext).containsExactly((byte) 1, (byte) 2, (byte) 3);
        }

        @Test
        void unwrap_shouldWrapUnknownAlgorithmAsCryptoException() throws Exception {
        AlibabaKmsCmkProvider provider = createProvider();

        assertThatThrownBy(() -> provider.unwrap(new WrappedKey(new byte[]{1}, "UNKNOWN")))
            .isInstanceOf(CryptoException.class)
            .hasMessageContaining("KMS AsymmetricDecrypt failed");
        }

        @Test
        void unwrap_shouldWrapClientExceptionAsCryptoException() throws Exception {
        AlibabaKmsCmkProvider provider = new AlibabaKmsCmkProvider(
                "key-test", "ver-test", rsaKeyPair.getPublic(), new FailingDecryptClient());

        assertThatThrownBy(() -> provider.unwrap(new WrappedKey(new byte[]{1, 2}, "RSAES-OAEP-SHA256")))
            .isInstanceOf(CryptoException.class)
            .hasMessageContaining("KMS AsymmetricDecrypt failed");
        }

    // ===== PublicKeyLoader tests =====

    @Test
    void publicKeyLoader_shouldParseValidRsaPem() {
        PublicKey key = PublicKeyLoader.loadFromPem(rsaPublicKeyPem);
        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void publicKeyLoader_shouldRejectInvalidPem() {
        assertThatThrownBy(() -> PublicKeyLoader.loadFromPem("not-a-pem"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicKeyLoader_shouldRejectNullPem() {
        assertThatThrownBy(() -> PublicKeyLoader.loadFromPem(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
    }

    // ===== Helper methods =====

    private AlibabaKmsCmkProvider createProvider() throws Exception {
        return new AlibabaKmsCmkProvider(
                "key-test", "ver-test", rsaKeyPair.getPublic(), createDummyClient());
    }

    private com.aliyun.kms20160120.Client createDummyClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config =
                new com.aliyun.teaopenapi.models.Config()
                        .setAccessKeyId("test-ak")
                        .setAccessKeySecret("test-sk")
                        .setRegionId("cn-hangzhou");
        return new com.aliyun.kms20160120.Client(config);
    }

    private static class SuccessfulDecryptClient extends com.aliyun.kms20160120.Client {
        SuccessfulDecryptClient() throws Exception {
            super(new com.aliyun.teaopenapi.models.Config()
                    .setAccessKeyId("test-ak")
                    .setAccessKeySecret("test-sk")
                    .setRegionId("cn-hangzhou"));
        }

        @Override
        public com.aliyun.kms20160120.models.AsymmetricDecryptResponse asymmetricDecrypt(
                com.aliyun.kms20160120.models.AsymmetricDecryptRequest request) {
            com.aliyun.kms20160120.models.AsymmetricDecryptResponseBody body =
                    new com.aliyun.kms20160120.models.AsymmetricDecryptResponseBody();
            body.setPlaintext(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
            com.aliyun.kms20160120.models.AsymmetricDecryptResponse response =
                    new com.aliyun.kms20160120.models.AsymmetricDecryptResponse();
            response.setBody(body);
            return response;
        }
    }

    private static class FailingDecryptClient extends com.aliyun.kms20160120.Client {
        FailingDecryptClient() throws Exception {
            super(new com.aliyun.teaopenapi.models.Config()
                    .setAccessKeyId("test-ak")
                    .setAccessKeySecret("test-sk")
                    .setRegionId("cn-hangzhou"));
        }

        @Override
        public com.aliyun.kms20160120.models.AsymmetricDecryptResponse asymmetricDecrypt(
                com.aliyun.kms20160120.models.AsymmetricDecryptRequest request) {
            throw new RuntimeException("boom");
        }
    }

    private static String toPem(PublicKey publicKey) {
        String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length()));
            sb.append('\n');
        }
        sb.append("-----END PUBLIC KEY-----");
        return sb.toString();
    }
}
