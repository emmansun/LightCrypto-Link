package io.github.emmansun.lightcrypto.example.basiccrud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal demo application for LightCrypto-Link.
 * Demonstrates transparent field encryption, decryption, and blind index queries.
 */
@SpringBootApplication
public class BasicCrudApplication {
    public static void main(String[] args) {
        SpringApplication.run(BasicCrudApplication.class, args);
    }
}
