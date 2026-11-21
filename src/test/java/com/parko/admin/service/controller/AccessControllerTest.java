package com.parko.admin.service.controller;

import com.parko.admin.service.dto.AccessLogResponse;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.security.AdminJwtAuthenticationConverter;
import com.parko.admin.service.security.SecurityConfig;
import com.parko.admin.service.service.AccessLogQueryService;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccessController.class)
@Import(SecurityConfig.class)
class AccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessLogQueryService accessLogQueryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminJwtAuthenticationConverter jwtAuthenticationConverter;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accesos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_nonAdminUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accesos").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_admin_returnsPage() throws Exception {
        AccessLogResponse row = new AccessLogResponse(UUID.randomUUID(), AccessEventType.ENTRY, AccessMethod.PLATE,
                AccessResult.AUTHORIZED, LocalDateTime.of(2026, 11, 10, 9, 30), UUID.randomUUID(),
                SessionType.REGISTERED, "AB123CD", AccessEventType.ENTRY, LocalDateTime.of(2026, 11, 10, 9, 30),
                "AUTOMATIC", "Lucas Fernández", "REGISTERED");
        when(accessLogQueryService.list(null, null, 0, null, null)).thenReturn(new PageResponse<>(List.of(row), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/accesos").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("ENTRY"))
                .andExpect(jsonPath("$.content[0].plate").value("AB123CD"))
                .andExpect(jsonPath("$.content[0].movement").value("ENTRY"))
                .andExpect(jsonPath("$.content[0].method").value("AUTOMATIC"))
                .andExpect(jsonPath("$.content[0].userName").value("Lucas Fernández"))
                .andExpect(jsonPath("$.content[0].userType").value("REGISTERED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_passesPage() throws Exception {
        when(accessLogQueryService.list(null, null, 3, null, null)).thenReturn(new PageResponse<>(List.of(), 3, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/accesos").param("page", "3").with(admin()))
                .andExpect(status().isOk());

        verify(accessLogQueryService).list(null, null, 3, null, null);
    }

    @Test
    void list_passesFilters() throws Exception {
        when(accessLogQueryService.list("AB1", AccessEventType.EXIT, 0, 10, "timestamp,asc"))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/admin/accesos")
                        .param("search", "AB1").param("movement", "EXIT").param("size", "10")
                        .param("sort", "timestamp,asc").with(admin()))
                .andExpect(status().isOk());

        verify(accessLogQueryService).list("AB1", AccessEventType.EXIT, 0, 10, "timestamp,asc");
    }

    @Test
    void list_negativePage_returns400() throws Exception {
        when(accessLogQueryService.list(null, null, -1, null, null)).thenThrow(new IllegalArgumentException("La página no puede ser negativa"));

        mockMvc.perform(get("/api/v1/admin/accesos").param("page", "-1").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La página no puede ser negativa"));
    }
}
