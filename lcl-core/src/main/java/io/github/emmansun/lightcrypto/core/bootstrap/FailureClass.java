package io.github.emmansun.lightcrypto.core.bootstrap;

/**
 * Failure classification for bootstrap phases.
 * <ul>
 *   <li>{@link #FATAL} — abort startup immediately</li>
 *   <li>{@link #RECOVERABLE} — retry allowed (up to 3 times with backoff)</li>
 *   <li>{@link #ADVISORY} — log warning and continue</li>
 * </ul>
 *
 * @since 1.0.0
 */
public enum FailureClass {

    /** Abort startup immediately. */
    FATAL,

    /** Retry allowed; in strict mode escalates to FATAL after retries exhausted. */
    RECOVERABLE,

    /** Log warning and continue startup. */
    ADVISORY
}
