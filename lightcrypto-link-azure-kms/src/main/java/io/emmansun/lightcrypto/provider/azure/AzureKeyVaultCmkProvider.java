package io.emmansun.lightcrypto.provider.azure;

import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.cryptography.models.UnwrapResult;
import io.emmansun.lightcrypto.exception.CryptoException;
import io.emmansun.lightcrypto.model.WrappedKey;
import io.emmansun.lightcrypto.provider.CmkProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;

/**
 * CMK Provider backed by Azure Key Vault using asymmetric RSA-OAEP-256.
 * <p>
 * Local wrap uses RSA-OAEP with SHA-256 (public key), remote unwrap calls Azure Key Vault API.
 */
public class AzureKeyVaultCmkProvider implements CmkProvider {

    private final PublicKey publicKey;
    private final CryptographyClient cryptoClient;
    private final String algorithm;
    private final String keyVersion;

    /**
     * Constructs a new Azure Key Vault asymmetric CMK provider.
     *
     * @param publicKey    RSA public key for local wrap (from PEM or auto-fetched via getKey())
     * @param cryptoClient Azure Key Vault CryptographyClient for unwrap
     * @param algorithm    wrap algorithm identifier (e.g. "RSA-OAEP-256")
     * @param keyVersion   key version auto-resolved from getKey()
     */
    public AzureKeyVaultCmkProvider(PublicKey publicKey,
                                    CryptographyClient cryptoClient,
                                    String algorithm,
                                    String keyVersion) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey must not be null");
        }
        if (cryptoClient == null) {
            throw new IllegalArgumentException("cryptoClient must not be null");
        }
        this.publicKey = publicKey;
        this.cryptoClient = cryptoClient;
        this.algorithm = algorithm;
        this.keyVersion = keyVersion;
    }

    @Override
    public WrappedKey wrap(byte[] key) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("Key to wrap must not be null or empty");
        }
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.WRAP_MODE, publicKey, oaepParams);
            byte[] ciphertext = cipher.wrap(new javax.crypto.spec.SecretKeySpec(key, "AES"));
            return new WrappedKey(ciphertext, algorithm);
        } catch (Exception e) {
            throw new CryptoException("Failed to wrap key with RSA-OAEP-256", e);
        }
    }

    @Override
    public byte[] unwrap(WrappedKey wrappedKey) {
        if (wrappedKey == null) {
            throw new IllegalArgumentException("WrappedKey must not be null");
        }
        try {
            UnwrapResult result = cryptoClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedKey.ciphertext());
            return result.getKey();
        } catch (Exception e) {
            throw new CryptoException("Failed to unwrap key via Azure Key Vault", e);
        }
    }

    @Override
    public String getProviderId() {
        return "azure-keyvault";
    }

    /** Returns the auto-resolved key version. */
    public String getKeyVersion() {
        return keyVersion;
    }
}
