package com.moga.khronosdemo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A region-based IANA time zone id, such as Europe/Madrid. Fixed offsets like +01:00 are rejected,
 * because they do not follow daylight saving time. A null value is valid; use @NotNull to require one.
 */
@Documented
@Constraint(validatedBy = TimeZoneIdValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
        ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeZoneId {

    String message() default "{khronos.validation.TimeZoneId.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
