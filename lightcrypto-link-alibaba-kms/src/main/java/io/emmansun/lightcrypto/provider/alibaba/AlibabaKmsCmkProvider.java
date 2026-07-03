package io.emmansun.lightcrypto.provider.alibaba;

import io.emmansun.lightcrypto.exception.CryptoException;
import io.emmansun.lightcrypto.model.WrappedKey;
import io.emmansun.lightcrypto.provider.CmkProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
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
public class AlibabaKmsCmkProvider implements CmkProvider {

    private static final String PROVIDER_ID = "alibaba-kms";

    /** WrappedKey algorithm identifier for RSA-OAEP wrapping. */
    static final String ALGORITHM_RSA_OAEP_SHA256 = "RSAES-OAEP-SHA256";

    /** Alibaba KMS AsymmetricDecrypt API Algorithm parameter value for RSA-OAEP. */
    private static final String KMS_ALGORITHM_RSA = "RSAES_OAEP_SHA_256";

    /**
     * OAEP parameters: SHA-256 hash + MGF1-SHA-256 (matching KMS RSAES_OAEP_SHA_256).
     * Java's default OAEPWithSHA-256AndMGF1Padding uses MGF1-SHA-1 which is incompatible.
     */
    private static final OAEPParameterSpec RSA_OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

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
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
        this.keyVersionId = keyVersionId;
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.kmsClient = Objects.requireNonNull(kmsClient, "kmsClient must not be null");

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

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public WrappedKey wrap(byte[] plaintextKey) {
        if (isRsa) {
            return rsaWrap(plaintextKey);
        }
        throw new UnsupportedOperationException("SM2 wrap is not yet implemented (Phase 2)");
    }

    @Override
    public byte[] unwrap(WrappedKey wrappedKey) {
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
