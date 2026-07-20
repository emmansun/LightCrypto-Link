package io.github.emmansun.lightcrypto.core.namespace;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable multi-tenant namespace model for Light Crypto Link.
 *
 * <p>Format: {@code <tenant>.<realm>.<entity>#<field>}
 * <ul>
 *   <li>Shorthand {@code entity#field} expands to {@code default.default.entity#field}</li>
 *   <li>Two-segment path (one dot before #) is forbidden (ambiguous)</li>
 *   <li>Segments allow {@code [a-zA-Z0-9_-]}; field allows additional {@code .} for nested paths</li>
 *   <li>Case-sensitive; max 256 UTF-8 bytes in canonical form</li>
 * </ul>
 *
 * @param tenant the tenant segment
 * @param realm  the realm segment
 * @param entity the entity segment
 * @param field  the field segment (may contain dots for nested paths)
 * @since 1.0.0
 */
public record Namespace(String tenant, String realm, String entity, String field) {

    private static final String DEFAULT = "default";
    private static final int MAX_UTF8_BYTES = 256;
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-]+");
    private static final Pattern FIELD_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-]+(\\.[a-zA-Z0-9_\\-]+)*");

    public Namespace {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(realm, "realm must not be null");
        Objects.requireNonNull(entity, "entity must not be null");
        Objects.requireNonNull(field, "field must not be null");
    }

    /**
     * Creates a Namespace from explicit segments.
     *
     * @param tenant the tenant segment
     * @param realm  the realm segment
     * @param entity the entity segment
     * @param field  the field segment
     * @return a validated Namespace instance
     * @throws IllegalArgumentException if any segment is invalid
     */
    public static Namespace of(String tenant, String realm, String entity, String field) {
        Namespace ns = new Namespace(tenant, realm, entity, field);
        ns.validate();
        return ns;
    }

    /**
     * Parses a namespace string into a Namespace instance.
     *
     * <p>Accepted forms:
     * <ul>
     *   <li>{@code tenant.realm.entity#field} — full four-segment form</li>
     *   <li>{@code entity#field} — shorthand, expands to {@code default.default.entity#field}</li>
     * </ul>
     *
     * <p>Rejected forms:
     * <ul>
     *   <li>{@code realm.entity#field} — two-segment path (one dot before #) is ambiguous</li>
     * </ul>
     *
     * @param raw the namespace string to parse
     * @return a validated Namespace instance
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Namespace parse(String raw) {
        Objects.requireNonNull(raw, "namespace string must not be null");
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("namespace string must not be empty");
        }

        int hashIdx = raw.indexOf('#');
        if (hashIdx < 0) {
            throw new IllegalArgumentException(
                    "namespace must contain '#' separator between path and field: " + raw);
        }
        if (hashIdx == 0) {
            throw new IllegalArgumentException("namespace path before '#' must not be empty: " + raw);
        }
        if (hashIdx == raw.length() - 1) {
            throw new IllegalArgumentException("namespace field after '#' must not be empty: " + raw);
        }

        String path = raw.substring(0, hashIdx);
        String field = raw.substring(hashIdx + 1);

        // Validate field (allows dots for nested paths)
        if (!FIELD_PATTERN.matcher(field).matches()) {
            throw new IllegalArgumentException(
                    "namespace field contains invalid characters (allowed: [a-zA-Z0-9_-.]): " + field);
        }

        // Parse path segments
        String[] segments = path.split("\\.", -1);

        String tenant;
        String realm;
        String entity;

        switch (segments.length) {
            case 1 -> {
                // Shorthand: entity#field
                tenant = DEFAULT;
                realm = DEFAULT;
                entity = segments[0];
            }
            case 2 -> throw new IllegalArgumentException(
                    "ambiguous two-segment namespace path (use 'entity#field' or 'tenant.realm.entity#field'): " + raw);
            case 3 -> {
                tenant = segments[0];
                realm = segments[1];
                entity = segments[2];
            }
            default -> throw new IllegalArgumentException(
                    "namespace path must have 1 or 3 dot-separated segments, got " + segments.length + ": " + raw);
        }

        Namespace ns = new Namespace(tenant, realm, entity, field);
        ns.validate();
        return ns;
    }

    /**
     * Returns the canonical string form: {@code tenant.realm.entity#field}.
     *
     * @return the canonical namespace string
     */
    public String canonical() {
        return tenant + "." + realm + "." + entity + "#" + field;
    }

    /**
     * Returns the UTF-8 byte representation of the canonical form.
     *
     * @return canonical namespace as UTF-8 bytes
     */
    public byte[] canonicalBytes() {
        return canonical().getBytes(StandardCharsets.UTF_8);
    }

    private void validate() {
        validateSegment("tenant", tenant, false);
        validateSegment("realm", realm, false);
        validateSegment("entity", entity, false);
        // field allows dots, validated by FIELD_PATTERN during parse; for of(), check here
        if (!FIELD_PATTERN.matcher(field).matches()) {
            throw new IllegalArgumentException(
                    "namespace field contains invalid characters (allowed: [a-zA-Z0-9_-.]): " + field);
        }
        int byteLen = canonical().getBytes(StandardCharsets.UTF_8).length;
        if (byteLen > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "namespace exceeds 256-byte UTF-8 limit (actual: " + byteLen + " bytes)");
        }
    }

    private static void validateSegment(String name, String value, boolean allowDot) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("namespace " + name + " segment must not be empty");
        }
        if (!SEGMENT_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "namespace " + name + " contains invalid characters (allowed: [a-zA-Z0-9_-]): " + value);
        }
    }

    @Override
    public String toString() {
        return canonical();
    }
}
