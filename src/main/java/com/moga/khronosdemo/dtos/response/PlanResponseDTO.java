package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.Plan;
import com.moga.khronosdemo.entities.enums.PlanFeature;

import java.util.List;

// An owner's plan and how much of it is in use
public record PlanResponseDTO(
        Plan plan,
        // Active workers allowed across all the owner's establishments; null when there is no limit
        Integer maxActiveWorkers,
        // Active workers the owner has now, across all their establishments
        long activeWorkers,
        // What the plan includes on top of what every plan has, in a fixed order
        List<PlanFeature> features
) {
}
