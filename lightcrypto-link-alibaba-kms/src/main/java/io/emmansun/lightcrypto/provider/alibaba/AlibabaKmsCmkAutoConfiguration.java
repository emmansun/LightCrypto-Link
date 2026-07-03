package io.emmansun.lightcrypto.provider.alibaba;

import io.emmansun.lightcrypto.provider.CmkProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Alibaba Cloud KMS CMK provider.
 * Activated when {@code lcl.crypto.alibaba.key-id} is set.
 */
@AutoConfiguration
@ConditionalOnClass(AlibabaKmsCmkProvider.class)
@ConditionalOnProperty(prefix = "lcl.crypto.alibaba", name = "key-id")
@EnableConfigurationProperties(AlibabaKmsCmkProperties.class)
public class AlibabaKmsCmkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CmkProvider.class)
    public CmkProvider cmkProvider(AlibabaKmsCmkProperties properties) {
        return new AlibabaKmsCmkProvider(
                properties.getRegionId(),
                properties.getKeyId(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }
}
