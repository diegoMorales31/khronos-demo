package com.moga.khronosdemo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collection;

public class MaxBatchSizeValidator implements ConstraintValidator<MaxBatchSize, Collection<?>> {

    private final int maxSize;

    // Built by Spring's validator factory, which injects the configured limit
    public MaxBatchSizeValidator(@Value("${app.batch.max-size}") int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
        if (value == null || value.size() <= maxSize) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.unwrap(HibernateConstraintValidatorContext.class)
                .addMessageParameter("max", maxSize)
                .buildConstraintViolationWithTemplate("{khronos.validation.MaxBatchSize.message}")
                .addConstraintViolation();
        return false;
    }
}
