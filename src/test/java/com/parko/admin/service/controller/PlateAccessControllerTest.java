package com.parko.admin.service.controller;

import com.parko.admin.service.dto.PlateEntryResponse;
import com.parko.admin.service.dto.PlateExitResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.exception.ExternalServiceException;
import com.parko.admin.service.security.AdminJwtAuthenticationConverter;
import com.parko.admin.service.security.SecurityConfig;
import com.parko.admin.service.service.AccessServiceClient;
import com.parko.admin.service.service.PlateAccessService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlateAccessController.class)
@Import(SecurityConfig.class)
class PlateAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlateAccessService plateAccessService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminJwtAuthenticationConverter jwtAuthenticationConverter;

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-uid")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static final String BODY = "{\"plate\":\"AB123CD\"}";

    @Test
    void entry_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/access/entry").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void entry_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/access/entry").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void exit_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/access/exit").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void entry_admin_returns201WithTheTicketAndPassesPlateAndOperator() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(plateAccessService.enter("AB123CD", "admin-uid")).thenReturn(new PlateEntryResponse(
                ticketId, "TCK-ABCD1234", TicketStatus.PENDING_PAYMENT, SessionType.VISITOR, "AB123CD",
                "Ingreso de visitante generado. Entregá el ticket para que pueda pagar."));

        mockMvc.perform(post("/api/v1/admin/access/entry").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").value("TCK-ABCD1234"))
                .andExpect(jsonPath("$.sessionType").value("VISITOR"))
                .andExpect(jsonPath("$.ticketStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.ticketPdf").doesNotExist());

        verify(plateAccessService).enter("AB123CD", "admin-uid");
    }

    @Test
    void entry_plateAlreadyInside_returns409() throws Exception {
        when(plateAccessService.enter("AB123CD", "admin-uid"))
                .thenThrow(new ConflictException("La patente AB123CD ya tiene un ingreso activo"));

        mockMvc.perform(post("/api/v1/admin/access/entry").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La patente AB123CD ya tiene un ingreso activo"));
    }

    @Test
    void entry_invalidPlate_returns400() throws Exception {
        when(plateAccessService.enter("XX", "admin-uid"))
                .thenThrow(new IllegalArgumentException("La patente no es válida. Usá el formato ABC123 o AB123CD"));

        mockMvc.perform(post("/api/v1/admin/access/entry").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"XX\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entry_accessServiceDown_returns502() throws Exception {
        when(plateAccessService.enter("AB123CD", "admin-uid"))
                .thenThrow(new ExternalServiceException(AccessServiceClient.UNREACHABLE, null));

        mockMvc.perform(post("/api/v1/admin/access/entry").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(AccessServiceClient.UNREACHABLE));
    }

    @Test
    void exit_admin_returns200() throws Exception {
        when(plateAccessService.exit("AB123CD", "admin-uid")).thenReturn(new PlateExitResponse(
                UUID.randomUUID(), "TCK-ABCD1234", SessionType.VISITOR, "AB123CD", "Egreso generado para AB123CD"));

        mockMvc.perform(post("/api/v1/admin/access/exit").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNumber").value("TCK-ABCD1234"))
                .andExpect(jsonPath("$.message").value("Egreso generado para AB123CD"));

        verify(plateAccessService).exit("AB123CD", "admin-uid");
    }

    @Test
    void exit_unpaidTicket_returns409() throws Exception {
        when(plateAccessService.exit("AB123CD", "admin-uid"))
                .thenThrow(new ConflictException("El ticket TCK-ABCD1234 no está pagado (pendiente de pago)"));

        mockMvc.perform(post("/api/v1/admin/access/exit").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void exit_plateWithoutActiveSession_returns404() throws Exception {
        when(plateAccessService.exit("AB123CD", "admin-uid"))
                .thenThrow(new NoSuchElementException("La patente AB123CD no tiene un ingreso activo"));

        mockMvc.perform(post("/api/v1/admin/access/exit").with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }
}
