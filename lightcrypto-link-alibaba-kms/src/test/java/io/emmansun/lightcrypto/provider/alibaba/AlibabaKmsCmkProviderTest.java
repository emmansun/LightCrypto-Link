package io.emmansun.lightcrypto.provider.alibaba;

import io.emmansun.lightcrypto.model.WrappedKey;
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
        AlibabaKmsCmkProvider provider = createProvider("RSA");
        byte[] plaintextKey = new byte[32];

        WrappedKey wrapped = provider.wrap(plaintextKey);

        assertThat(wrapped.algorithm()).isEqualTo("RSAES-OAEP-SHA256");
        assertThat(wrapped.ciphertext()).isNotEmpty();
        // RSA-2048 OAEP ciphertext is 256 bytes
        assertThat(wrapped.ciphertext()).hasSize(256);
    }

    @Test
    void rsaWrap_shouldProduceDifferentCiphertextsForSameInput() throws Exception {
        AlibabaKmsCmkProvider provider = createProvider("RSA");
        byte[] plaintextKey = new byte[32];

        WrappedKey wrapped1 = provider.wrap(plaintextKey);
        WrappedKey wrapped2 = provider.wrap(plaintextKey);

        assertThat(wrapped1.ciphertext()).isNotEqualTo(wrapped2.ciphertext());
    }

    @Test
    void constructor_shouldRejectInvalidAlgorithm() throws Exception {
        com.aliyun.kms20160120.Client dummyClient = createDummyClient();

        assertThatThrownBy(() -> new AlibabaKmsCmkProvider(
                "key-1", "ver-1", rsaKeyPair.getPublic(), dummyClient, "DES"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported algorithm");
    }

    @Test
    void constructor_shouldRejectNullAlgorithm() throws Exception {
        com.aliyun.kms20160120.Client dummyClient = createDummyClient();

        assertThatThrownBy(() -> new AlibabaKmsCmkProvider(
                "key-1", "ver-1", rsaKeyPair.getPublic(), dummyClient, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm must not be null or blank");
    }

    @Test
    void getProviderId_shouldReturnAlibabaKms() throws Exception {
        AlibabaKmsCmkProvider provider = createProvider("RSA");
        assertThat(provider.getProviderId()).isEqualTo("alibaba-kms");
    }

    // ===== PublicKeyLoader tests =====

    @Test
    void publicKeyLoader_shouldParseValidRsaPem() {
        PublicKey key = PublicKeyLoader.loadFromPem(rsaPublicKeyPem, "RSA");
        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void publicKeyLoader_shouldRejectInvalidPem() {
        assertThatThrownBy(() -> PublicKeyLoader.loadFromPem("not-a-pem", "RSA"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicKeyLoader_shouldRejectNullPem() {
        assertThatThrownBy(() -> PublicKeyLoader.loadFromPem(null, "RSA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    void publicKeyLoader_shouldRejectUnsupportedAlgorithm() {
        assertThatThrownBy(() -> PublicKeyLoader.loadFromPem(rsaPublicKeyPem, "DES"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported algorithm");
    }

    // ===== Helper methods =====

    private AlibabaKmsCmkProvider createProvider(String algorithm) throws Exception {
        return new AlibabaKmsCmkProvider(
                "key-test", "ver-test", rsaKeyPair.getPublic(), createDummyClient(), algorithm);
    }

    private com.aliyun.kms20160120.Client createDummyClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config =
                new com.aliyun.teaopenapi.models.Config()
                        .setAccessKeyId("test-ak")
                        .setAccessKeySecret("test-sk")
                        .setRegionId("cn-hangzhou");
        return new com.aliyun.kms20160120.Client(config);
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
