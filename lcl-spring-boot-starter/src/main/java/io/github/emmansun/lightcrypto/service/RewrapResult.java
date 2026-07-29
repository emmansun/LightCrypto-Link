package io.github.emmansun.lightcrypto.service;

/**
 * Result of a single namespace re-wrap operation.
 *
 * @param namespace the canonical namespace that was re-wrapped
 * @param success whether the re-wrap succeeded
 * @param keyCount number of key entries re-wrapped
 * @param errorMessage error message if failed, null if success
 * @param durationMicros elapsed time in microseconds
 */
public record RewrapResult(
        String namespace,
        boolean success,
        int keyCount,
        String errorMessage,
        long durationMicros) {

    public static RewrapResult success(String namespace, int keyCount, long durationMicros) {
        return new RewrapResult(namespace, true, keyCount, null, durationMicros);
    }

    public static RewrapResult failure(String namespace, String errorMessage, long durationMicros) {
        return new RewrapResult(namespace, false, 0, errorMessage, durationMicros);
    }
}
