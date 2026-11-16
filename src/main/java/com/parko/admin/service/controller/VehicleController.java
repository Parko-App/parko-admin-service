package com.parko.admin.service.controller;

import com.parko.admin.service.dto.CreateVehicleRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.UpdateVehicleRequest;
import com.parko.admin.service.dto.VehicleResponse;
import com.parko.admin.service.service.VehicleAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/vehicles")
public class VehicleController {

    private final VehicleAdminService vehicleAdminService;

    public VehicleController(VehicleAdminService vehicleAdminService) {
        this.vehicleAdminService = vehicleAdminService;
    }

    @GetMapping
    public PageResponse<VehicleResponse> list(
            @RequestParam(required = false) String plate,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page) {
        return vehicleAdminService.list(plate, userId, active, page);
    }

    @GetMapping("/{id}")
    public VehicleResponse getById(@PathVariable UUID id) {
        return vehicleAdminService.get(id);
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody CreateVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleAdminService.create(request));
    }

    @PatchMapping("/{id}")
    public VehicleResponse update(@PathVariable UUID id, @RequestBody UpdateVehicleRequest request) {
        return vehicleAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vehicleAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
