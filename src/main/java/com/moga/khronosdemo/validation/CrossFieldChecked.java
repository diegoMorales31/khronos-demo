package com.moga.khronosdemo.validation;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A request with rules that span several of its fields, such as a quantity whose sign depends on a type.
 * Annotated with {@link CrossFieldValid}, its broken rules answer 400 with fieldErrors under the field
 * each one names, like any other validation error, and before the @PreAuthorize rules run.
 */
public interface CrossFieldChecked {

    /**
     * The field each broken rule is reported under, and its message; empty when every rule holds. A field
     * that is null or malformed is left to its own constraints, so it is not reported twice.
     */
    Map<String, String> crossFieldErrors();

    // Whether a value appears more than once, leaving out nulls, which their own @NotNull reports
    static boolean hasRepeats(Stream<?> values) {
        Set<Object> seen = new HashSet<>();
        return values.filter(Objects::nonNull).anyMatch(value -> !seen.add(value));
    }
}
