package com.moga.khronosdemo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits the UTF-8 length of a string in bytes, not characters. Passwords use it because BCrypt
 * only accepts 72 bytes, and a password with accents or emoji reaches that before 72 characters.
 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
        ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

    int value();

    String message() default "must be at most {value} bytes long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
