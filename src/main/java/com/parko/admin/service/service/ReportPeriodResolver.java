package com.parko.admin.service.service;

import com.parko.admin.service.dto.ReportPeriod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class ReportPeriodResolver {

    private final Clock clock;
    private final int defaultDays;
    private final int maxDays;

    public ReportPeriodResolver(Clock clock,
                                @Value("${reports.default-days}") int defaultDays,
                                @Value("${reports.max-days}") int maxDays) {
        this.clock = clock;
        this.defaultDays = defaultDays;
        this.maxDays = maxDays;
    }

    public ReportPeriod resolve(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now(clock);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(defaultDays - 1L);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("La fecha 'from' no puede ser posterior a 'to'");
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1 > maxDays) {
            throw new IllegalArgumentException("El rango máximo es de " + maxDays + " días");
        }
        return new ReportPeriod(resolvedFrom, resolvedTo);
    }
}
