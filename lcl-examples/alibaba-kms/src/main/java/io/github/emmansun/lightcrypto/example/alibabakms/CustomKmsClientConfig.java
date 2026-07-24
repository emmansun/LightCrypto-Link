package io.github.emmansun.lightcrypto.example.alibabakms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Example: providing a fully custom Alibaba Cloud KMS client.
 * <p>
 * Activate with {@code --spring.profiles.active=custom-client}.
 * When this bean is present, LCL skips internal client construction
 * and uses this client directly for all KMS operations.
 * </p>
 * <p>
 * Use this pattern when you need control over:
 * <ul>
 *   <li>HTTP/HTTPS proxy settings</li>
 *   <li>Custom read/connect timeouts</li>
 *   <li>VPC endpoint routing</li>
 *   <li>Custom retry or connection pool settings</li>
 * </ul>
 * </p>
 */
@Configuration
@Profile("custom-client")
public class CustomKmsClientConfig {

    @Bean
    public com.aliyun.kms20160120.Client alibabaKmsClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config =
                new com.aliyun.teaopenapi.models.Config()
                        .setAccessKeyId(System.getenv("ALIBABA_AK_ID"))
                        .setAccessKeySecret(System.getenv("ALIBABA_AK_SECRET"))
                        // VPC endpoint for private network access
                        .setEndpoint("kms-vpc.cn-shenzhen.aliyuncs.com")
                        // Custom timeouts (milliseconds)
                        .setReadTimeout(30000)
                        .setConnectTimeout(10000);
        // Additional options:
        // .setHttpProxy("http://proxy:8080")
        // .setHttpsProxy("https://proxy:8443")
        // .setIgnoreSSL(true)
        return new com.aliyun.kms20160120.Client(config);
    }
}
