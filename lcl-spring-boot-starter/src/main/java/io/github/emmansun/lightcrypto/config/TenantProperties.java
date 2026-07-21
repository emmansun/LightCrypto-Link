package io.github.emmansun.lightcrypto.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tenant configuration properties.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "lightcrypto.tenants")
public class TenantProperties {

    /** Tenant identifier for namespace construction (default: "default"). */
    @NotBlank
    private String tenant = "default";

    /** Realm identifier for namespace construction (default: "default"). */
    @NotBlank
    private String realm = "default";
}
