package com.parko.admin.service.controller;

import com.parko.admin.service.dto.CreateTopUpRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TopUpResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.security.AdminJwtAuthenticationConverter;
import com.parko.admin.service.security.SecurityConfig;
import com.parko.admin.service.service.TopUpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TopUpController.class)
@Import(SecurityConfig.class)
class TopUpControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopUpService topUpService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminJwtAuthenticationConverter jwtAuthenticationConverter;

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin-uid")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static TopUpResponse response() {
        return new TopUpResponse(UUID.randomUUID(), "TX-54EFBE", "Lucas Fernández", "lucas@frc.utn.edu.ar",
                "2026-11-21T10:30:00-03:00", new BigDecimal("1500.00"), "CASH", "CREDITED");
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/top-ups")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/top-ups").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void list_admin_returnsPageAndPassesParams() throws Exception {
        when(topUpService.list("lucas", 2, 10, "amount,desc"))
                .thenReturn(new PageResponse<>(List.of(response()), 2, 10, 21, 3));

        mockMvc.perform(get("/api/v1/admin/top-ups")
                        .param("search", "lucas").param("page", "2").param("size", "10").param("sort", "amount,desc")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionNumber").value("TX-54EFBE"))
                .andExpect(jsonPath("$.content[0].method").value("CASH"))
                .andExpect(jsonPath("$.content[0].status").value("CREDITED"))
                .andExpect(jsonPath("$.content[0].amount").value(1500.00))
                .andExpect(jsonPath("$.totalElements").value(21));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/top-ups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"amount\":100}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/top-ups").with(jwt()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"amount\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_admin_returns201AndPassesOperator() throws Exception {
        when(topUpService.create(new CreateTopUpRequest(USER_ID, new BigDecimal("1500")), "admin-uid"))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/admin/top-ups").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"amount\":1500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREDITED"))
                .andExpect(jsonPath("$.userName").value("Lucas Fernández"));

        verify(topUpService).create(new CreateTopUpRequest(USER_ID, new BigDecimal("1500")), "admin-uid");
    }

    @Test
    void create_invalidAmount_returns400() throws Exception {
        when(topUpService.create(new CreateTopUpRequest(USER_ID, new BigDecimal("-1")), "admin-uid"))
                .thenThrow(new IllegalArgumentException("El monto debe ser mayor a cero"));

        mockMvc.perform(post("/api/v1/admin/top-ups").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El monto debe ser mayor a cero"));
    }

    @Test
    void create_inactiveUser_returns409() throws Exception {
        when(topUpService.create(new CreateTopUpRequest(USER_ID, new BigDecimal("100")), "admin-uid"))
                .thenThrow(new ConflictException("El usuario está dado de baja"));

        mockMvc.perform(post("/api/v1/admin/top-ups").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"amount\":100}"))
                .andExpect(status().isConflict());
    }
}
