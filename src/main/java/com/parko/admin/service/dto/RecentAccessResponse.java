package com.parko.admin.service.dto;

import com.parko.domain.lib.model.AccessEventType;

/** Último movimiento para la tabla del dashboard. timestamp va en ISO 8601 con offset (hora de Argentina). */
public record RecentAccessResponse(
        String id,
        String plate,
        String userName,
        String userType,
        String timestamp,
        AccessEventType movement
) {
}
