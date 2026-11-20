package com.moga.khronosdemo.entities.enums;

import java.util.EnumSet;
import java.util.Set;

// What an owner pays for. Each plan includes everything in the one before it
public enum Plan {
    // Khronos: shifts, hourly rates, zones and events, for up to 50 active workers
    BASIC(50, EnumSet.noneOf(PlanFeature.class)),
    // Khronos Plus: also the cash, for up to 100 active workers
    PLUS(100, EnumSet.of(PlanFeature.CASH)),
    // Khronos Ultra: also the inventory, with no limit on workers
    ULTRA(null, EnumSet.of(PlanFeature.CASH, PlanFeature.INVENTORY));

    private final Integer maxActiveWorkers;
    private final Set<PlanFeature> features;

    Plan(Integer maxActiveWorkers, Set<PlanFeature> features) {
        this.maxActiveWorkers = maxActiveWorkers;
        this.features = features;
    }

    // Active workers allowed across all of the owner's establishments; null when there is no limit
    public Integer getMaxActiveWorkers() {
        return maxActiveWorkers;
    }

    public Set<PlanFeature> getFeatures() {
        return Set.copyOf(features);
    }

    public boolean includes(PlanFeature feature) {
        return features.contains(feature);
    }

    public boolean allowsActiveWorkers(long count) {
        return maxActiveWorkers == null || count <= maxActiveWorkers;
    }
}
