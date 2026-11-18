package com.moga.khronosdemo.validation;

import jakarta.validation.groups.Default;

/**
 * Validation group for constraints that only apply when something is created, such as the
 * establishment it belongs to, which an update ignores. It extends Default, so validating with it
 * also checks every other constraint. Use it as {@code @Validated(OnCreate.class)} on the request body.
 */
public interface OnCreate extends Default {
}
