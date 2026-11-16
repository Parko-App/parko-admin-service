package com.parko.admin.service.dto;

import com.parko.domain.lib.model.InstitutionalDomain;
import com.parko.domain.lib.model.UserRole;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserDetailResponse(
        UUID id,
        String studentId,
        String fullName,
        String email,
        InstitutionalDomain institutionalDomain,
        UserRole role,
        boolean active,
        boolean emailVerified,
        String firebaseUid,
        BigDecimal balance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<VehicleResponse> vehicles
) {
}
