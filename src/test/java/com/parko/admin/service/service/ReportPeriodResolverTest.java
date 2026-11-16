package com.parko.admin.service.service;

import com.parko.admin.service.dto.ReportPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportPeriodResolverTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private ReportPeriodResolver resolver;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZONE);
        resolver = new ReportPeriodResolver(clock, 30, 366);
    }

    @Test
    void resolve_withoutDates_usesLastThirtyDaysUntilToday() {
        ReportPeriod period = resolver.resolve(null, null);

        assertEquals(LocalDate.of(2026, 11, 20), period.to());
        assertEquals(LocalDate.of(2026, 10, 22), period.from());
    }

    @Test
    void resolve_onlyFrom_usesTodayAsEnd() {
        ReportPeriod period = resolver.resolve(LocalDate.of(2026, 11, 1), null);

        assertEquals(LocalDate.of(2026, 11, 1), period.from());
        assertEquals(LocalDate.of(2026, 11, 20), period.to());
    }

    @Test
    void resolve_onlyTo_usesDefaultWindowBeforeIt() {
        ReportPeriod period = resolver.resolve(null, LocalDate.of(2026, 6, 30));

        assertEquals(LocalDate.of(2026, 6, 1), period.from());
        assertEquals(LocalDate.of(2026, 6, 30), period.to());
    }

    @Test
    void resolve_sameDay_isValid() {
        LocalDate day = LocalDate.of(2026, 11, 10);

        ReportPeriod period = resolver.resolve(day, day);

        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), period.start());
        assertEquals(LocalDateTime.of(2026, 11, 11, 0, 0), period.endExclusive());
    }

    @Test
    void resolve_fromAfterTo_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9)));
    }

    @Test
    void resolve_exactlyMaxDays_isValid() {
        ReportPeriod period = resolver.resolve(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

        assertEquals(LocalDate.of(2027, 1, 1), period.to());
    }

    @Test
    void resolve_moreThanMaxDays_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)));
    }
}
