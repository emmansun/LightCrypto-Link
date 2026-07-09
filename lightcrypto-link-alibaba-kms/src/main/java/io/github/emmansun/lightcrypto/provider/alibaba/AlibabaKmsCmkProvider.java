package io.github.emmansun.lightcrypto.provider.alibaba;

import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.model.GeneratedKey;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.provider.alibaba.AlibabaKmsCmkProperties.Mode;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Alibaba Cloud KMS CMK provider — wraps DEKs locally using an asymmetric public key
 * and unwraps them remotely via the KMS {@code AsymmetricDecrypt} API.
 * <p>
 * The private key never leaves the KMS HSM. The application holds only the public key
 * for local wrap operations; unwrap always requires a KMS API call.
 * </p>
 * <p>
 * The wrapping algorithm is auto-detected from the public key type:
 * <ul>
 *   <li>RSA keys → RSAES-OAEP with SHA-256 hash + MGF1-SHA-256
 *       (matching KMS {@code RSAES_OAEP_SHA_256}, RFC 3447/PKCS#1)</li>
 *   <li>EC keys (SM2) → SM2 PKE (Phase 2)</li>
 * </ul>
 * Note: Java's default {@code OAEPWithSHA-256AndMGF1Padding} uses SHA-1 for MGF1,
 * which is incompatible with KMS — an explicit {@link OAEPParameterSpec} with
 * {@link MGF1ParameterSpec#SHA256} is required.
 * </p>
 */
public final class AlibabaKmsCmkProvider implements CmkProvider {

    private static final String PROVIDER_ID = "alibaba-kms";

    /** WrappedKey algorithm identifier for RSA-OAEP wrapping. */
    static final String ALGORITHM_RSA_OAEP_SHA256 = "RSAES-OAEP-SHA256";

    /** Alibaba KMS AsymmetricDecrypt API Algorithm parameter value for RSA-OAEP. */
    private static final String KMS_ALGORITHM_RSA = "RSAES_OAEP_SHA_256";

    private static final String KMS_DATA_KEY = "KMS_DATA_KEY";

    /**
     * OAEP parameters: SHA-256 hash + MGF1-SHA-256 (matching KMS RSAES_OAEP_SHA_256).
     * Java's default OAEPWithSHA-256AndMGF1Padding uses MGF1-SHA-1 which is incompatible.
     */
    private static final OAEPParameterSpec RSA_OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    
    private final Mode mode;
    private final Map<String, String> encryptionContext;
    private final String keyId;
    private final String keyVersionId;
    private final PublicKey publicKey;
    private final com.aliyun.kms20160120.Client kmsClient;
    private final boolean isRsa;

    /**
     * Construct an Alibaba KMS CMK provider.
     * <p>
     * The wrapping algorithm is derived from the public key type:
     * RSA keys use RSAES-OAEP-SHA256; EC keys (SM2) will use SM2 PKE (Phase 2).
     * </p>
     *
     * @param keyId        the KMS CMK key identifier
     * @param keyVersionId the KMS CMK key version ID (may be {@code null} if resolved dynamically)
     * @param publicKey    the asymmetric public key for local wrap
     * @param kmsClient    the Alibaba Cloud KMS client for remote unwrap
     */
    public AlibabaKmsCmkProvider(String keyId, String keyVersionId, PublicKey publicKey,
                                  com.aliyun.kms20160120.Client kmsClient) {
        this.mode = Mode.ASYMMETRIC;
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
        this.keyVersionId = keyVersionId;
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.kmsClient = Objects.requireNonNull(kmsClient, "kmsClient must not be null");
        this.encryptionContext = null; // Not used in asymmetric mode

        String keyAlgorithm = publicKey.getAlgorithm();
        if ("RSA".equals(keyAlgorithm)) {
            this.isRsa = true;
        } else if ("EC".equals(keyAlgorithm)) {
            this.isRsa = false;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported public key algorithm: '" + keyAlgorithm
                            + "'. Supported: RSA, EC (SM2)");
        }
    }

    /**
     * Construct an Alibaba KMS CMK provider in SYMMETRIC mode.
     * <p>
     * In SYMMETRIC mode, the provider uses KMS GenerateDataKey and Decrypt APIs for envelope encryption.
     * </p>
     * @param keyId              the KMS CMK key identifier
     * @param encryptionContext  the encryption context for envelope encryption
     * @param kmsClient          the Alibaba Cloud KMS client for remote operations
     */
    public AlibabaKmsCmkProvider(String keyId, Map<String, String> encryptionContext, com.aliyun.kms20160120.Client kmsClient) {
        this.mode = Mode.SYMMETRIC;
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
        this.encryptionContext = encryptionContext;
        this.kmsClient = Objects.requireNonNull(kmsClient, "kmsClient must not be null");
        this.isRsa = false;
        this.keyVersionId = null;
        this.publicKey = null;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getPublicReference() {
        if (keyVersionId == null || keyVersionId.isBlank()) {
            return keyId;
        }
        return keyId + ":" + keyVersionId;
    }

    @Override
    public GeneratedKey generateKey(int keyLength) {
        if (mode == Mode.ASYMMETRIC) {
            return CmkProvider.super.generateKey(keyLength);
        }
        return generateDataKey(keyLength);
    }

    /**
     * Generate a new random symmetric key of the specified length, wrap it with the CMK using KMS GenerateDataKey,
     * and return both the plaintext key and its wrapped representation.
     * 
     * @param keyLength the length of the symmetric key to generate, in bytes
     * @return a GeneratedKey containing the plaintext key and its wrapped representation
     */
    private GeneratedKey generateDataKey(int keyLength) {
        try {
            com.aliyun.kms20160120.models.GenerateDataKeyRequest request =
                    new com.aliyun.kms20160120.models.GenerateDataKeyRequest()
                            .setKeyId(keyId)
                            .setNumberOfBytes(keyLength);
            if (encryptionContext != null && !encryptionContext.isEmpty()) {
                request.setEncryptionContext(encryptionContext);
            }

            com.aliyun.kms20160120.models.GenerateDataKeyResponse response =
                    kmsClient.generateDataKey(request);
            String plaintextBase64 = response.getBody().getPlaintext();
            String ciphertextBase64 = response.getBody().getCiphertextBlob();

            byte[] plaintext = Base64.getDecoder().decode(plaintextBase64);
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);

            return new GeneratedKey(plaintext, new WrappedKey(ciphertext, KMS_DATA_KEY));
        } catch (Exception e) {
            throw new CryptoException("KMS GenerateDataKey failed for keyId=" + keyId, e);
        }
    }

    @Override
    public WrappedKey wrap(byte[] plaintextKey) {
        if (mode == Mode.SYMMETRIC) {
            throw new UnsupportedOperationException("Wrap is not supported in SYMMETRIC mode");
        }
        if (isRsa) {
            return rsaWrap(plaintextKey);
        }
        throw new UnsupportedOperationException("SM2 wrap is not yet implemented (Phase 2)");
    }

    @Override
    public byte[] unwrap(WrappedKey wrappedKey) {
        if (mode == Mode.SYMMETRIC) {
            return unwrapSymmetric(wrappedKey);
        }
        return unwrapAsymmetric(wrappedKey);
    }

    private byte[] unwrapSymmetric(WrappedKey wrappedKey) {
        if (!KMS_DATA_KEY.equals(wrappedKey.algorithm())) {
            throw new IllegalArgumentException(
                    "Unexpected WrappedKey algorithm: '" + wrappedKey.algorithm()
                            + "'. Expected: " + KMS_DATA_KEY);
        }
        try {
            String ciphertextBlob = Base64.getEncoder().encodeToString(wrappedKey.ciphertext());

            com.aliyun.kms20160120.models.DecryptRequest request =
                    new com.aliyun.kms20160120.models.DecryptRequest()
                            .setCiphertextBlob(ciphertextBlob);
            if (encryptionContext != null && !encryptionContext.isEmpty()) {
                request.setEncryptionContext(encryptionContext);
            }

            com.aliyun.kms20160120.models.DecryptResponse response =
                    kmsClient.decrypt(request);
            String plaintextBase64 = response.getBody().getPlaintext();
            return Base64.getDecoder().decode(plaintextBase64);
        } catch (Exception e) {
            throw new CryptoException("KMS Decrypt failed for keyId=" + keyId, e);
        }
    }

    private byte[] unwrapAsymmetric(WrappedKey wrappedKey) {
        try {
            String kmsAlgorithm = mapToKmsAlgorithm(wrappedKey.algorithm());
            String ciphertextBlob = Base64.getEncoder().encodeToString(wrappedKey.ciphertext());

            com.aliyun.kms20160120.models.AsymmetricDecryptRequest request =
                    new com.aliyun.kms20160120.models.AsymmetricDecryptRequest()
                            .setKeyId(keyId)
                            .setCiphertextBlob(ciphertextBlob)
                            .setAlgorithm(kmsAlgorithm);
            if (keyVersionId != null) {
                request.setKeyVersionId(keyVersionId);
            }

            com.aliyun.kms20160120.models.AsymmetricDecryptResponse response =
                    kmsClient.asymmetricDecrypt(request);
            String plaintextBase64 = response.getBody().getPlaintext();
            return Base64.getDecoder().decode(plaintextBase64);
        } catch (Exception e) {
            throw new CryptoException("KMS AsymmetricDecrypt failed for keyId=" + keyId, e);
        }
    }

    private WrappedKey rsaWrap(byte[] plaintextKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, RSA_OAEP_SPEC);
            byte[] ciphertext = cipher.doFinal(plaintextKey);
            return new WrappedKey(ciphertext, ALGORITHM_RSA_OAEP_SHA256);
        } catch (Exception e) {
            throw new CryptoException("RSA-OAEP wrap failed", e);
        }
    }

    private static String mapToKmsAlgorithm(String wrappedAlgorithm) {
        if (ALGORITHM_RSA_OAEP_SHA256.equals(wrappedAlgorithm)) {
            return KMS_ALGORITHM_RSA;
        }
        if ("SM2PKE".equals(wrappedAlgorithm)) {
            return "SM2PKE";
        }
        throw new IllegalArgumentException(
                "Unknown WrappedKey algorithm: '" + wrappedAlgorithm
                        + "'. Expected RSAES-OAEP-SHA256 or SM2PKE");
    }
}
