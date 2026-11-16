package com.parko.admin.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReportPeriod(LocalDate from, LocalDate to) {

    public LocalDateTime start() {
        return from.atStartOfDay();
    }

    public LocalDateTime endExclusive() {
        return to.plusDays(1).atStartOfDay();
    }
}
