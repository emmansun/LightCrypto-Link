package org.springframework.data.mongodb.core.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test stub for MongoDB's @DBRef annotation.
 * Allows EntityMetadataCache reflection-based exclusion tests to run
 * without spring-data-mongodb on the classpath.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface DBRef {
    boolean lazy() default false;
}
