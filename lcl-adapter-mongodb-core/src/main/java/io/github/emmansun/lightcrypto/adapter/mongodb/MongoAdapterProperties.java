package io.github.emmansun.lightcrypto.adapter.mongodb;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MongoDB adapter configuration properties.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "lightcrypto.adapters.mongodb")
public class MongoAdapterProperties {

    /** Whether the MongoDB adapter is enabled (default: true). */
    private boolean enabled = true;

    /** Key vault collection name (default: "__lcl_keyvault"). */
    private String keyVaultCollection = "__lcl_keyvault";

    /** Auto-initialize the vault on first startup (default: true). */
    private boolean autoInit = true;

    /** Database name where the Key Vault collection resides (defaults to the application database). */
    private String keyVaultDatabase;
}
