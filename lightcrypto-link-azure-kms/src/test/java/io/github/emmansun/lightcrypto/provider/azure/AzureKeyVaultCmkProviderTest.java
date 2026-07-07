package io.github.emmansun.lightcrypto.provider.azure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.models.JsonWebKey;
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
 * Unit tests for {@link AzureKeyVaultCmkProvider}, {@link PublicKeyLoader},
 * and {@link JsonWebKeyToPublicKey}.
 * Uses a locally generated RSA key pair — no real Azure calls needed for wrap tests.
 */
class AzureKeyVaultCmkProviderTest {

    private static KeyPair rsaKeyPair;
    private static String rsaPublicKeyPem;

    @BeforeAll
    static void generateTestKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        rsaKeyPair = kpg.generateKeyPair();
        rsaPublicKeyPem = toPem(rsaKeyPair.getPublic());
    }

    // ===== AzureKeyVaultCmkProvider tests =====

    @Test
    void rsaWrap_shouldReturnCorrectAlgorithmAndNonEmptyCiphertext() {
        AzureKeyVaultCmkProvider provider = createProvider();
        byte[] plaintextKey = new byte[32];

        WrappedKey wrapped = provider.wrap(plaintextKey);

        assertThat(wrapped.algorithm()).isEqualTo("RSA-OAEP-256");
        assertThat(wrapped.ciphertext()).isNotEmpty();
        assertThat(wrapped.ciphertext()).hasSize(256);
    }

    @Test
    void rsaWrap_shouldProduceDifferentCiphertextsForSameInput() {
        AzureKeyVaultCmkProvider provider = createProvider();
        byte[] plaintextKey = new byte[32];

        WrappedKey wrapped1 = provider.wrap(plaintextKey);
        WrappedKey wrapped2 = provider.wrap(plaintextKey);

        assertThat(wrapped1.ciphertext()).isNotEqualTo(wrapped2.ciphertext());
    }

    @Test
    void getProviderId_shouldReturnAzureKeyVault() {
        AzureKeyVaultCmkProvider provider = createProvider();
        assertThat(provider.getProviderId()).isEqualTo("azure-keyvault");
    }

    @Test
    void getPublicReference_shouldContainProviderAndVersion() {
        AzureKeyVaultCmkProvider provider = createProvider();
        assertThat(provider.getPublicReference()).isEqualTo("azure-keyvault:test-version");
    }

    @Test
    void getKeyVersion_shouldReturnAutoResolvedVersion() {
        AzureKeyVaultCmkProvider provider = createProvider();
        assertThat(provider.getKeyVersion()).isEqualTo("test-version");
    }

    @Test
    void constructor_shouldRejectNullPublicKey() {
        CryptographyClient dummyClient = createDummyClient();
        assertThatThrownBy(() -> new AzureKeyVaultCmkProvider(null, dummyClient, "RSA-OAEP-256", "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicKey must not be null");
    }

    @Test
    void constructor_shouldRejectNullCryptoClient() {
        assertThatThrownBy(() -> new AzureKeyVaultCmkProvider(rsaKeyPair.getPublic(), null, "RSA-OAEP-256", "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cryptoClient must not be null");
    }

    // ===== JsonWebKeyToPublicKey tests =====

    @Test
    void jsonWebKeyToPublicKey_shouldConvertValidJwk() throws Exception {
        java.security.interfaces.RSAPublicKey rsaPub = (java.security.interfaces.RSAPublicKey) rsaKeyPair.getPublic();
        JsonWebKey jwk = new JsonWebKey()
                .setN(toUnsignedBytes(rsaPub.getModulus()))
                .setE(toUnsignedBytes(rsaPub.getPublicExponent()));

        PublicKey converted = JsonWebKeyToPublicKey.convert(jwk);

        assertThat(converted).isNotNull();
        assertThat(converted.getAlgorithm()).isEqualTo("RSA");

        // Verify the converted key can encrypt data
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
        javax.crypto.spec.OAEPParameterSpec oaepParams = new javax.crypto.spec.OAEPParameterSpec(
                "SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256,
                javax.crypto.spec.PSource.PSpecified.DEFAULT);
        cipher.init(javax.crypto.Cipher.WRAP_MODE, converted, oaepParams);
        byte[] ciphertext = cipher.wrap(new javax.crypto.spec.SecretKeySpec(new byte[32], "AES"));
        assertThat(ciphertext).hasSize(256);
    }

    @Test
    void jsonWebKeyToPublicKey_shouldRejectNull() {
        assertThatThrownBy(() -> JsonWebKeyToPublicKey.convert(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void jsonWebKeyToPublicKey_shouldRejectJwkWithoutParameters() {
        JsonWebKey emptyJwk = new JsonWebKey();
        assertThatThrownBy(() -> JsonWebKeyToPublicKey.convert(emptyJwk))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not contain RSA public key parameters");
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

    private AzureKeyVaultCmkProvider createProvider() {
        CryptographyClient dummyClient = createDummyClient();
        return new AzureKeyVaultCmkProvider(rsaKeyPair.getPublic(), dummyClient, "RSA-OAEP-256", "test-version");
    }

    private CryptographyClient createDummyClient() {
        return new CryptographyClientBuilder()
                .keyIdentifier("https://dummy.vault.azure.net/keys/dummy-key/dummy-version")
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
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

    private static byte[] toUnsignedBytes(java.math.BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        if (bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
