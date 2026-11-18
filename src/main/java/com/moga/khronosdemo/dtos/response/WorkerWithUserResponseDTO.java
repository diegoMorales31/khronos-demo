package com.moga.khronosdemo.dtos.response;

public record WorkerWithUserResponseDTO(
        UserResponseDTO user,
        WorkerResponseDTO worker
) {
}
