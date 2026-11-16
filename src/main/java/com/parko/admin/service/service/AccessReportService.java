package com.parko.admin.service.service;

import com.parko.admin.service.dto.AccessDay;
import com.parko.admin.service.dto.AccessReport;
import com.parko.admin.service.dto.ReportPeriod;
import com.parko.admin.service.repository.AccessRow;
import com.parko.admin.service.repository.AdminAccessLogRepository;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.SessionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AccessReportService {

    private final AdminAccessLogRepository accessLogRepository;
    private final ReportPeriodResolver periodResolver;

    public AccessReportService(AdminAccessLogRepository accessLogRepository, ReportPeriodResolver periodResolver) {
        this.accessLogRepository = accessLogRepository;
        this.periodResolver = periodResolver;
    }

    public AccessReport report(LocalDate from, LocalDate to) {
        ReportPeriod period = periodResolver.resolve(from, to);
        Map<LocalDate, long[]> counters = new HashMap<>();
        for (AccessRow row : accessLogRepository.countByDay(period.start(), period.endExclusive())) {
            long[] counter = counters.computeIfAbsent(LocalDate.parse(row.getDay()), key -> new long[4]);
            boolean visitor = SessionType.valueOf(row.getSessionType()) == SessionType.VISITOR;
            boolean entry = AccessEventType.valueOf(row.getEventType()) == AccessEventType.ENTRY;
            counter[(visitor ? 2 : 0) + (entry ? 0 : 1)] += row.getTotal();
        }

        List<AccessDay> days = new ArrayList<>();
        long[] totals = new long[4];
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            long[] counter = counters.getOrDefault(date, new long[4]);
            days.add(new AccessDay(date, counter[0], counter[1], counter[2], counter[3]));
            for (int i = 0; i < totals.length; i++) {
                totals[i] += counter[i];
            }
        }
        return new AccessReport(period.from(), period.to(), days, totals[0], totals[1], totals[2], totals[3]);
    }
}
