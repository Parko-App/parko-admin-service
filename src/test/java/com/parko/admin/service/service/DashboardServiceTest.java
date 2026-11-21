package com.parko.admin.service.service;

import com.parko.admin.service.dto.AccessLogResponse;
import com.parko.admin.service.dto.DashboardSummary;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.RecentAccessResponse;
import com.parko.admin.service.repository.AdminAccessLogRepository;
import com.parko.admin.service.repository.AdminParkingSessionRepository;
import com.parko.admin.service.repository.AdminTicketRepository;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    /** Hoy a las 10:30 (hora de Argentina). */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 30);
    private static final LocalDateTime DAY_START = LocalDateTime.of(2026, 11, 21, 0, 0);
    private static final LocalDateTime DAY_END = LocalDateTime.of(2026, 11, 22, 0, 0);

    @Mock
    private AdminParkingSessionRepository sessionRepository;
    @Mock
    private AdminAccessLogRepository accessLogRepository;
    @Mock
    private AdminTransactionRepository transactionRepository;
    @Mock
    private AdminTicketRepository ticketRepository;
    @Mock
    private AccessLogQueryService accessLogQueryService;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new DashboardService(sessionRepository, accessLogRepository, transactionRepository,
                ticketRepository, accessLogQueryService, clock, 300);
    }

    private static ParkingSessionEntity session(LocalDateTime entry, LocalDateTime exit, SessionStatus status) {
        ParkingSessionEntity s = new ParkingSessionEntity();
        s.setEntryAt(entry);
        s.setExitAt(exit);
        s.setStatus(status);
        return s;
    }

    @Test
    void summary_aggregatesTodayFigures() {
        when(sessionRepository.countByStatus(SessionStatus.ACTIVE)).thenReturn(12L);
        when(sessionRepository.countByStatusAndSessionType(SessionStatus.ACTIVE, SessionType.VISITOR)).thenReturn(2L);
        when(sessionRepository.countByStatusAndSessionType(SessionStatus.ACTIVE, SessionType.REGISTERED)).thenReturn(10L);
        when(transactionRepository.sumCompletedCharges(DAY_START, DAY_END)).thenReturn(new BigDecimal("150"));
        when(transactionRepository.sumCompletedCharges(DAY_START.minusDays(1), NOW.minusDays(1)))
                .thenReturn(new BigDecimal("100"));
        when(sessionRepository.countEntered(SessionType.VISITOR, DAY_START, DAY_END)).thenReturn(5L);
        when(transactionRepository.sumCompletedVisitorCharges(DAY_START, DAY_END)).thenReturn(new BigDecimal("40"));
        when(ticketRepository.countPendingVisitorTickets(DAY_START, DAY_END)).thenReturn(3L);
        when(accessLogRepository.countByEventTypeAndResultAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                AccessEventType.ENTRY, AccessResult.AUTHORIZED, DAY_START, DAY_END)).thenReturn(20L);
        when(accessLogRepository.countByEventTypeAndResultAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                AccessEventType.EXIT, AccessResult.AUTHORIZED, DAY_START, DAY_END)).thenReturn(8L);
        when(sessionRepository.countDistinctVisitorPlates(DAY_START, DAY_END)).thenReturn(4L);
        when(transactionRepository.countByTypeAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                TransactionType.CHARGE, TransactionStatus.COMPLETED, DAY_START, DAY_END)).thenReturn(9L);
        when(transactionRepository.countByTypeAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                TransactionType.CHARGE, TransactionStatus.PENDING, DAY_START, DAY_END)).thenReturn(2L);
        when(sessionRepository.findOverlapping(DAY_START, DAY_END)).thenReturn(List.of());

        DashboardSummary summary = service.summary();

        assertEquals(12, summary.occupancy().current());
        assertEquals(300, summary.occupancy().capacity());
        assertEquals(new BigDecimal("150"), summary.revenueToday().amount());
        assertEquals(50.0, summary.revenueToday().changePercent());
        assertEquals(12, summary.activeSessions().total());
        assertEquals(2, summary.activeSessions().visitors());
        assertEquals(10, summary.activeSessions().registered());
        assertEquals(5, summary.visitorsToday().count());
        assertEquals(new BigDecimal("40"), summary.visitorsToday().collected());
        assertEquals(3, summary.visitorsToday().pendingPayments());
        assertEquals(20, summary.dayStats().entries());
        assertEquals(8, summary.dayStats().exits());
        assertEquals(4, summary.dayStats().uniqueVisitors());
        assertEquals(9, summary.dayStats().paymentsProcessed());
        assertEquals(2, summary.dayStats().paymentsPending());
        assertEquals(0, summary.dayStats().openIncidents());
        assertEquals(24, summary.occupancyByHour().size());
    }

    @Test
    void summary_revenueChange_isZeroWhenYesterdayHadNone() {
        when(transactionRepository.sumCompletedCharges(DAY_START, DAY_END)).thenReturn(new BigDecimal("150"));
        when(transactionRepository.sumCompletedCharges(DAY_START.minusDays(1), NOW.minusDays(1)))
                .thenReturn(BigDecimal.ZERO);

        assertEquals(0.0, service.summary().revenueToday().changePercent());
    }

    @Test
    void summary_revenueChange_canBeNegative() {
        when(transactionRepository.sumCompletedCharges(DAY_START, DAY_END)).thenReturn(new BigDecimal("50"));
        when(transactionRepository.sumCompletedCharges(DAY_START.minusDays(1), NOW.minusDays(1)))
                .thenReturn(new BigDecimal("200"));

        assertEquals(-75.0, service.summary().revenueToday().changePercent());
    }

    @Test
    void summary_occupancyByHour_countsParkedVehiclesAtCloseOfEachHour() {
        // entra 08:15 y sale 09:20 -> estacionado al cierre de la hora 8, ya no a las 9
        ParkingSessionEntity shortStay = session(LocalDateTime.of(2026, 11, 21, 8, 15),
                LocalDateTime.of(2026, 11, 21, 9, 20), SessionStatus.COMPLETED);
        // entró ayer y sigue activa: está en todas las horas transcurridas
        ParkingSessionEntity overnight = session(LocalDateTime.of(2026, 11, 20, 22, 0), null, SessionStatus.ACTIVE);
        when(sessionRepository.findOverlapping(DAY_START, DAY_END)).thenReturn(List.of(shortStay, overnight));
        when(transactionRepository.sumCompletedCharges(any(), any())).thenReturn(BigDecimal.ZERO);

        List<DashboardSummary.HourlyOccupancy> hours = service.summary().occupancyByHour();

        assertEquals(1, hours.get(7).vehicles());   // solo overnight
        assertEquals(2, hours.get(8).vehicles());   // overnight + shortStay
        assertEquals(1, hours.get(9).vehicles());   // shortStay ya salió
        assertEquals(1, hours.get(10).vehicles());  // hora en curso, medida hasta 10:30
        assertEquals(0, hours.get(11).vehicles());  // hora futura
        assertEquals(0, hours.get(23).vehicles());
    }

    @Test
    void recentAccess_mapsLogsWithOffsetTimestamp() {
        UUID id = UUID.randomUUID();
        AccessLogResponse log = new AccessLogResponse(id, AccessEventType.EXIT, AccessMethod.PLATE,
                AccessResult.AUTHORIZED, LocalDateTime.of(2026, 11, 21, 10, 47), UUID.randomUUID(),
                SessionType.REGISTERED, "AB123CD", AccessEventType.EXIT, LocalDateTime.of(2026, 11, 21, 10, 47),
                "AUTOMATIC", "Lucas Fernández", "REGISTERED");
        when(accessLogQueryService.list(null, null, 0, 8, null))
                .thenReturn(new PageResponse<>(List.of(log), 0, 8, 1, 1));

        RecentAccessResponse row = service.recentAccess(null).getFirst();

        assertEquals(id.toString(), row.id());
        assertEquals("AB123CD", row.plate());
        assertEquals("Lucas Fernández", row.userName());
        assertEquals("REGISTERED", row.userType());
        assertEquals("2026-11-21T10:47:00-03:00", row.timestamp());
        assertEquals(AccessEventType.EXIT, row.movement());
    }

    @Test
    void recentAccess_limitIsClamped() {
        when(accessLogQueryService.list(any(), any(), eq(0), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));

        service.recentAccess(9999);
        service.recentAccess(0);

        verify(accessLogQueryService).list(null, null, 0, DashboardService.MAX_RECENT_LIMIT, null);
        verify(accessLogQueryService).list(null, null, 0, 1, null);
    }
}
