package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.Role;

public record UserResponseDTO(
        Long id,
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        Role role
) {
}
