package com.parko.admin.service.controller;

import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.UserDetailResponse;
import com.parko.admin.service.dto.UserSummaryResponse;
import com.parko.admin.service.security.AdminJwtAuthenticationConverter;
import com.parko.admin.service.security.SecurityConfig;
import com.parko.admin.service.service.UserAdminService;
import com.parko.domain.lib.model.InstitutionalDomain;
import com.parko.domain.lib.model.UserRole;
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
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAdminService userAdminService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminJwtAuthenticationConverter jwtAuthenticationConverter;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private UserDetailResponse detail(UUID id) {
        return new UserDetailResponse(id, "82116", "Ana Pérez", "82116@frc.utn.edu.ar", InstitutionalDomain.FRC,
                UserRole.STUDENT, true, true, "uid-1", new BigDecimal("100.00"), null, null, List.of());
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_admin_passesFiltersAndReturnsPage() throws Exception {
        UserSummaryResponse row = new UserSummaryResponse(UUID.randomUUID(), "82116", "Ana Pérez",
                "82116@frc.utn.edu.ar", UserRole.STAFF, true);
        when(userAdminService.list("ana", UserRole.STAFF, true, 1))
                .thenReturn(new PageResponse<>(List.of(row), 1, 20, 21, 2));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", "ana").param("role", "STAFF").param("active", "true").param("page", "1")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Ana Pérez"))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void list_invalidRole_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("role", "SUPERMAN").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_admin_returnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(userAdminService.get(id)).thenReturn(detail(id));

        mockMvc.perform(get("/api/v1/admin/users/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("82116@frc.utn.edu.ar"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(userAdminService.get(id)).thenThrow(new NoSuchElementException("Usuario no encontrado"));

        mockMvc.perform(get("/api/v1/admin/users/" + id).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_admin_returnsUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(userAdminService.update(eq(id), any())).thenReturn(detail(id));

        mockMvc.perform(patch("/api/v1/admin/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana María\",\"active\":false}")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void update_invalidData_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(userAdminService.update(eq(id), any())).thenThrow(new IllegalArgumentException("Email invalido"));

        mockMvc.perform(patch("/api/v1/admin/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x\"}")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email invalido"));
    }

    @Test
    void delete_admin_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/users/" + id).with(admin()))
                .andExpect(status().isNoContent());

        verify(userAdminService).delete(id);
    }

    @Test
    void delete_nonAdmin_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/" + UUID.randomUUID()).with(jwt()))
                .andExpect(status().isForbidden());
    }
}
