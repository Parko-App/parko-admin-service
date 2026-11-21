package com.parko.admin.service.dto;

import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccessLogResponse(
        UUID id,
        AccessEventType eventType,
        AccessMethod accessMethod,
        AccessResult result,
        LocalDateTime occurredAt,
        UUID parkingSessionId,
        SessionType sessionType,
        String plate
) {
}
