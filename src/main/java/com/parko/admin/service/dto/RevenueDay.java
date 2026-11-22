package com.parko.admin.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Recaudación de un día: cargas de saldo (efectivo o Mercado Pago) y cobros de tickets en efectivo. */
public record RevenueDay(LocalDate date, BigDecimal topUps, BigDecimal cash, BigDecimal total) {
}
