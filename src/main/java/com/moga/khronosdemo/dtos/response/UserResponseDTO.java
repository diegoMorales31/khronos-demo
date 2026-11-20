package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.Plan;
import com.moga.khronosdemo.entities.enums.Role;

public record UserResponseDTO(
        Long id,
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        Role role,
        // Only owners have one; null for everyone else
        Plan plan
) {
}
