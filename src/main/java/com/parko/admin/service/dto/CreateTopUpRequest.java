package com.parko.admin.service.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Carga manual de saldo hecha por un administrador (se registra como efectivo). */
public record CreateTopUpRequest(UUID userId, BigDecimal amount) {
}
