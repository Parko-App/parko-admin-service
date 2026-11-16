package com.parko.admin.service.controller;

import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.VehicleResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.security.AdminJwtAuthenticationConverter;
import com.parko.admin.service.security.SecurityConfig;
import com.parko.admin.service.service.VehicleAdminService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VehicleController.class)
@Import(SecurityConfig.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleAdminService vehicleAdminService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminJwtAuthenticationConverter jwtAuthenticationConverter;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private VehicleResponse vehicle(UUID id, UUID userId) {
        return new VehicleResponse(id, userId, "Ana Pérez", "82116@frc.utn.edu.ar", "AB123CD", "Ford", "Ka",
                true, null, null);
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vehicles").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_admin_passesFiltersAndReturnsPage() throws Exception {
        UUID userId = UUID.randomUUID();
        when(vehicleAdminService.list("ab1", userId, true, 0))
                .thenReturn(new PageResponse<>(List.of(vehicle(UUID.randomUUID(), userId)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/vehicles")
                        .param("plate", "ab1").param("userId", userId.toString()).param("active", "true")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].plate").value("AB123CD"));
    }

    @Test
    void list_invalidUserId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vehicles").param("userId", "no-uuid").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_admin_returnsVehicle() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicleAdminService.get(id)).thenReturn(vehicle(id, UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/admin/vehicles/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Ford"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicleAdminService.get(id)).thenThrow(new NoSuchElementException("Vehículo no encontrado"));

        mockMvc.perform(get("/api/v1/admin/vehicles/" + id).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_valid_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        when(vehicleAdminService.create(any())).thenReturn(vehicle(UUID.randomUUID(), userId));

        mockMvc.perform(post("/api/v1/admin/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"plate\":\"AB123CD\",\"brand\":\"Ford\",\"model\":\"Ka\"}")
                        .with(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plate").value("AB123CD"));
    }

    @Test
    void create_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"AB123CD\"}")
                        .with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_plateTaken_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        when(vehicleAdminService.create(any())).thenThrow(new ConflictException("La patente ya está registrada"));

        mockMvc.perform(post("/api/v1/admin/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"plate\":\"AB123CD\",\"brand\":\"Ford\",\"model\":\"Ka\"}")
                        .with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La patente ya está registrada"));
    }

    @Test
    void update_admin_returnsUpdatedVehicle() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicleAdminService.update(eq(id), any())).thenReturn(vehicle(id, UUID.randomUUID()));

        mockMvc.perform(patch("/api/v1/admin/vehicles/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brand\":\"Renault\",\"active\":true}")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void update_limitReached_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicleAdminService.update(eq(id), any()))
                .thenThrow(new IllegalArgumentException("El usuario ya tiene el máximo de 3 vehículos activos"));

        mockMvc.perform(patch("/api/v1/admin/vehicles/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}")
                        .with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_admin_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/vehicles/" + id).with(admin()))
                .andExpect(status().isNoContent());

        verify(vehicleAdminService).delete(id);
    }

    @Test
    void delete_nonAdmin_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/vehicles/" + UUID.randomUUID()).with(jwt()))
                .andExpect(status().isForbidden());
    }
}
