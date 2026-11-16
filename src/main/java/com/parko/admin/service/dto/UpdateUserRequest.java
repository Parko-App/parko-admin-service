package com.parko.admin.service.dto;

import com.parko.domain.lib.model.UserRole;

public record UpdateUserRequest(
        String fullName,
        String email,
        String studentId,
        UserRole role,
        Boolean active,
        Boolean emailVerified
) {
}
