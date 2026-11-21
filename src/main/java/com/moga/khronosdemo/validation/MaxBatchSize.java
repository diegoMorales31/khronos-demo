package com.moga.khronosdemo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits a list sent in one request to app.batch.max-size items. The list is checked before the
 * @PreAuthorize rules run, so an oversized request is turned away before any per-item work.
 */
@Documented
@Constraint(validatedBy = MaxBatchSizeValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBatchSize {

    String message() default "{khronos.validation.MaxBatchSize.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
