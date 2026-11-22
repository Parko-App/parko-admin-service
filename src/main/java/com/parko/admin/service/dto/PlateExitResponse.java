package com.parko.admin.service.dto;

import com.parko.domain.lib.model.SessionType;

import java.util.UUID;

/** Resultado de generar un egreso desde el panel. */
public record PlateExitResponse(
        UUID ticketId,
        String ticketNumber,
        SessionType sessionType,
        String plate,
        String message
) {
}
