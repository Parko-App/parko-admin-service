package com.parko.admin.service.dto;

import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;

import java.util.UUID;

/**
 * Resultado de generar un ingreso desde el panel. sessionType dice si se trató como usuario registrado o como
 * visitante. El PDF del ticket lo genera el propio panel (el mismo de "Generar PDF" en la lista de tickets), no
 * el que devuelve access-service.
 */
public record PlateEntryResponse(
        UUID ticketId,
        String ticketNumber,
        TicketStatus ticketStatus,
        SessionType sessionType,
        String plate,
        String message
) {
}
