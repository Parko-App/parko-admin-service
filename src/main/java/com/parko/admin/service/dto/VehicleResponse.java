package com.parko.admin.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID userId,
        String ownerName,
        String ownerEmail,
        String plate,
        String brand,
        String model,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
