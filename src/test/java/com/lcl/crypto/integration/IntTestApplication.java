package com.lcl.crypto.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for integration tests.
 * Uses embedded MongoDB via flapdoodle auto-configuration.
 * Crypto auto-configuration (LightCryptoLinkAutoConfiguration) is
 * automatically picked up via META-INF/spring/...AutoConfiguration.imports.
 */
@SpringBootApplication
public class IntTestApplication {
}
