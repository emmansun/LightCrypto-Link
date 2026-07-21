package io.github.emmansun.lightcrypto.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class that reads the LCL SDK version from {@code lcl-build.properties}.
 * <p>
 * The version is populated by Maven resource filtering during build.
 *
 * @since 1.0.0
 */
public final class SdkVersion {

    private static final String PROPERTIES_FILE = "lcl-build.properties";
    private static final String VERSION_KEY = "lcl.sdk.version";
    private static final String UNKNOWN = "unknown";

    private static final String VERSION;

    static {
        String v = UNKNOWN;
        try (InputStream is = SdkVersion.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                v = props.getProperty(VERSION_KEY, UNKNOWN);
            }
        } catch (IOException e) {
            // fall through with "unknown"
        }
        VERSION = v;
    }

    private SdkVersion() {
    }

    /**
     * Returns the LCL SDK version (e.g., "1.0.0-SNAPSHOT").
     * Returns "unknown" if the version cannot be determined.
     */
    public static String getVersion() {
        return VERSION;
    }
}
