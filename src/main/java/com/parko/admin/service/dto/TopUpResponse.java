package com.parko.admin.service.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Carga de saldo para la pantalla "Carga de saldos" del panel.
 * status: CREDITED | PENDING | REJECTED. method: MERCADO_PAGO | CASH.
 * timestamp va en ISO 8601 con offset (hora de Argentina).
 */
public record TopUpResponse(
        UUID id,
        String transactionNumber,
        String userName,
        String userEmail,
        String timestamp,
        BigDecimal amount,
        String method,
        String status
) {
}
