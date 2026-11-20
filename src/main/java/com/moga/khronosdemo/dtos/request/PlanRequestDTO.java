package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.entities.enums.Plan;
import jakarta.validation.constraints.NotNull;

public record PlanRequestDTO(
        @NotNull
        Plan plan
) {
}
