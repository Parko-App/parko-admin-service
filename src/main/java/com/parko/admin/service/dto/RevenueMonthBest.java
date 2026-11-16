package com.parko.admin.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record RevenueMonthBest(YearMonth month, LocalDate date, BigDecimal total) {
}
