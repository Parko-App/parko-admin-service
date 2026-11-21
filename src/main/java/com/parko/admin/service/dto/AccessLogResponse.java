package com.parko.admin.service.dto;

import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fila del listado de accesos. Además de los datos crudos del evento, incluye los campos
 * que consume el panel web: movement, timestamp, method (AUTOMATIC/MANUAL), userName y userType.
 */
public record AccessLogResponse(
        UUID id,
        AccessEventType eventType,
        AccessMethod accessMethod,
        AccessResult result,
        LocalDateTime occurredAt,
        UUID parkingSessionId,
        SessionType sessionType,
        String plate,
        AccessEventType movement,
        LocalDateTime timestamp,
        String method,
        String userName,
        String userType
) {
}
