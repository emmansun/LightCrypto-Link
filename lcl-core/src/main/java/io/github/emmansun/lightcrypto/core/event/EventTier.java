package io.github.emmansun.lightcrypto.core.event;

/**
 * Event tier classification for LCL events.
 * <ul>
 *   <li>{@link #L1} — Diagnostic, best-effort delivery</li>
 *   <li>{@link #L2} — Operational, reliable delivery for monitoring</li>
 *   <li>{@link #L3} — Audit, guaranteed delivery for compliance</li>
 * </ul>
 *
 * @since 1.0.0
 */
public enum EventTier {

    /** Diagnostic events — best-effort delivery (e.g., cache eviction). */
    L1,

    /** Operational events — reliable delivery for monitoring (e.g., encrypt/decrypt/rotation). */
    L2,

    /** Audit events — guaranteed delivery for compliance. */
    L3
}
