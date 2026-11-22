package com.parko.admin.service.controller;

import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TicketDetailResponse;
import com.parko.admin.service.dto.TicketSummaryResponse;
import com.parko.admin.service.security.AdminJwtAuthenticationConverter;
import com.parko.admin.service.security.SecurityConfig;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.service.TicketPaymentService;
import com.parko.admin.service.service.TicketQueryService;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TicketController.class)
@Import(SecurityConfig.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketQueryService ticketQueryService;

    @MockitoBean
    private TicketPaymentService ticketPaymentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminJwtAuthenticationConverter jwtAuthenticationConverter;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_nonAdminUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tickets").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_admin_returnsPage() throws Exception {
        TicketSummaryResponse row = new TicketSummaryResponse(UUID.randomUUID(), "T-0001", TicketStatus.PAID,
                LocalDateTime.of(2026, 11, 10, 9, 30), null, SessionType.VISITOR, "ZZ999XX", null,
                "CASH", "Facundo Morales");
        when(ticketQueryService.list(any(), any(), any(), eq(0), any()))
                .thenReturn(new PageResponse<>(List.of(row), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/tickets").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketNumber").value("T-0001"))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.content[0].cashierName").value("Facundo Morales"))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_passesDateRangeStatusAndPage() throws Exception {
        when(ticketQueryService.list(any(), any(), any(), eq(2), any()))
                .thenReturn(new PageResponse<>(List.of(), 2, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/tickets")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-15")
                        .param("status", "PAID")
                        .param("page", "2")
                        .with(admin()))
                .andExpect(status().isOk());

        verify(ticketQueryService).list(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), TicketStatus.PAID, 2, null);
    }

    @Test
    void list_passesSort() throws Exception {
        when(ticketQueryService.list(any(), any(), any(), eq(0), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/tickets").param("sort", "status,asc").with(admin()))
                .andExpect(status().isOk());

        verify(ticketQueryService).list(null, null, null, 0, "status,asc");
    }

    @Test
    void list_invalidDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tickets").param("from", "10-11-2026").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para el parámetro 'from'"));
    }

    @Test
    void list_invalidRange_returns400() throws Exception {
        when(ticketQueryService.list(any(), any(), any(), eq(0), any()))
                .thenThrow(new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta"));

        mockMvc.perform(get("/api/v1/admin/tickets").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha desde no puede ser posterior a la fecha hasta"));
    }

    @Test
    void getById_admin_returnsTicket() throws Exception {
        UUID id = UUID.randomUUID();
        TicketDetailResponse detail = new TicketDetailResponse(id, "T-0001", TicketStatus.PENDING_PAYMENT, "qr",
                LocalDateTime.of(2026, 11, 10, 9, 30), null, null, null, null, null, null, null, null);
        when(ticketQueryService.getById(id)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/tickets/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNumber").value("T-0001"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketQueryService.getById(id)).thenThrow(new NoSuchElementException("Ticket no encontrado"));

        mockMvc.perform(get("/api/v1/admin/tickets/" + id).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket no encontrado"));
    }

    @Test
    void getById_malformedId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tickets/not-a-uuid").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByNumber_admin_returnsTicket() throws Exception {
        TicketDetailResponse detail = new TicketDetailResponse(UUID.randomUUID(), "T-0007", TicketStatus.USED, "qr",
                LocalDateTime.of(2026, 11, 10, 9, 30), null, null, null, null, null, null, null, null);
        when(ticketQueryService.getByNumber("T-0007")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/tickets/number/T-0007").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"));
    }

    @Test
    void unexpectedError_returns500WithGenericMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketQueryService.getById(id)).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/v1/admin/tickets/" + id).with(admin()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado"));
    }

    @Test
    void otherPaths_areDenied() throws Exception {
        mockMvc.perform(get("/api/v1/other").with(admin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pay_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tickets/" + UUID.randomUUID() + "/pay"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pay_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tickets/" + UUID.randomUUID() + "/pay").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pay_admin_registersPaymentWithOperatorUid() throws Exception {
        UUID id = UUID.randomUUID();
        TicketDetailResponse detail = new TicketDetailResponse(id, "TCK-1", TicketStatus.PAID, "qr",
                LocalDateTime.of(2026, 11, 10, 9, 30), LocalDateTime.of(2026, 11, 10, 10, 0), null, null, null, null,
                null, null, null);
        when(ticketPaymentService.payInCash(id, null, "admin-uid")).thenReturn(detail);

        mockMvc.perform(post("/api/v1/admin/tickets/" + id + "/pay")
                        .with(jwt().jwt(token -> token.subject("admin-uid")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void pay_withAmount_passesItToTheService() throws Exception {
        UUID id = UUID.randomUUID();
        TicketDetailResponse detail = new TicketDetailResponse(id, "TCK-1", TicketStatus.PAID, "qr",
                LocalDateTime.of(2026, 11, 10, 9, 30), LocalDateTime.of(2026, 11, 10, 10, 0), null, null, null, null,
                null, null, null);
        when(ticketPaymentService.payInCash(id, new BigDecimal("1.00"), "admin-uid")).thenReturn(detail);

        mockMvc.perform(post("/api/v1/admin/tickets/" + id + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00}")
                        .with(jwt().jwt(token -> token.subject("admin-uid")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(ticketPaymentService).payInCash(id, new BigDecimal("1.00"), "admin-uid");
    }

    @Test
    void pay_amountDoesNotMatch_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketPaymentService.payInCash(eq(id), any(), any()))
                .thenThrow(new IllegalArgumentException("El monto ingresado no coincide con el total a cobrar ($1)"));

        mockMvc.perform(post("/api/v1/admin/tickets/" + id + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1500}")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El monto ingresado no coincide con el total a cobrar ($1)"));
    }

    @Test
    void pay_notPending_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketPaymentService.payInCash(eq(id), any(), any()))
                .thenThrow(new ConflictException("El ticket no está pendiente de pago: PAID"));

        mockMvc.perform(post("/api/v1/admin/tickets/" + id + "/pay").with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void pay_unknownTicket_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketPaymentService.payInCash(eq(id), any(), any())).thenThrow(new NoSuchElementException("Ticket no encontrado"));

        mockMvc.perform(post("/api/v1/admin/tickets/" + id + "/pay").with(admin()))
                .andExpect(status().isNotFound());
    }
}
