package io.github.emmansun.lightcrypto.provider.azure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
    void getPublicReference_shouldSameAsKeyName() {
        AzureKeyVaultCmkProvider provider = createProvider();
        assertThat(provider.getPublicReference()).isEqualTo("test-name");
    }

    @Test
    void getKeyVersion_shouldReturnAutoResolvedVersion() {
        AzureKeyVaultCmkProvider provider = createProvider();
        assertThat(provider.getKeyVersion()).isEqualTo("test-version");
    }

    @Test
    void constructor_shouldRejectNullPublicKey() {
        KeyClient dummyClient = createDummyClient();
        assertThatThrownBy(() -> new AzureKeyVaultCmkProvider(null, dummyClient, "RSA-OAEP-256", "keyName", "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicKey must not be null");
    }

    @Test
    void constructor_shouldRejectNullKeyClient() {
        assertThatThrownBy(() -> new AzureKeyVaultCmkProvider(rsaKeyPair.getPublic(), null, "RSA-OAEP-256", "keyName", "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyClient must not be null");
    }

        @Test
        void wrap_shouldRejectNullOrEmptyKey() {
        AzureKeyVaultCmkProvider provider = createProvider();

        assertThatThrownBy(() -> provider.wrap(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null or empty");
        assertThatThrownBy(() -> provider.wrap(new byte[0]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null or empty");
        }

        @Test
        void unwrap_shouldRejectNullWrappedKey() {
        AzureKeyVaultCmkProvider provider = createProvider();

        assertThatThrownBy(() -> provider.unwrap(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("WrappedKey must not be null");
        }

        @Test
        void unwrap_shouldUseMetadataVersionWhenPresent() {
        byte[] expected = new byte[]{1, 2, 3, 4};
        byte[] ciphertext = new byte[]{9, 8, 7};
            AtomicReference<String> usedVersion = new AtomicReference<>();

            KeyClient keyClient = createDummyClient();

        AzureKeyVaultCmkProvider provider = new AzureKeyVaultCmkProvider(
                rsaKeyPair.getPublic(),
                keyClient,
                "RSA-OAEP-256",
                "test-name",
                "provider-version",
                (keyName, keyVersion, wrappedCiphertext) -> {
                    usedVersion.set(keyVersion);
                    assertThat(keyName).isEqualTo("test-name");
                    assertThat(wrappedCiphertext).containsExactly(ciphertext);
                    return expected;
                });

        WrappedKey wrappedKey = new WrappedKey(
            ciphertext,
            "RSA-OAEP-256",
            Map.of(CmkProvider.META_CMK_VERSION, "meta-version"));

        assertThat(provider.unwrap(wrappedKey)).containsExactly(expected);
            assertThat(usedVersion.get()).isEqualTo("meta-version");
        }

        @Test
        void unwrap_shouldFallbackToProviderVersionWhenMetadataVersionMissing() {
        byte[] expected = new byte[]{5, 6, 7, 8};
        byte[] ciphertext = new byte[]{3, 2, 1};
            AtomicReference<String> usedVersion = new AtomicReference<>();

            KeyClient keyClient = createDummyClient();

        AzureKeyVaultCmkProvider provider = new AzureKeyVaultCmkProvider(
                rsaKeyPair.getPublic(),
                keyClient,
                "RSA-OAEP-256",
                "test-name",
                "provider-version",
                (keyName, keyVersion, wrappedCiphertext) -> {
                    usedVersion.set(keyVersion);
                    assertThat(keyName).isEqualTo("test-name");
                    assertThat(wrappedCiphertext).containsExactly(ciphertext);
                    return expected;
                });

        WrappedKey wrappedKey = new WrappedKey(ciphertext, "RSA-OAEP-256");

        assertThat(provider.unwrap(wrappedKey)).containsExactly(expected);
            assertThat(usedVersion.get()).isEqualTo("provider-version");
        }

        @Test
        void unwrap_shouldWrapClientExceptionAsCryptoException() {
            KeyClient keyClient = createDummyClient();
        byte[] ciphertext = new byte[]{1, 9, 9};
        WrappedKey wrappedKey = new WrappedKey(
            ciphertext,
            "RSA-OAEP-256",
            Map.of(CmkProvider.META_CMK_VERSION, "meta-version"));

        AzureKeyVaultCmkProvider provider = new AzureKeyVaultCmkProvider(
                rsaKeyPair.getPublic(),
                keyClient,
                "RSA-OAEP-256",
                "test-name",
                "provider-version",
                (keyName, keyVersion, wrappedCiphertext) -> {
                    throw new RuntimeException("boom");
                });

        assertThatThrownBy(() -> provider.unwrap(wrappedKey))
            .isInstanceOf(CryptoException.class)
            .hasMessageContaining("Failed to unwrap key via Azure Key Vault");
        }

        @Test
        void unwrap_shouldRejectUnsupportedAlgorithm() {
        AzureKeyVaultCmkProvider provider = createProvider();
        WrappedKey wrappedKey = new WrappedKey(new byte[]{1, 2}, "RSA1_5");

        assertThatThrownBy(() -> provider.unwrap(wrappedKey))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported algorithm");
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
        KeyClient dummyClient = createDummyClient();
        return new AzureKeyVaultCmkProvider(rsaKeyPair.getPublic(), dummyClient, "RSA-OAEP-256", "test-name", "test-version");
    }

    private KeyClient createDummyClient() {
        return new KeyClientBuilder()
                .vaultUrl("https://dummy.vault.azure.net/")
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
