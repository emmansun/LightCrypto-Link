package com.lcl.crypto.integration;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Resolves active profiles for integration tests.
 * When running on GitHub Actions (GITHUB_ACTIONS=true), adds the "ci" profile
 * which uses a real MongoDB instead of flapdoodle embedded MongoDB.
 */
public class CiProfileResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        boolean isCi = "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
        return isCi ? new String[]{"test", "ci"} : new String[]{"test"};
    }
}
