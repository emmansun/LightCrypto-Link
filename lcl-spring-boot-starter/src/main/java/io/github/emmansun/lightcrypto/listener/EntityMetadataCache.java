package io.github.emmansun.lightcrypto.listener;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.annotation.EncryptionMode;
import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.config.CryptographyProperties;
import io.github.emmansun.lightcrypto.config.TenantProperties;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.exception.UnsupportedTypeException;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import org.springframework.data.annotation.Transient;

import java.lang.annotation.Annotation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.emmansun.lightcrypto.service.TypeSerializer.isSupported;

/**
 * Entity metadata cache — scans @Encrypted annotated fields and caches the results.
 * <p>
 * On first access per entity class, all {@code @Encrypted} fields are discovered,
 * validated, and converted into pre-bound {@link MethodHandle} getters. Subsequent
 * accesses return the cached metadata list with zero reflection overhead.
 */
public class EntityMetadataCache {

    private static final int MAX_DEPTH = 5;

    private final CryptographyProperties cryptographyProperties;
    private final TenantProperties tenantProperties;
    private final Map<Class<?>, List<EncryptedFieldMetadata>> cache = new ConcurrentHashMap<>();

    public EntityMetadataCache(CryptographyProperties cryptographyProperties, TenantProperties tenantProperties) {
        this.cryptographyProperties = cryptographyProperties;
        this.tenantProperties = tenantProperties;
    }

    /**
     * Get the list of encrypted field metadata for the given entity.
     */
    public List<EncryptedFieldMetadata> getEncryptedFields(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, this::scanFields);
    }

    /**
     * Check whether the entity has any encrypted fields.
     */
    public boolean hasEncryptedFields(Class<?> entityClass) {
        return !getEncryptedFields(entityClass).isEmpty();
    }

    /**
     * Pre-warm the cache for the given entity classes.
     * Call this at startup to eliminate cold-start latency on first request.
     *
     * @param entityClasses the entity classes to pre-scan
     */
    public void preWarm(Class<?>... entityClasses) {
        for (Class<?> clazz : entityClasses) {
            getEncryptedFields(clazz);
        }
    }

    private List<EncryptedFieldMetadata> scanFields(Class<?> entityClass) {
        List<EncryptedFieldMetadata> result = new ArrayList<>();
        String entityName = entityClass.getSimpleName();
        scanFieldsRecursive(
                entityClass,
                entityName,
                result,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashSet<>(),
                0
        );
        return Collections.unmodifiableList(result);
    }

    private void scanFieldsRecursive(Class<?> currentClass,
                                     String rootEntityName,
                                     List<EncryptedFieldMetadata> result,
                                     List<MethodHandle> accessorPrefix,
                                     List<String> pathPrefix,
                                     List<PathSegmentType> pathTypePrefix,
                                     Set<Class<?>> visiting,
                                     int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("Maximum recursion depth " + MAX_DEPTH + " exceeded when scanning " + currentClass.getName());
        }
        if (!visiting.add(currentClass)) {
            throw new IllegalStateException("Circular reference detected while scanning class: " + currentClass.getName());
        }

        MethodHandles.Lookup rootLookup = MethodHandles.lookup();
        for (Field field : getAllFields(currentClass)) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (isExcludedField(field)) {
                continue;
            }

            MethodHandle getter = createGetter(currentClass, field, rootLookup);
            Encrypted encrypted = field.getAnnotation(Encrypted.class);
            Class<?> fieldType = field.getType();

            if (encrypted != null) {
                handleEncryptedField(result, accessorPrefix, pathPrefix, pathTypePrefix, field, fieldType, encrypted, getter, rootEntityName);
                continue;
            }

            if (isCollectionType(fieldType)) {
                Class<?> elementType = resolveCollectionElementType(field);
                if (isPojoType(elementType)) {
                    List<MethodHandle> nextAccessors = append(accessorPrefix, getter);
                    List<String> nextPath = append(pathPrefix, field.getName());
                    List<PathSegmentType> nextPathTypes = append(pathTypePrefix, PathSegmentType.LIST_ITER);
                    scanFieldsRecursive(elementType, rootEntityName, result, nextAccessors, nextPath, nextPathTypes, visiting, depth + 1);
                }
                continue;
            }

            if (isMapType(fieldType)) {
                Class<?> valueType = resolveMapValueType(field);
                if (isPojoType(valueType)) {
                    List<MethodHandle> nextAccessors = append(accessorPrefix, getter);
                    List<String> nextPath = append(pathPrefix, field.getName());
                    List<PathSegmentType> nextPathTypes = append(pathTypePrefix, PathSegmentType.MAP_ITER);
                    scanFieldsRecursive(valueType, rootEntityName, result, nextAccessors, nextPath, nextPathTypes, visiting, depth + 1);
                }
                continue;
            }

            if (isPojoType(fieldType)) {
                List<MethodHandle> nextAccessors = append(accessorPrefix, getter);
                List<String> nextPath = append(pathPrefix, field.getName());
                List<PathSegmentType> nextPathTypes = append(pathTypePrefix, PathSegmentType.FIELD);
                scanFieldsRecursive(fieldType, rootEntityName, result, nextAccessors, nextPath, nextPathTypes, visiting, depth + 1);
            }
        }

        visiting.remove(currentClass);
    }

    private void handleEncryptedField(List<EncryptedFieldMetadata> result,
                                      List<MethodHandle> accessorPrefix,
                                      List<String> pathPrefix,
                                      List<PathSegmentType> pathTypePrefix,
                                      Field field,
                                      Class<?> fieldType,
                                      Encrypted encrypted,
                                      MethodHandle getter,
                                      String rootEntityName) {
        SymmetricAlgorithm algo = (encrypted.algorithm() == SymmetricAlgorithm.DEFAULT)
                ? cryptographyProperties.getDefaultAlgorithm()
                : encrypted.algorithm();

        if (isCollectionType(fieldType)) {
            Class<?> elementType = resolveCollectionElementType(field);
            boolean wholeObject = resolveWholeObjectMode(field, elementType, encrypted, true);
            validateWholeObjectMode(field, elementType, wholeObject, encrypted);
            if (!wholeObject && !isSupported(elementType)) {
                throw new UnsupportedTypeException(
                        "Field '" + field.getName() + "' in class '" + field.getDeclaringClass().getName() +
                                "' has unsupported collection element type: " + elementType.getName());
            }
            List<String> fieldPath = append(pathPrefix, field.getName());
            result.add(new EncryptedFieldMetadata(
                    append(accessorPrefix, getter),
                    fieldPath,
                    append(pathTypePrefix, PathSegmentType.LIST_ITER),
                    elementType,
                    algo,
                    encrypted.blindIndex(),
                    wholeObject,
                    encrypted.fieldName().isEmpty() ? String.join(".", fieldPath) : encrypted.fieldName(),
                    buildNamespace(rootEntityName, fieldPath)
            ));
            return;
        }

        if (isMapType(fieldType)) {
            Class<?> valueType = resolveMapValueType(field);
            boolean wholeObject = resolveWholeObjectMode(field, valueType, encrypted, true);
            validateWholeObjectMode(field, valueType, wholeObject, encrypted);
            if (!wholeObject && !isSupported(valueType)) {
                throw new UnsupportedTypeException(
                        "Field '" + field.getName() + "' in class '" + field.getDeclaringClass().getName() +
                                "' has unsupported map value type: " + valueType.getName());
            }
            List<String> fieldPath = append(pathPrefix, field.getName());
            result.add(new EncryptedFieldMetadata(
                    append(accessorPrefix, getter),
                    fieldPath,
                    append(pathTypePrefix, PathSegmentType.MAP_ITER),
                    valueType,
                    algo,
                    encrypted.blindIndex(),
                    wholeObject,
                    encrypted.fieldName().isEmpty() ? String.join(".", fieldPath) : encrypted.fieldName(),
                    buildNamespace(rootEntityName, fieldPath)
            ));
            return;
        }

        boolean wholeObject = resolveWholeObjectMode(field, fieldType, encrypted, false);
        validateWholeObjectMode(field, fieldType, wholeObject, encrypted);
        if (!wholeObject && !isSupported(fieldType)) {
            throw new UnsupportedTypeException(
                    "Field '" + field.getName() + "' in class '" + field.getDeclaringClass().getName() +
                            "' has unsupported type: " + fieldType.getName());
        }

        List<String> fieldPath = append(pathPrefix, field.getName());
        result.add(new EncryptedFieldMetadata(
                append(accessorPrefix, getter),
                fieldPath,
                append(pathTypePrefix, PathSegmentType.FIELD),
                fieldType,
                algo,
                encrypted.blindIndex(),
                wholeObject,
                encrypted.fieldName().isEmpty() ? String.join(".", fieldPath) : encrypted.fieldName(),
                buildNamespace(rootEntityName, fieldPath)
        ));
    }

    /**
     * Builds a Namespace from the root entity name and field path.
     * Format: default.default.{EntityName}#{fieldPath}
     */
    private Namespace buildNamespace(String rootEntityName, List<String> fieldPath) {
        String tenant = tenantProperties != null ? tenantProperties.getTenant() : "default";
        String realm = tenantProperties != null ? tenantProperties.getRealm() : "default";
        String field = String.join(".", fieldPath);
        return Namespace.of(tenant, realm, rootEntityName, field);
    }

    private boolean resolveWholeObjectMode(Field field,
                                           Class<?> valueType,
                                           Encrypted encrypted,
                                           boolean containerField) {
        EncryptionMode mode = encrypted.mode();
        boolean pojoType = isPojoType(valueType);

        if (mode == EncryptionMode.WHOLE) {
            if (!containerField && !pojoType) {
                // Scalar/simple fields already use field-level encrypted sub-doc format.
                return false;
            }
            return true;
        }

        if (mode == EncryptionMode.ELEMENT) {
            if (containerField && pojoType) {
                throw new UnsupportedTypeException(
                        "Field '" + field.getName() + "' in class '" + field.getDeclaringClass().getName() +
                                "' cannot use mode=ELEMENT for POJO collection/map values. " +
                                "Use mode=WHOLE or remove @Encrypted from the container field and annotate nested fields instead.");
            }
            if (!containerField && pojoType) {
                throw new UnsupportedTypeException(
                        "Field '" + field.getName() + "' in class '" + field.getDeclaringClass().getName() +
                                "' cannot use mode=ELEMENT for POJO fields.");
            }
            return false;
        }

        // AUTO mode keeps existing behavior.
        return pojoType;
    }

    private MethodHandle createGetter(Class<?> ownerClass, Field field, MethodHandles.Lookup rootLookup) {
        try {
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(ownerClass, rootLookup);
            return privateLookup.unreflectGetter(field);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Failed to create MethodHandle getter for field '" + field.getName() +
                            "' in class '" + ownerClass.getName() + "'", e);
        }
    }

    private boolean isExcludedField(Field field) {
        return field.isAnnotationPresent(Transient.class) || hasAnnotationByName(field, "org.springframework.data.mongodb.core.mapping.DBRef");
    }

    private boolean hasAnnotationByName(Field field, String annotationClassName) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationClassName)) {
                return true;
            }
        }
        return false;
    }

    private void validateWholeObjectMode(Field field, Class<?> valueType, boolean wholeObject, Encrypted encrypted) {
        if (!wholeObject) {
            return;
        }
        if (encrypted.blindIndex()) {
            throw new UnsupportedTypeException(
                    "Field '" + field.getName() + "' in class '" + field.getDeclaringClass().getName() +
                            "' uses whole-object encryption and does not support blindIndex=true");
        }
        if (containsNestedEncryptedFields(valueType, new HashSet<>(), 0)) {
            throw new IllegalStateException(
                    "Field '" + field.getName() + "' uses whole-object encryption, but nested type '" + valueType.getName() +
                            "' also declares @Encrypted fields. Use either whole-object or field-level encryption.");
        }
    }

    private boolean containsNestedEncryptedFields(Class<?> type, Set<Class<?>> visiting, int depth) {
        if (depth > MAX_DEPTH || !visiting.add(type)) {
            return false;
        }
        for (Field field : getAllFields(type)) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (isExcludedField(field)) {
                continue;
            }
            if (field.isAnnotationPresent(Encrypted.class)) {
                return true;
            }

            Class<?> fieldType = field.getType();
            if (isCollectionType(fieldType)) {
                Class<?> elementType = resolveCollectionElementType(field);
                if (isPojoType(elementType) && containsNestedEncryptedFields(elementType, visiting, depth + 1)) {
                    return true;
                }
            } else if (isMapType(fieldType)) {
                Class<?> valueType = resolveMapValueType(field);
                if (isPojoType(valueType) && containsNestedEncryptedFields(valueType, visiting, depth + 1)) {
                    return true;
                }
            } else if (isPojoType(fieldType) && containsNestedEncryptedFields(fieldType, visiting, depth + 1)) {
                return true;
            }
        }
        visiting.remove(type);
        return false;
    }

    private boolean isCollectionType(Class<?> type) {
        return Collection.class.isAssignableFrom(type);
    }

    private boolean isMapType(Class<?> type) {
        return Map.class.isAssignableFrom(type);
    }

    private Class<?> resolveCollectionElementType(Field field) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType pt)) {
            throw new UnsupportedTypeException("Collection field '" + field.getName() + "' must declare a generic element type");
        }
        Type elementType = pt.getActualTypeArguments()[0];
        Class<?> resolved = resolveClassType(elementType);
        if (resolved == null) {
            throw new UnsupportedTypeException("Collection field '" + field.getName() + "' has unsupported generic element type: " + elementType.getTypeName());
        }
        return resolved;
    }

    private Class<?> resolveMapValueType(Field field) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType pt)) {
            throw new UnsupportedTypeException("Map field '" + field.getName() + "' must declare generic key/value types");
        }
        Type keyType = pt.getActualTypeArguments()[0];
        Class<?> keyClass = resolveClassType(keyType);
        if (keyClass == null || !String.class.isAssignableFrom(keyClass)) {
            throw new UnsupportedTypeException("Map field '" + field.getName() + "' must use String keys");
        }
        Type valueType = pt.getActualTypeArguments()[1];
        Class<?> resolved = resolveClassType(valueType);
        if (resolved == null) {
            throw new UnsupportedTypeException("Map field '" + field.getName() + "' has unsupported generic value type: " + valueType.getTypeName());
        }
        return resolved;
    }

    private Class<?> resolveClassType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> rawClass) {
            return rawClass;
        }
        return null;
    }

    private boolean isPojoType(Class<?> type) {
        if (type.isPrimitive() || isWrapperType(type) || isSupported(type)) {
            return false;
        }
        if (type == String.class || type == BigDecimal.class || type == byte[].class) {
            return false;
        }
        if (Temporal.class.isAssignableFrom(type)) {
            return false;
        }
        if (type.isEnum() || type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return false;
        }
        String pkg = type.getPackageName();
        if (pkg.startsWith("java.") || pkg.startsWith("jakarta.")) {
            return false;
        }
        return true;
    }

    private boolean isWrapperType(Class<?> type) {
        return type == Integer.class || type == Long.class || type == Short.class || type == Byte.class
                || type == Float.class || type == Double.class || type == Boolean.class || type == Character.class;
    }

    private <T> List<T> append(List<T> source, T value) {
        List<T> copy = new ArrayList<>(source);
        copy.add(value);
        return copy;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }
}
