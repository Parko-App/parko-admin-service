package com.parko.admin.service.dto;

import java.util.UUID;

public record UpdateVehicleRequest(
        UUID userId,
        String plate,
        String brand,
        String model,
        Boolean active
) {
}
