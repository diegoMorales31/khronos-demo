package com.moga.khronosdemo.exceptions;

import com.moga.khronosdemo.entities.enums.Plan;
import com.moga.khronosdemo.entities.enums.PlanFeature;

/**
 * An owner called an endpoint of a feature their plan does not include: 403 with the code
 * PLAN_FEATURE_UNAVAILABLE, so clients can tell it from a request for someone else's data.
 */
public class PlanFeatureUnavailableException extends RuntimeException {

    private final Plan plan;
    private final PlanFeature feature;

    public PlanFeatureUnavailableException(Plan plan, PlanFeature feature) {
        super("The " + plan + " plan does not include " + feature.name().toLowerCase()
                + "; ask the platform admin to move you to a plan that does");
        this.plan = plan;
        this.feature = feature;
    }

    public Plan getPlan() {
        return plan;
    }

    public PlanFeature getFeature() {
        return feature;
    }
}
