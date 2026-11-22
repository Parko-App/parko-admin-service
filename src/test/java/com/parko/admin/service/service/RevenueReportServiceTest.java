package com.parko.admin.service.service;

import com.parko.admin.service.dto.RevenueReport;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.admin.service.repository.RevenueRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueReportServiceTest {

    @Mock
    private AdminTransactionRepository transactionRepository;

    private RevenueReportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        service = new RevenueReportService(transactionRepository, new ReportPeriodResolver(clock, 30, 366));
    }

    private static RevenueRow row(String day, String source, String total) {
        return new RevenueRow() {
            @Override
            public String getDay() {
                return day;
            }

            @Override
            public String getSource() {
                return source;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " pero fue " + actual);
    }

    @Test
    void report_splitsTopUpsAndCashPerDayAndTotals() {
        when(transactionRepository.sumRevenueByDay(any(), any())).thenReturn(List.of(
                row("2026-11-10", "TOPUP", "1500.00"),
                row("2026-11-10", "CASH", "3000.00"),
                row("2026-11-12", "CASH", "3000.00"),
                row("2026-11-17", "TOPUP", "4500.00")));

        RevenueReport report = service.report(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 18));

        assertEquals(10, report.days().size());
        assertAmount("1500", report.days().get(1).topUps());
        assertAmount("3000", report.days().get(1).cash());
        assertAmount("4500", report.days().get(1).total());
        assertAmount("0", report.days().get(0).total());
        assertAmount("6000", report.topUps());
        assertAmount("6000", report.cash());
        assertAmount("12000", report.total());
        assertAmount("1200", report.dailyAverage());
    }

    @Test
    void report_groupsTotalsByWeekStartingMonday() {
        when(transactionRepository.sumRevenueByDay(any(), any())).thenReturn(List.of(
                row("2026-11-10", "TOPUP", "1500.00"),
                row("2026-11-12", "CASH", "3000.00"),
                row("2026-11-17", "TOPUP", "4500.00")));

        RevenueReport report = service.report(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 18));

        assertEquals(2, report.weeks().size());
        assertEquals(LocalDate.of(2026, 11, 9), report.weeks().get(0).weekStart());
        assertAmount("4500", report.weeks().get(0).total());
        assertEquals(LocalDate.of(2026, 11, 16), report.weeks().get(1).weekStart());
        assertAmount("4500", report.weeks().get(1).total());
    }

    @Test
    void report_partialWeekIsLabeledWithItsMonday() {
        when(transactionRepository.sumRevenueByDay(any(), any())).thenReturn(List.of(
                row("2026-10-30", "CASH", "3000.00"),
                row("2026-11-02", "CASH", "6000.00")));

        RevenueReport report = service.report(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2));

        assertEquals(LocalDate.of(2026, 10, 26), report.weeks().get(0).weekStart());
        assertAmount("3000", report.weeks().get(0).total());
        assertEquals(LocalDate.of(2026, 11, 2), report.weeks().get(1).weekStart());
        assertAmount("6000", report.weeks().get(1).total());
    }

    @Test
    void report_bestDayIsComputedPerMonth() {
        when(transactionRepository.sumRevenueByDay(any(), any())).thenReturn(List.of(
                row("2026-10-30", "CASH", "3000.00"),
                row("2026-10-31", "TOPUP", "9000.00"),
                row("2026-11-01", "CASH", "3000.00"),
                row("2026-11-02", "CASH", "6000.00")));

        RevenueReport report = service.report(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2));

        assertEquals(2, report.bestDayByMonth().size());
        assertEquals(YearMonth.of(2026, 10), report.bestDayByMonth().get(0).month());
        assertEquals(LocalDate.of(2026, 10, 31), report.bestDayByMonth().get(0).date());
        assertAmount("9000", report.bestDayByMonth().get(0).total());
        assertEquals(YearMonth.of(2026, 11), report.bestDayByMonth().get(1).month());
        assertEquals(LocalDate.of(2026, 11, 2), report.bestDayByMonth().get(1).date());
        assertAmount("6000", report.bestDayByMonth().get(1).total());
    }

    @Test
    void report_tiedDaysKeepTheEarliest() {
        when(transactionRepository.sumRevenueByDay(any(), any())).thenReturn(List.of(
                row("2026-11-03", "CASH", "3000.00"),
                row("2026-11-05", "CASH", "3000.00")));

        RevenueReport report = service.report(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));

        assertEquals(LocalDate.of(2026, 11, 3), report.bestDayByMonth().get(0).date());
    }

    @Test
    void report_withoutCharges_hasNoBestDayAndZeroTotals() {
        when(transactionRepository.sumRevenueByDay(any(), any())).thenReturn(List.of());

        RevenueReport report = service.report(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3));

        assertEquals(3, report.days().size());
        assertTrue(report.bestDayByMonth().isEmpty());
        assertAmount("0", report.total());
        assertAmount("0", report.dailyAverage());
    }

    @Test
    void report_dailyAverageIsRoundedToTwoDecimals() {
        when(transactionRepository.sumRevenueByDay(any(), any())).thenReturn(List.of(
                row("2026-11-01", "CASH", "1000.00")));

        RevenueReport report = service.report(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3));

        assertAmount("333.33", report.dailyAverage());
    }

    @Test
    void report_sumsRowsOfSameDayAndType() {
        when(transactionRepository.sumRevenueByDay(any(), any())).thenReturn(List.of(
                row("2026-11-01", "CASH", "1000.00"),
                row("2026-11-01", "CASH", "500.00")));

        RevenueReport report = service.report(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        assertAmount("1500", report.days().get(0).cash());
    }

    @Test
    void report_invalidRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.report(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)));
    }
}
