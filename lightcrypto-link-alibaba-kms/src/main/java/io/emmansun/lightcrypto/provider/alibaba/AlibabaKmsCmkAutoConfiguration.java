package io.emmansun.lightcrypto.provider.alibaba;

import io.emmansun.lightcrypto.provider.CmkProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.security.PublicKey;

/**
 * Auto-configuration for Alibaba Cloud KMS CMK provider.
 * <p>
 * Activated when {@code lcl.crypto.alibaba.key-id} is set. Creates an
 * {@link AlibabaKmsCmkProvider} bean that takes precedence over the default
 * {@code LocalSymmetricCmkProvider} via {@code @ConditionalOnMissingBean}.
 * </p>
 * <p>
 * Startup flow: {@code ListKeyVersions} resolves the keyVersionId, then
 * {@code GetPublicKey} fetches the PEM for local wrap operations.
 * The key algorithm (RSA/SM2) is auto-detected from the public key material.
 * </p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(AlibabaKmsCmkProvider.class)
@ConditionalOnProperty(prefix = "lcl.crypto.alibaba", name = "key-id")
@EnableConfigurationProperties(AlibabaKmsCmkProperties.class)
public class AlibabaKmsCmkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CmkProvider.class)
    public CmkProvider cmkProvider(AlibabaKmsCmkProperties properties) {
        validateProperties(properties);

        com.aliyun.kms20160120.Client kmsClient = buildKmsClient(properties);

        // Step 1: Resolve keyVersionId via ListKeyVersions
        String keyVersionId = resolveKeyVersionId(properties, kmsClient);

        // Step 2: Load/fetch public key for local wrap (algorithm auto-detected)
        PublicKey publicKey = resolvePublicKey(properties, kmsClient, keyVersionId);

        log.info("Alibaba KMS CMK provider initialized: keyId={}, keyVersionId={}, keyType={}",
                properties.getKeyId(), keyVersionId, publicKey.getAlgorithm());

        return new AlibabaKmsCmkProvider(
                properties.getKeyId(),
                keyVersionId,
                publicKey,
                kmsClient);
    }

    private void validateProperties(AlibabaKmsCmkProperties properties) {
        if (properties.getAccessKeyId() == null || properties.getAccessKeyId().isBlank()) {
            throw new IllegalArgumentException(
                    "lcl.crypto.alibaba.access-key-id must not be null or blank");
        }
        if (properties.getAccessKeySecret() == null || properties.getAccessKeySecret().isBlank()) {
            throw new IllegalArgumentException(
                    "lcl.crypto.alibaba.access-key-secret must not be null or blank");
        }
    }

    private com.aliyun.kms20160120.Client buildKmsClient(AlibabaKmsCmkProperties properties) {
        try {
            com.aliyun.teaopenapi.models.Config config =
                    new com.aliyun.teaopenapi.models.Config()
                            .setAccessKeyId(properties.getAccessKeyId())
                            .setAccessKeySecret(properties.getAccessKeySecret())
                            .setRegionId(properties.getRegionId());
            if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
                config.setEndpoint(properties.getEndpoint());
            }
            return new com.aliyun.kms20160120.Client(config);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to create Alibaba Cloud KMS client: " + e.getMessage(), e);
        }
    }

    private String resolveKeyVersionId(AlibabaKmsCmkProperties properties,
                                        com.aliyun.kms20160120.Client kmsClient) {
        String configured = properties.getKeyVersionId();
        if (configured != null && !configured.isBlank()) {
            log.info("Using pre-configured keyVersionId={}", configured);
            return configured;
        }

        log.info("Resolving keyVersionId via ListKeyVersions: keyId={}", properties.getKeyId());
        try {
            com.aliyun.kms20160120.models.ListKeyVersionsRequest request =
                    new com.aliyun.kms20160120.models.ListKeyVersionsRequest()
                            .setKeyId(properties.getKeyId())
                            .setPageNumber(1)
                            .setPageSize(1);
            com.aliyun.kms20160120.models.ListKeyVersionsResponse response =
                    kmsClient.listKeyVersions(request);
            var versions = response.getBody().getKeyVersions().getKeyVersion();
            if (versions == null || versions.isEmpty()) {
                throw new IllegalStateException(
                        "ListKeyVersions returned no versions for keyId=" + properties.getKeyId());
            }
            return versions.get(0).getKeyVersionId();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to resolve keyVersionId via ListKeyVersions for keyId="
                            + properties.getKeyId() + ": " + e.getMessage(), e);
        }
    }

    private PublicKey resolvePublicKey(AlibabaKmsCmkProperties properties,
                                        com.aliyun.kms20160120.Client kmsClient,
                                        String keyVersionId) {
        String pem = properties.getPublicKey();
        if (pem != null && !pem.isBlank()) {
            log.info("Using pre-configured public key");
            return PublicKeyLoader.loadFromPem(pem);
        }

        log.info("Fetching public key from KMS: keyId={}, keyVersionId={}",
                properties.getKeyId(), keyVersionId);
        try {
            com.aliyun.kms20160120.models.GetPublicKeyRequest request =
                    new com.aliyun.kms20160120.models.GetPublicKeyRequest()
                            .setKeyId(properties.getKeyId())
                            .setKeyVersionId(keyVersionId);
            com.aliyun.kms20160120.models.GetPublicKeyResponse response =
                    kmsClient.getPublicKey(request);
            return PublicKeyLoader.loadFromPem(response.getBody().getPublicKey());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to fetch public key from KMS for keyId=" + properties.getKeyId()
                            + ": " + e.getMessage(), e);
        }
    }
}
