package com.parko.admin.service.service;

import com.parko.admin.service.dto.OccupancyDay;
import com.parko.admin.service.dto.OccupancyReport;
import com.parko.admin.service.dto.ReportPeriod;
import com.parko.admin.service.repository.AdminParkingSessionRepository;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OccupancyReportService {

    private final AdminParkingSessionRepository sessionRepository;
    private final ReportPeriodResolver periodResolver;
    private final Clock clock;

    public OccupancyReportService(AdminParkingSessionRepository sessionRepository,
                                  ReportPeriodResolver periodResolver,
                                  Clock clock) {
        this.sessionRepository = sessionRepository;
        this.periodResolver = periodResolver;
        this.clock = clock;
    }

    public OccupancyReport report(LocalDate from, LocalDate to) {
        ReportPeriod period = periodResolver.resolve(from, to);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Interval> intervals = sessionRepository.findOverlapping(period.start(), period.endExclusive()).stream()
                .map(session -> toInterval(session, now))
                .filter(interval -> interval.end().isAfter(interval.start()))
                .toList();

        List<OccupancyDay> days = new ArrayList<>();
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            days.add(day(date, intervals, now));
        }

        int peak = days.stream().mapToInt(OccupancyDay::peak).max().orElse(0);
        double average = days.stream().mapToDouble(OccupancyDay::average).average().orElse(0);
        return new OccupancyReport(period.from(), period.to(), days, peak, round(average));
    }

    private Interval toInterval(ParkingSessionEntity session, LocalDateTime now) {
        LocalDateTime end = session.getExitAt();
        if (end == null) {
            end = session.getStatus() == SessionStatus.ACTIVE ? now : session.getEntryAt();
        }
        return new Interval(session.getEntryAt(), end);
    }

    private OccupancyDay day(LocalDate date, List<Interval> intervals, LocalDateTime now) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        LocalDateTime effectiveEnd = now.isBefore(dayEnd) ? now : dayEnd;
        if (!effectiveEnd.isAfter(dayStart)) {
            return new OccupancyDay(date, 0, 0);
        }

        List<Event> events = new ArrayList<>();
        int running = 0;
        long occupiedSeconds = 0;
        for (Interval interval : intervals) {
            LocalDateTime start = interval.start().isAfter(dayStart) ? interval.start() : dayStart;
            LocalDateTime end = interval.end().isBefore(effectiveEnd) ? interval.end() : effectiveEnd;
            if (!end.isAfter(start)) {
                continue;
            }
            occupiedSeconds += Duration.between(start, end).getSeconds();
            if (start.equals(dayStart)) {
                running++;
            } else {
                events.add(new Event(start, 1));
            }
            if (end.isBefore(effectiveEnd)) {
                events.add(new Event(end, -1));
            }
        }

        events.sort(Comparator.comparing(Event::at).thenComparingInt(Event::delta));
        int peak = running;
        for (Event event : events) {
            running += event.delta();
            peak = Math.max(peak, running);
        }

        long daySeconds = Duration.between(dayStart, effectiveEnd).getSeconds();
        return new OccupancyDay(date, peak, round((double) occupiedSeconds / daySeconds));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Interval(LocalDateTime start, LocalDateTime end) {
    }

    private record Event(LocalDateTime at, int delta) {
    }
}
