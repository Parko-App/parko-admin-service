package com.parko.admin.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateVehicleRequest(
        @NotNull UUID userId,
        @NotBlank String plate,
        @NotBlank String brand,
        @NotBlank String model
) {
}
