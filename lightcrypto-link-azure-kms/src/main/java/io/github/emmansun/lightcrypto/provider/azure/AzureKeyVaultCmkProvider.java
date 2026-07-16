package io.github.emmansun.lightcrypto.provider.azure;

import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.cryptography.models.UnwrapResult;
import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Map;

/**
 * CMK Provider backed by Azure Key Vault using asymmetric RSA-OAEP-256.
 * <p>
 * Local wrap uses RSA-OAEP with SHA-256 (public key), remote unwrap calls Azure Key Vault API.
 */
public final class AzureKeyVaultCmkProvider implements CmkProvider {
    private static final String PROVIDER_ID = "azure-keyvault";

    private final PublicKey publicKey;
    private final KeyClient keyClient;
    private final String algorithm;
    private final String keyName;
    private final String keyVersion;

    /**
     * Constructs a new Azure Key Vault asymmetric CMK provider.
     *
     * @param publicKey    RSA public key for local wrap (from PEM or auto-fetched via getKey())
     * @param keyClient Azure Key Vault KeyClient for unwrap
     * @param algorithm    wrap algorithm identifier (e.g. "RSA-OAEP-256")
     * @param keyVersion   key version auto-resolved from getKey()
     */
    public AzureKeyVaultCmkProvider(PublicKey publicKey,
                                    KeyClient keyClient,
                                    String algorithm,
                                    String keyName,
                                    String keyVersion) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey must not be null");
        }
        if (keyClient == null) {
            throw new IllegalArgumentException("keyClient must not be null");
        }
        this.publicKey = publicKey;
        this.keyClient = keyClient;
        this.algorithm = algorithm;
        this.keyName = keyName;
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
            return new WrappedKey(ciphertext, algorithm, Map.of(CmkProvider.META_CMK_VERSION, keyVersion));
        } catch (Exception e) {
            throw new CryptoException("Failed to wrap key with RSA-OAEP-256", e);
        }
    }

    @Override
    public byte[] unwrap(WrappedKey wrappedKey) {
        if (wrappedKey == null) {
            throw new IllegalArgumentException("WrappedKey must not be null");
        }
        if (!supportsAlgorithm(wrappedKey.algorithm())) {
            throw new IllegalArgumentException("Unsupported algorithm: " + wrappedKey.algorithm());
        }
        try {
            String cmkVersion = wrappedKey.metadata().get(CmkProvider.META_CMK_VERSION);
            if (cmkVersion == null || cmkVersion.isEmpty()) {
                cmkVersion = keyVersion; // fallback to provider's key version if not present in metadata
            }
            CryptographyClient cryptoClient = this.keyClient.getCryptographyClient(keyName, cmkVersion);
            UnwrapResult result = cryptoClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedKey.ciphertext());
            return result.getKey();
        } catch (Exception e) {
            throw new CryptoException("Failed to unwrap key via Azure Key Vault", e);
        }
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getPublicReference() {
        return keyName;
    }

    @Override
    public boolean supportsAlgorithm(String lclAlgorithm) {
        return KeyWrapAlgorithm.RSA_OAEP_256.toString().equals(lclAlgorithm);
    }

    @Override
    public String mapAlgorithm(String lclAlgorithm) {
        return lclAlgorithm; 
    }

    /** Returns the auto-resolved key version. */
    public String getKeyVersion() {
        return keyVersion;
    }
}
