package com.moga.khronosdemo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Checks the rules a {@link CrossFieldChecked} request states across its fields, reporting each broken
 * rule under the field it names.
 */
@Documented
@Constraint(validatedBy = CrossFieldValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossFieldValid {

    String message() default "is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
