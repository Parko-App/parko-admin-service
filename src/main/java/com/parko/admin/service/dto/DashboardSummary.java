package com.parko.admin.service.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resumen del día para el dashboard del panel web (GET /api/v1/admin/dashboard/summary). */
public record DashboardSummary(
        Occupancy occupancy,
        RevenueToday revenueToday,
        ActiveSessions activeSessions,
        VisitorsToday visitorsToday,
        DayStats dayStats,
        List<HourlyOccupancy> occupancyByHour
) {

    public record Occupancy(long current, int capacity) {
    }

    /** changePercent compara con el mismo tramo horario de ayer (0 si ayer no hubo ingresos). */
    public record RevenueToday(BigDecimal amount, double changePercent) {
    }

    public record ActiveSessions(long total, long visitors, long registered) {
    }

    public record VisitorsToday(long count, BigDecimal collected, long pendingPayments) {
    }

    public record DayStats(
            long entries,
            long exits,
            long uniqueVisitors,
            long paymentsProcessed,
            long paymentsPending,
            long openIncidents
    ) {
    }

    /** Vehículos estacionados al cierre de la hora (0 a 23). Las horas que todavía no ocurrieron valen 0. */
    public record HourlyOccupancy(int hour, long vehicles) {
    }
}
