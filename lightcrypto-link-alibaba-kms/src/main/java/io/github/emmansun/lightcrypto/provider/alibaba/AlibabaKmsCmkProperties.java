package io.github.emmansun.lightcrypto.provider.alibaba;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Alibaba Cloud KMS CMK provider.
 */
@Data
@ConfigurationProperties(prefix = "lcl.crypto.alibaba")
public class AlibabaKmsCmkProperties {

    /** Alibaba Cloud region ID (e.g. cn-hangzhou). */
    private String regionId;

    /**
     * Optional custom KMS endpoint URL (e.g. {@code kms-vpc.cn-shenzhen.aliyuncs.com} for VPC access).
     * If not set, the SDK derives the endpoint from {@code regionId} (public network).
     */
    private String endpoint;

    /** CMK key ID in Alibaba Cloud KMS. */
    private String keyId;

    /**
     * CMK key version ID in Alibaba Cloud KMS.
     * Optional — resolved automatically from the {@code GetPublicKey} API response at startup.
     * Retained for advanced use cases where a specific key version is required.
     */
    private String keyVersionId;

    /** Alibaba Cloud access key ID. */
    private String accessKeyId;

    /** Alibaba Cloud access key secret. */
    private String accessKeySecret;

    /**
     * Optional PEM-encoded public key (X.509 SubjectPublicKeyInfo).
     * If not set, the provider fetches the public key from KMS via GetPublicKey API at startup.
     * The key type (RSA or EC/SM2) is auto-detected from the key material.
     */
    private String publicKey;
}
