package com.parko.admin.service.service;

import com.parko.admin.service.dto.VehicleResponse;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;

import java.util.UUID;

public final class VehicleMapper {

    private static final String RELEASED_PLATE_SEPARATOR = "#";

    private VehicleMapper() {
    }

    public static VehicleResponse toResponse(VehicleEntity vehicle) {
        UserEntity owner = vehicle.getUser();
        return new VehicleResponse(
                vehicle.getId(),
                owner.getId(),
                owner.getFullName(),
                owner.getEmail(),
                visiblePlate(vehicle.getPlate()),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.isActive(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }

    public static String visiblePlate(String plate) {
        int separator = plate == null ? -1 : plate.indexOf(RELEASED_PLATE_SEPARATOR);
        return separator < 0 ? plate : plate.substring(0, separator);
    }

    public static String releasedPlate(String plate, UUID vehicleId) {
        return plate + RELEASED_PLATE_SEPARATOR + vehicleId;
    }
}
