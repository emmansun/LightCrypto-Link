package io.github.emmansun.lightcrypto.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Runtime configuration properties for LightCrypto-Link.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "lightcrypto.runtime")
public class RuntimeProperties {

    /** SPI version (default: 1). */
    @Min(1)
    private int spiVersion = 1;

    /** Runtime mode (default: SPRING_BOOT). */
    private Mode mode = Mode.SPRING_BOOT;

    /** When true, non-fatal configuration warnings are treated as errors (default: true). */
    private boolean strictMode = true;

    /** Runtime mode enum. */
    public enum Mode {
        SPRING_BOOT,
        STANDALONE,
        MIGRATION
    }
}
