package com.parko.admin.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueWeek(LocalDate weekStart, BigDecimal total) {
}
