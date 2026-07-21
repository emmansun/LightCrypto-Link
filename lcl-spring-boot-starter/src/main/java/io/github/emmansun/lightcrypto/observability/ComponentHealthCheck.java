package io.github.emmansun.lightcrypto.observability;

/**
 * Functional interface for component-level health checks.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface ComponentHealthCheck {

    /**
     * Perform a health check and return the component's current status.
     *
     * @return the health status of this component
     */
    LclHealthStatus check();
}
