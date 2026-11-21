package com.parko.admin.service.controller;

import com.parko.admin.service.dto.DashboardSummary;
import com.parko.admin.service.dto.RecentAccessResponse;
import com.parko.admin.service.security.AdminJwtAuthenticationConverter;
import com.parko.admin.service.security.SecurityConfig;
import com.parko.admin.service.service.DashboardService;
import com.parko.domain.lib.model.AccessEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminJwtAuthenticationConverter jwtAuthenticationConverter;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void summary_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summary_nonAdminUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void summary_admin_returnsContractShape() throws Exception {
        DashboardSummary summary = new DashboardSummary(
                new DashboardSummary.Occupancy(221, 300),
                new DashboardSummary.RevenueToday(new BigDecimal("18340"), 12.5),
                new DashboardSummary.ActiveSessions(221, 8, 213),
                new DashboardSummary.VisitorsToday(34, new BigDecimal("9120"), 12),
                new DashboardSummary.DayStats(335, 114, 34, 28, 6, 0),
                List.of(new DashboardSummary.HourlyOccupancy(0, 10), new DashboardSummary.HourlyOccupancy(1, 6)));
        when(dashboardService.summary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/admin/dashboard/summary").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupancy.current").value(221))
                .andExpect(jsonPath("$.occupancy.capacity").value(300))
                .andExpect(jsonPath("$.revenueToday.amount").value(18340))
                .andExpect(jsonPath("$.revenueToday.changePercent").value(12.5))
                .andExpect(jsonPath("$.activeSessions.registered").value(213))
                .andExpect(jsonPath("$.visitorsToday.pendingPayments").value(12))
                .andExpect(jsonPath("$.dayStats.paymentsPending").value(6))
                .andExpect(jsonPath("$.dayStats.openIncidents").value(0))
                .andExpect(jsonPath("$.occupancyByHour[1].hour").value(1))
                .andExpect(jsonPath("$.occupancyByHour[1].vehicles").value(6));
    }

    @Test
    void recentAccess_admin_returnsRowsAndPassesLimit() throws Exception {
        when(dashboardService.recentAccess(5)).thenReturn(List.of(new RecentAccessResponse(
                "abc", "AB123CD", "Lucas Fernández", "REGISTERED", "2026-11-21T10:47:00-03:00", AccessEventType.ENTRY)));

        mockMvc.perform(get("/api/v1/admin/dashboard/recent-access").param("limit", "5").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("abc"))
                .andExpect(jsonPath("$[0].plate").value("AB123CD"))
                .andExpect(jsonPath("$[0].userType").value("REGISTERED"))
                .andExpect(jsonPath("$[0].timestamp").value("2026-11-21T10:47:00-03:00"))
                .andExpect(jsonPath("$[0].movement").value("ENTRY"));

        verify(dashboardService).recentAccess(5);
    }

    @Test
    void recentAccess_withoutLimit_passesNull() throws Exception {
        when(dashboardService.recentAccess(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/dashboard/recent-access").with(admin()))
                .andExpect(status().isOk());

        verify(dashboardService).recentAccess(null);
    }
}
