package com.parko.admin.service.controller;

import com.parko.admin.service.dto.AccessDay;
import com.parko.admin.service.dto.AccessReport;
import com.parko.admin.service.dto.OccupancyDay;
import com.parko.admin.service.dto.OccupancyReport;
import com.parko.admin.service.dto.RevenueDay;
import com.parko.admin.service.dto.RevenueReport;
import com.parko.admin.service.security.AdminJwtAuthenticationConverter;
import com.parko.admin.service.security.SecurityConfig;
import com.parko.admin.service.service.AccessReportService;
import com.parko.admin.service.service.OccupancyReportService;
import com.parko.admin.service.service.RevenueReportService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 10);
    private static final LocalDate TO = LocalDate.of(2026, 11, 11);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OccupancyReportService occupancyReportService;

    @MockitoBean
    private RevenueReportService revenueReportService;

    @MockitoBean
    private AccessReportService accessReportService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminJwtAuthenticationConverter jwtAuthenticationConverter;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void occupancy_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/occupancy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void occupancy_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/occupancy").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void occupancy_admin_returnsReport() throws Exception {
        when(occupancyReportService.report(FROM, TO)).thenReturn(new OccupancyReport(FROM, TO,
                List.of(new OccupancyDay(FROM, 4, 1.5), new OccupancyDay(TO, 2, 0.5)), 4, 1.0));

        mockMvc.perform(get("/api/v1/admin/reports/occupancy")
                        .param("from", "2026-11-10").param("to", "2026-11-11").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peak").value(4))
                .andExpect(jsonPath("$.average").value(1.0))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-10"))
                .andExpect(jsonPath("$.days[0].peak").value(4))
                .andExpect(jsonPath("$.days[1].average").value(0.5));
    }

    @Test
    void occupancy_withoutDates_delegatesWithNulls() throws Exception {
        when(occupancyReportService.report(null, null)).thenReturn(new OccupancyReport(FROM, TO, List.of(), 0, 0));

        mockMvc.perform(get("/api/v1/admin/reports/occupancy").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-10"));
    }

    @Test
    void occupancy_invalidDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/occupancy").param("from", "10/11/2026").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void occupancy_invalidRange_returns400() throws Exception {
        when(occupancyReportService.report(TO, FROM)).thenThrow(new IllegalArgumentException("rango inválido"));

        mockMvc.perform(get("/api/v1/admin/reports/occupancy")
                        .param("from", "2026-11-11").param("to", "2026-11-10").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("rango inválido"));
    }

    @Test
    void revenue_admin_returnsReport() throws Exception {
        RevenueDay day = new RevenueDay(FROM, new BigDecimal("1500.00"), new BigDecimal("3000.00"),
                new BigDecimal("4500.00"));
        when(revenueReportService.report(FROM, TO)).thenReturn(new RevenueReport(FROM, TO, List.of(day), List.of(),
                List.of(), new BigDecimal("1500.00"), new BigDecimal("3000.00"), new BigDecimal("4500.00"),
                new BigDecimal("2250.00")));

        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .param("from", "2026-11-10").param("to", "2026-11-11").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4500.00))
                .andExpect(jsonPath("$.dailyAverage").value(2250.00))
                .andExpect(jsonPath("$.days[0].registered").value(1500.00))
                .andExpect(jsonPath("$.days[0].visitors").value(3000.00));
    }

    @Test
    void revenue_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/revenue").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void access_admin_returnsReport() throws Exception {
        when(accessReportService.report(FROM, TO)).thenReturn(new AccessReport(FROM, TO,
                List.of(new AccessDay(FROM, 5, 4, 3, 2)), 5, 4, 3, 2));

        mockMvc.perform(get("/api/v1/admin/reports/access")
                        .param("from", "2026-11-10").param("to", "2026-11-11").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredEntries").value(5))
                .andExpect(jsonPath("$.visitorExits").value(2))
                .andExpect(jsonPath("$.days[0].registeredExits").value(4))
                .andExpect(jsonPath("$.days[0].visitorEntries").value(3));
    }

    @Test
    void access_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/access"))
                .andExpect(status().isUnauthorized());
    }
}
