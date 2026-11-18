package com.moga.khronosdemo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

public class CrossFieldValidator implements ConstraintValidator<CrossFieldValid, CrossFieldChecked> {

    @Override
    public boolean isValid(CrossFieldChecked value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Map<String, String> errors = value.crossFieldErrors();
        if (errors.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        errors.forEach((field, message) -> context.buildConstraintViolationWithTemplate(literal(message))
                .addPropertyNode(field)
                .addConstraintViolation());
        return false;
    }

    // The message is shown as it is: braces and dollar signs are escaped so they are never interpolated
    private static String literal(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
