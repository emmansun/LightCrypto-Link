package io.github.emmansun.lightcrypto.listener;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.exception.UnsupportedTypeException;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.emmansun.lightcrypto.service.TypeSerializer.isSupported;

/**
 * Entity metadata cache — scans @Encrypted annotated fields and caches the results.
 */
public class EntityMetadataCache {

    private final Map<Class<?>, List<EncryptedFieldMetadata>> cache = new ConcurrentHashMap<>();

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

    private List<EncryptedFieldMetadata> scanFields(Class<?> entityClass) {
        List<EncryptedFieldMetadata> result = new ArrayList<>();
        for (Field field : getAllFields(entityClass)) {
            Encrypted encrypted = field.getAnnotation(Encrypted.class);
            if (encrypted == null) continue;

            Class<?> fieldType = field.getType();
            if (!isSupported(fieldType)) {
                throw new UnsupportedTypeException(
                        "Field '" + field.getName() + "' in class '" + entityClass.getName() +
                                "' has unsupported type: " + fieldType.getName());
            }

            field.setAccessible(true);

            String effectiveFieldName = encrypted.fieldName().isEmpty()
                    ? field.getName()
                    : encrypted.fieldName();

            result.add(new EncryptedFieldMetadata(
                    field,
                    field.getName(),
                    fieldType,
                    encrypted.algorithm(),
                    encrypted.blindIndex(),
                    effectiveFieldName
            ));
        }
        return Collections.unmodifiableList(result);
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
