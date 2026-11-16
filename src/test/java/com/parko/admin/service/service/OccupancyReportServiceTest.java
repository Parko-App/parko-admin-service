package com.parko.admin.service.service;

import com.parko.admin.service.dto.OccupancyDay;
import com.parko.admin.service.dto.OccupancyReport;
import com.parko.admin.service.repository.AdminParkingSessionRepository;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OccupancyReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Mock
    private AdminParkingSessionRepository sessionRepository;

    private OccupancyReportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZONE);
        service = new OccupancyReportService(sessionRepository, new ReportPeriodResolver(clock, 30, 366), clock);
    }

    private ParkingSessionEntity session(LocalDateTime entry, LocalDateTime exit, SessionStatus status) {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setEntryAt(entry);
        session.setExitAt(exit);
        session.setStatus(status);
        return session;
    }

    private LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute);
    }

    private void sessions(ParkingSessionEntity... sessions) {
        when(sessionRepository.findOverlapping(any(), any())).thenReturn(List.of(sessions));
    }

    @Test
    void report_noSessions_returnsZeroedDays() {
        sessions();

        OccupancyReport report = service.report(DAY, DAY.plusDays(2));

        assertEquals(3, report.days().size());
        assertEquals(0, report.peak());
        assertEquals(0.0, report.average());
        assertEquals(DAY, report.from());
        assertEquals(DAY.plusDays(2), report.to());
    }

    @Test
    void report_singleSession_peakOneAndTimeWeightedAverage() {
        sessions(session(at(10, 10, 0), at(10, 12, 0), SessionStatus.COMPLETED));

        OccupancyDay day = service.report(DAY, DAY).days().get(0);

        assertEquals(1, day.peak());
        assertEquals(0.08, day.average());
    }

    @Test
    void report_overlappingSessions_peakIsMaxConcurrent() {
        sessions(
                session(at(10, 10, 0), at(10, 12, 0), SessionStatus.COMPLETED),
                session(at(10, 11, 0), at(10, 13, 0), SessionStatus.COMPLETED));

        OccupancyDay day = service.report(DAY, DAY).days().get(0);

        assertEquals(2, day.peak());
        assertEquals(0.17, day.average());
    }

    @Test
    void report_exitAndEntryAtSameInstant_doNotCountAsConcurrent() {
        sessions(
                session(at(10, 10, 0), at(10, 12, 0), SessionStatus.COMPLETED),
                session(at(10, 12, 0), at(10, 14, 0), SessionStatus.COMPLETED));

        OccupancyDay day = service.report(DAY, DAY).days().get(0);

        assertEquals(1, day.peak());
    }

    @Test
    void report_sessionCrossingMidnight_countsInBothDays() {
        sessions(session(at(10, 22, 0), at(11, 2, 0), SessionStatus.COMPLETED));

        OccupancyReport report = service.report(DAY, DAY.plusDays(1));

        assertEquals(1, report.days().get(0).peak());
        assertEquals(0.08, report.days().get(0).average());
        assertEquals(1, report.days().get(1).peak());
        assertEquals(0.08, report.days().get(1).average());
        assertEquals(1, report.peak());
        assertEquals(0.08, report.average());
    }

    @Test
    void report_sessionCoveringWholeDay_hasFullAverage() {
        sessions(session(at(9, 8, 0), at(11, 8, 0), SessionStatus.COMPLETED));

        OccupancyDay day = service.report(DAY, DAY).days().get(0);

        assertEquals(1, day.peak());
        assertEquals(1.0, day.average());
    }

    @Test
    void report_activeSessionWithoutExit_countsUntilNow() {
        sessions(session(at(20, 10, 0), null, SessionStatus.ACTIVE));
        LocalDate today = LocalDate.of(2026, 11, 20);

        OccupancyReport report = service.report(today, today.plusDays(1));

        assertEquals(1, report.days().get(0).peak());
        assertEquals(0.17, report.days().get(0).average());
        assertEquals(0, report.days().get(1).peak());
        assertEquals(0.0, report.days().get(1).average());
    }

    @Test
    void report_completedSessionWithoutExit_isIgnored() {
        sessions(session(at(10, 10, 0), null, SessionStatus.COMPLETED));

        OccupancyReport report = service.report(DAY, DAY);

        assertEquals(0, report.peak());
    }

    @Test
    void report_overallAverageIsMeanOfDailyAverages() {
        sessions(session(at(10, 0, 0), at(10, 12, 0), SessionStatus.COMPLETED));

        OccupancyReport report = service.report(DAY, DAY.plusDays(1));

        assertEquals(0.5, report.days().get(0).average());
        assertEquals(0.0, report.days().get(1).average());
        assertEquals(0.25, report.average());
    }

    @Test
    void report_invalidRange_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.report(DAY, DAY.minusDays(1)));
    }
}
