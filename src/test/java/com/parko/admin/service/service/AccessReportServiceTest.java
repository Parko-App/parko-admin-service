package com.parko.admin.service.service;

import com.parko.admin.service.dto.AccessDay;
import com.parko.admin.service.dto.AccessReport;
import com.parko.admin.service.repository.AccessRow;
import com.parko.admin.service.repository.AdminAccessLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessReportServiceTest {

    @Mock
    private AdminAccessLogRepository accessLogRepository;

    private AccessReportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new AccessReportService(accessLogRepository, new ReportPeriodResolver(clock, 30, 366));
    }

    private static AccessRow row(String day, String eventType, String sessionType, long total) {
        return new AccessRow() {
            @Override
            public String getDay() {
                return day;
            }

            @Override
            public String getEventType() {
                return eventType;
            }

            @Override
            public String getSessionType() {
                return sessionType;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    @Test
    void report_countsEntriesAndExitsPerUserTypeAndDay() {
        when(accessLogRepository.countByDay(any(), any())).thenReturn(List.of(
                row("2026-11-10", "ENTRY", "REGISTERED", 5),
                row("2026-11-10", "EXIT", "REGISTERED", 4),
                row("2026-11-10", "ENTRY", "VISITOR", 3),
                row("2026-11-10", "EXIT", "VISITOR", 2),
                row("2026-11-12", "ENTRY", "REGISTERED", 7)));

        AccessReport report = service.report(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));

        AccessDay first = report.days().get(0);
        assertEquals(LocalDate.of(2026, 11, 10), first.date());
        assertEquals(5, first.registeredEntries());
        assertEquals(4, first.registeredExits());
        assertEquals(3, first.visitorEntries());
        assertEquals(2, first.visitorExits());
    }

    @Test
    void report_fillsDaysWithoutActivityWithZeros() {
        when(accessLogRepository.countByDay(any(), any())).thenReturn(List.of(
                row("2026-11-12", "ENTRY", "REGISTERED", 7)));

        AccessReport report = service.report(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));

        AccessDay empty = report.days().get(1);
        assertEquals(LocalDate.of(2026, 11, 11), empty.date());
        assertEquals(0, empty.registeredEntries());
        assertEquals(0, empty.registeredExits());
        assertEquals(0, empty.visitorEntries());
        assertEquals(0, empty.visitorExits());
    }

    @Test
    void report_totalsAddUpAllDays() {
        when(accessLogRepository.countByDay(any(), any())).thenReturn(List.of(
                row("2026-11-10", "ENTRY", "REGISTERED", 5),
                row("2026-11-10", "EXIT", "VISITOR", 2),
                row("2026-11-12", "ENTRY", "REGISTERED", 7),
                row("2026-11-12", "EXIT", "REGISTERED", 1),
                row("2026-11-12", "ENTRY", "VISITOR", 4)));

        AccessReport report = service.report(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));

        assertEquals(12, report.registeredEntries());
        assertEquals(1, report.registeredExits());
        assertEquals(4, report.visitorEntries());
        assertEquals(2, report.visitorExits());
        assertEquals(LocalDate.of(2026, 11, 10), report.from());
        assertEquals(LocalDate.of(2026, 11, 12), report.to());
    }

    @Test
    void report_mergesRowsOfTheSameBucket() {
        when(accessLogRepository.countByDay(any(), any())).thenReturn(List.of(
                row("2026-11-10", "ENTRY", "REGISTERED", 2),
                row("2026-11-10", "ENTRY", "REGISTERED", 3)));

        AccessReport report = service.report(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));

        assertEquals(5, report.days().get(0).registeredEntries());
    }

    @Test
    void report_invalidRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.report(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 10)));
    }
}
