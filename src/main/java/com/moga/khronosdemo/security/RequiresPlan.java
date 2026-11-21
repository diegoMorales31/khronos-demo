package com.moga.khronosdemo.security;

import com.moga.khronosdemo.entities.enums.PlanFeature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller, or one of its endpoints, as part of a feature that only some plans include.
 * PlanFeatureInterceptor answers 403 PLAN_FEATURE_UNAVAILABLE to an owner whose plan does not include it.
 * An annotation on the method wins over the one on its class.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPlan {

    PlanFeature value();
}
