package io.github.emmansun.lightcrypto.example.basiccrudv4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal demo application for LightCrypto-Link on Spring Boot 4.x.
 * Demonstrates transparent field encryption, decryption, and blind index queries.
 */
@SpringBootApplication
public class BasicCrudV4Application {
    public static void main(String[] args) {
        SpringApplication.run(BasicCrudV4Application.class, args);
    }
}
