package io.emmansun.lightcrypto.provider.alibaba;

import io.emmansun.lightcrypto.model.WrappedKey;
import io.emmansun.lightcrypto.provider.CmkProvider;

/**
 * Alibaba Cloud KMS CMK provider — wraps/unwraps DEKs via Alibaba Cloud KMS API.
 * <p>
 * This is a v2 roadmap skeleton. Actual implementation requires:
 * <ul>
 *   <li>alibabacloud-kms20160120 SDK + aliyun-java-sdk-core</li>
 *   <li>Encrypt/Decrypt API calls with RSA-OAEP key wrapping</li>
 * </ul>
 * </p>
 *
 * <h3>Configuration</h3>
 * <pre>
 * lcl:
 *   crypto:
 *     alibaba:
 *       region-id: cn-hangzhou
 *       key-id: key-xxxxxxxx
 *       access-key-id: ${ALIBABA_ACCESS_KEY_ID}
 *       access-key-secret: ${ALIBABA_ACCESS_KEY_SECRET}
 * </pre>
 */
public class AlibabaKmsCmkProvider implements CmkProvider {

    private static final String PROVIDER_ID = "alibaba-kms";
    private static final String ALGORITHM = "ALIBABA-KMS-RSA";

    private final String regionId;
    private final String keyId;
    private final String accessKeyId;
    private final String accessKeySecret;

    public AlibabaKmsCmkProvider(String regionId, String keyId,
                                  String accessKeyId, String accessKeySecret) {
        this.regionId = regionId;
        this.keyId = keyId;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public WrappedKey wrap(byte[] plaintextKey) {
        // TODO: Implement Alibaba Cloud KMS Encrypt API call
        // 1. Create KMS client with regionId, accessKeyId, accessKeySecret
        // 2. Call Encrypt API with keyId and plaintext (base64-encoded)
        // 3. Return WrappedKey with ciphertext blob
        throw new UnsupportedOperationException(
                "Alibaba Cloud KMS provider is a v2 roadmap feature. " +
                "Use LocalSymmetricCmkProvider (lcl.crypto.cmk) for production.");
    }

    @Override
    public byte[] unwrap(WrappedKey wrappedKey) {
        // TODO: Implement Alibaba Cloud KMS Decrypt API call
        // 1. Create KMS client with regionId, accessKeyId, accessKeySecret
        // 2. Call Decrypt API with ciphertext blob
        // 3. Return plaintext key bytes
        throw new UnsupportedOperationException(
                "Alibaba Cloud KMS provider is a v2 roadmap feature. " +
                "Use LocalSymmetricCmkProvider (lcl.crypto.cmk) for production.");
    }
}
