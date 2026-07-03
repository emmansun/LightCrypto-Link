package io.github.emmansun.lightcrypto.example.alibabakms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo application for LightCrypto-Link with Alibaba Cloud KMS as CMK provider.
 * DEK wrapping is done locally using RSA-OAEP (zero network overhead),
 * unwrapping is done via KMS AsymmetricDecrypt.
 */
@SpringBootApplication
public class AlibabaKmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlibabaKmsApplication.class, args);
    }
}
