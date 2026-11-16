package com.parko.admin.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueDay(LocalDate date, BigDecimal registered, BigDecimal visitors, BigDecimal total) {
}
