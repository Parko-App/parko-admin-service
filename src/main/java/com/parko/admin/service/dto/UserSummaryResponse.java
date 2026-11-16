package com.parko.admin.service.dto;

import com.parko.domain.lib.model.UserRole;

import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String studentId,
        String fullName,
        String email,
        UserRole role,
        boolean active
) {
}
