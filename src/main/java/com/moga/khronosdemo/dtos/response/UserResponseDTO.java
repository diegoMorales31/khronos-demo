package com.moga.khronosdemo.dtos.response;

public record UserResponseDTO(
        Long id,
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        Boolean isAdmin
) {
}
