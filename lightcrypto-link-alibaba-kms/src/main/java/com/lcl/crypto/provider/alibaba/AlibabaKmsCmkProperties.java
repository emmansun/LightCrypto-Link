package com.lcl.crypto.provider.alibaba;

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

    /** CMK key ID in Alibaba Cloud KMS. */
    private String keyId;

    /** Alibaba Cloud access key ID. */
    private String accessKeyId;

    /** Alibaba Cloud access key secret. */
    private String accessKeySecret;
}
