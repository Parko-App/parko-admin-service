package com.parko.admin.service.service;

import com.parko.admin.service.dto.AccessLogResponse;
import com.parko.admin.service.dto.DashboardSummary;
import com.parko.admin.service.dto.DashboardSummary.ActiveSessions;
import com.parko.admin.service.dto.DashboardSummary.DayStats;
import com.parko.admin.service.dto.DashboardSummary.HourlyOccupancy;
import com.parko.admin.service.dto.DashboardSummary.Occupancy;
import com.parko.admin.service.dto.DashboardSummary.RevenueToday;
import com.parko.admin.service.dto.DashboardSummary.VisitorsToday;
import com.parko.admin.service.dto.RecentAccessResponse;
import com.parko.admin.service.repository.AdminAccessLogRepository;
import com.parko.admin.service.repository.AdminParkingSessionRepository;
import com.parko.admin.service.repository.AdminTicketRepository;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    public static final int DEFAULT_RECENT_LIMIT = 8;
    public static final int MAX_RECENT_LIMIT = 50;

    private final AdminParkingSessionRepository sessionRepository;
    private final AdminAccessLogRepository accessLogRepository;
    private final AdminTransactionRepository transactionRepository;
    private final AdminTicketRepository ticketRepository;
    private final AccessLogQueryService accessLogQueryService;
    private final Clock clock;
    private final int capacity;

    public DashboardService(AdminParkingSessionRepository sessionRepository,
                            AdminAccessLogRepository accessLogRepository,
                            AdminTransactionRepository transactionRepository,
                            AdminTicketRepository ticketRepository,
                            AccessLogQueryService accessLogQueryService,
                            Clock clock,
                            @Value("${parking.capacity}") int capacity) {
        this.sessionRepository = sessionRepository;
        this.accessLogRepository = accessLogRepository;
        this.transactionRepository = transactionRepository;
        this.ticketRepository = ticketRepository;
        this.accessLogQueryService = accessLogQueryService;
        this.clock = clock;
        this.capacity = capacity;
    }

    public DashboardSummary summary() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        long active = sessionRepository.countByStatus(SessionStatus.ACTIVE);
        long activeVisitors = sessionRepository.countByStatusAndSessionType(SessionStatus.ACTIVE, SessionType.VISITOR);
        long activeRegistered = sessionRepository.countByStatusAndSessionType(SessionStatus.ACTIVE, SessionType.REGISTERED);

        BigDecimal revenueToday = transactionRepository.sumCompletedCharges(dayStart, dayEnd);

        VisitorsToday visitorsToday = new VisitorsToday(
                sessionRepository.countEntered(SessionType.VISITOR, dayStart, dayEnd),
                transactionRepository.sumCompletedVisitorCharges(dayStart, dayEnd),
                ticketRepository.countPendingVisitorTickets(dayStart, dayEnd));

        DayStats dayStats = new DayStats(
                accessLogRepository.countByEventTypeAndResultAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        com.parko.domain.lib.model.AccessEventType.ENTRY, AccessResult.AUTHORIZED, dayStart, dayEnd),
                accessLogRepository.countByEventTypeAndResultAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        com.parko.domain.lib.model.AccessEventType.EXIT, AccessResult.AUTHORIZED, dayStart, dayEnd),
                sessionRepository.countDistinctVisitorPlates(dayStart, dayEnd),
                transactionRepository.countByTypeAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        TransactionType.CHARGE, TransactionStatus.COMPLETED, dayStart, dayEnd),
                transactionRepository.countByTypeAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        TransactionType.CHARGE, TransactionStatus.PENDING, dayStart, dayEnd),
                0); // todavía no existe un modelo de incidentes

        return new DashboardSummary(
                new Occupancy(active, capacity),
                new RevenueToday(revenueToday, revenueChangePercent(revenueToday, dayStart, now)),
                new ActiveSessions(active, activeVisitors, activeRegistered),
                visitorsToday,
                dayStats,
                occupancyByHour(dayStart, dayEnd, now));
    }

    public List<RecentAccessResponse> recentAccess(Integer limit) {
        int size = limit == null ? DEFAULT_RECENT_LIMIT : Math.max(1, Math.min(limit, MAX_RECENT_LIMIT));
        return accessLogQueryService.list(null, null, 0, size, null).content().stream()
                .map(this::toRecent)
                .toList();
    }

    private RecentAccessResponse toRecent(AccessLogResponse log) {
        return new RecentAccessResponse(
                log.id().toString(),
                log.plate(),
                log.userName(),
                log.userType(),
                log.occurredAt().atZone(clock.getZone()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                log.eventType());
    }

    /** Variación contra el mismo tramo horario de ayer, para no comparar un día entero con medio día. */
    private double revenueChangePercent(BigDecimal today, LocalDateTime dayStart, LocalDateTime now) {
        BigDecimal yesterday = transactionRepository.sumCompletedCharges(dayStart.minusDays(1), now.minusDays(1));
        if (yesterday == null || yesterday.signum() == 0) {
            return 0;
        }
        return today.subtract(yesterday)
                .multiply(BigDecimal.valueOf(100))
                .divide(yesterday, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Vehículos estacionados al cierre de cada hora de hoy. La hora en curso se mide hasta ahora; las futuras valen 0. */
    private List<HourlyOccupancy> occupancyByHour(LocalDateTime dayStart, LocalDateTime dayEnd, LocalDateTime now) {
        List<ParkingSessionEntity> sessions = sessionRepository.findOverlapping(dayStart, dayEnd);
        List<HourlyOccupancy> hours = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            LocalDateTime hourStart = dayStart.plusHours(hour);
            if (hourStart.isAfter(now)) {
                hours.add(new HourlyOccupancy(hour, 0));
                continue;
            }
            LocalDateTime hourEnd = hourStart.plusHours(1);
            LocalDateTime at = hourEnd.isAfter(now) ? now : hourEnd;
            hours.add(new HourlyOccupancy(hour, sessions.stream().filter(s -> parkedAt(s, at)).count()));
        }
        return hours;
    }

    private boolean parkedAt(ParkingSessionEntity session, LocalDateTime at) {
        if (session.getEntryAt() == null || session.getEntryAt().isAfter(at)) {
            return false;
        }
        if (session.getExitAt() != null) {
            return session.getExitAt().isAfter(at);
        }
        return session.getStatus() == SessionStatus.ACTIVE;
    }
}
