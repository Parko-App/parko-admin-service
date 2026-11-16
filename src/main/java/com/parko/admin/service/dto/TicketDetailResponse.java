package com.parko.admin.service.dto;

import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TicketDetailResponse(
        UUID id,
        String ticketNumber,
        TicketStatus status,
        String qrData,
        LocalDateTime issuedAt,
        LocalDateTime paidAt,
        BigDecimal amount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        SessionInfo session,
        VehicleInfo vehicle,
        OwnerInfo owner,
        PaymentInfo payment
) {

    public record SessionInfo(
            UUID id,
            SessionType type,
            SessionStatus status,
            String plate,
            String plateSnapshot,
            String visitorPlate,
            LocalDateTime entryAt,
            LocalDateTime exitAt
    ) {
    }

    public record VehicleInfo(
            UUID id,
            String plate,
            String brand,
            String model,
            boolean active
    ) {
    }

    public record OwnerInfo(
            UUID id,
            String studentId,
            String fullName,
            String email
    ) {
    }

    public record PaymentInfo(
            UUID transactionId,
            TransactionType type,
            TransactionStatus status,
            BigDecimal amount,
            String paymentProvider,
            String externalRef,
            LocalDateTime createdAt
    ) {
    }
}
