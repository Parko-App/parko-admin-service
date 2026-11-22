package com.parko.admin.service.service;

import com.parko.admin.service.dto.ReportPeriod;
import com.parko.admin.service.dto.RevenueDay;
import com.parko.admin.service.dto.RevenueMonthBest;
import com.parko.admin.service.dto.RevenueReport;
import com.parko.admin.service.dto.RevenueWeek;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.admin.service.repository.RevenueRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class RevenueReportService {

    private static final String SOURCE_TOPUP = "TOPUP";

    private final AdminTransactionRepository transactionRepository;
    private final ReportPeriodResolver periodResolver;

    public RevenueReportService(AdminTransactionRepository transactionRepository, ReportPeriodResolver periodResolver) {
        this.transactionRepository = transactionRepository;
        this.periodResolver = periodResolver;
    }

    public RevenueReport report(LocalDate from, LocalDate to) {
        ReportPeriod period = periodResolver.resolve(from, to);
        Map<LocalDate, BigDecimal> topUps = new HashMap<>();
        Map<LocalDate, BigDecimal> cash = new HashMap<>();
        for (RevenueRow row : transactionRepository.sumRevenueByDay(period.start(), period.endExclusive())) {
            LocalDate date = LocalDate.parse(row.getDay());
            Map<LocalDate, BigDecimal> target = SOURCE_TOPUP.equals(row.getSource()) ? topUps : cash;
            target.merge(date, row.getTotal(), BigDecimal::add);
        }

        List<RevenueDay> days = new ArrayList<>();
        BigDecimal totalTopUps = BigDecimal.ZERO;
        BigDecimal totalCash = BigDecimal.ZERO;
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            BigDecimal dayTopUps = topUps.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal dayCash = cash.getOrDefault(date, BigDecimal.ZERO);
            days.add(new RevenueDay(date, dayTopUps, dayCash, dayTopUps.add(dayCash)));
            totalTopUps = totalTopUps.add(dayTopUps);
            totalCash = totalCash.add(dayCash);
        }

        BigDecimal total = totalTopUps.add(totalCash);
        long dayCount = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
        BigDecimal average = total.divide(BigDecimal.valueOf(dayCount), 2, RoundingMode.HALF_UP);

        return new RevenueReport(period.from(), period.to(), days, weeks(days), bestDayByMonth(days),
                totalTopUps, totalCash, total, average);
    }

    private List<RevenueWeek> weeks(List<RevenueDay> days) {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        for (RevenueDay day : days) {
            LocalDate weekStart = day.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            totals.merge(weekStart, day.total(), BigDecimal::add);
        }
        return totals.entrySet().stream().map(entry -> new RevenueWeek(entry.getKey(), entry.getValue())).toList();
    }

    private List<RevenueMonthBest> bestDayByMonth(List<RevenueDay> days) {
        Map<YearMonth, RevenueDay> best = new LinkedHashMap<>();
        for (RevenueDay day : days) {
            YearMonth month = YearMonth.from(day.date());
            RevenueDay current = best.get(month);
            boolean earned = day.total().signum() > 0;
            if (earned && (current == null || day.total().compareTo(current.total()) > 0)) {
                best.put(month, day);
            }
        }
        return best.entrySet().stream()
                .map(entry -> new RevenueMonthBest(entry.getKey(), entry.getValue().date(), entry.getValue().total()))
                .toList();
    }
}
