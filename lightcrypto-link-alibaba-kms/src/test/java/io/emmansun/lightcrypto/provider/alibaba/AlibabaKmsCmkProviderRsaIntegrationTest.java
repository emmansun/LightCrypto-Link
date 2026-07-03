package io.emmansun.lightcrypto.provider.alibaba;

import io.emmansun.lightcrypto.model.WrappedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.security.PublicKey;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that require real Alibaba Cloud KMS credentials.
 * Skipped in CI when environment variables are not set.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code ALIBABA_AK_ID} — access key ID</li>
 *   <li>{@code ALIBABA_AK_SECRET} — access key secret</li>
 *   <li>{@code ALIBABA_KMS_REGION} — region ID (e.g. cn-shenzhen), optional if endpoint is set</li>
 *   <li>{@code ALIBABA_KMS_ENDPOINT} — custom KMS endpoint (e.g. kms.cn-shenzhen.aliyuncs.com), optional</li>
 *   <li>{@code ALIBABA_KMS_KEY_ID} — RSA CMK key ID</li>
 * </ul>
 *
 * <p>The {@code keyVersionId} is resolved automatically via {@code ListKeyVersions} API.</p>
 */
@EnabledIfEnvironmentVariable(named = "ALIBABA_AK_ID", matches = ".+")
class AlibabaKmsCmkProviderRsaIntegrationTest {

    @Test
    void rsaWrapAndUnwrap_shouldRoundtrip() throws Exception {
        String keyId = System.getenv("ALIBABA_KMS_KEY_ID");
        com.aliyun.kms20160120.Client kmsClient = buildKmsClient();

        // Resolve keyVersionId via ListKeyVersions
        String keyVersionId = resolveKeyVersionId(kmsClient, keyId);
        assertThat(keyVersionId).isNotBlank();

        // Fetch public key via GetPublicKey
        PublicKey publicKey = fetchPublicKey(kmsClient, keyId, keyVersionId);

        AlibabaKmsCmkProvider provider =
                new AlibabaKmsCmkProvider(keyId, keyVersionId, publicKey, kmsClient, "RSA");

        byte[] plaintextKey = new byte[32];
        new SecureRandom().nextBytes(plaintextKey);

        WrappedKey wrapped = provider.wrap(plaintextKey);
        assertThat(wrapped.algorithm()).isEqualTo("RSAES-OAEP-SHA256");

        byte[] unwrapped = provider.unwrap(wrapped);
        assertThat(unwrapped).isEqualTo(plaintextKey);
    }

    @Test
    void rsaWrapAndUnwrap_withAutoFetchedPublicKey_shouldRoundtrip() throws Exception {
        String keyId = System.getenv("ALIBABA_KMS_KEY_ID");
        com.aliyun.kms20160120.Client kmsClient = buildKmsClient();

        // Resolve keyVersionId + publicKey from KMS
        String keyVersionId = resolveKeyVersionId(kmsClient, keyId);
        PublicKey publicKey = fetchPublicKey(kmsClient, keyId, keyVersionId);

        AlibabaKmsCmkProvider provider =
                new AlibabaKmsCmkProvider(keyId, keyVersionId, publicKey, kmsClient, "RSA");

        byte[] plaintextKey = new byte[32];
        new SecureRandom().nextBytes(plaintextKey);

        WrappedKey wrapped = provider.wrap(plaintextKey);
        byte[] unwrapped = provider.unwrap(wrapped);
        assertThat(unwrapped).isEqualTo(plaintextKey);
    }

    private com.aliyun.kms20160120.Client buildKmsClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config =
                new com.aliyun.teaopenapi.models.Config()
                        .setAccessKeyId(System.getenv("ALIBABA_AK_ID"))
                        .setAccessKeySecret(System.getenv("ALIBABA_AK_SECRET"));
        String endpoint = System.getenv("ALIBABA_KMS_ENDPOINT");
        if (endpoint != null && !endpoint.isBlank()) {
            config.setEndpoint(endpoint);
        } else {
            config.setRegionId(System.getenv("ALIBABA_KMS_REGION"));
        }
        return new com.aliyun.kms20160120.Client(config);
    }

    private String resolveKeyVersionId(com.aliyun.kms20160120.Client kmsClient, String keyId)
            throws Exception {
        var resp = kmsClient.listKeyVersions(
                new com.aliyun.kms20160120.models.ListKeyVersionsRequest()
                        .setKeyId(keyId)
                        .setPageNumber(1)
                        .setPageSize(1));
        return resp.getBody().getKeyVersions().getKeyVersion().get(0).getKeyVersionId();
    }

    private PublicKey fetchPublicKey(com.aliyun.kms20160120.Client kmsClient,
                                      String keyId, String keyVersionId) throws Exception {
        var resp = kmsClient.getPublicKey(
                new com.aliyun.kms20160120.models.GetPublicKeyRequest()
                        .setKeyId(keyId)
                        .setKeyVersionId(keyVersionId));
        return PublicKeyLoader.loadFromPem(resp.getBody().getPublicKey(), "RSA");
    }
}
