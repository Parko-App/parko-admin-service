package com.parko.admin.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RevenueReport(
        LocalDate from,
        LocalDate to,
        List<RevenueDay> days,
        List<RevenueWeek> weeks,
        List<RevenueMonthBest> bestDayByMonth,
        BigDecimal topUps,
        BigDecimal cash,
        BigDecimal total,
        BigDecimal dailyAverage
) {
}
