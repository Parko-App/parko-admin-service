package com.parko.admin.service.dto;

import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TicketSummaryResponse(
        UUID id,
        String ticketNumber,
        TicketStatus status,
        LocalDateTime issuedAt,
        LocalDateTime paidAt,
        SessionType sessionType,
        String plate,
        BigDecimal amount
) {
}
