package com.parko.admin.service.dto;

import com.parko.domain.lib.model.UserRole;

public record CreateUserRequest(
        String fullName,
        String email,
        String studentId,
        String password,
        UserRole role,
        Boolean emailVerified
) {
}
