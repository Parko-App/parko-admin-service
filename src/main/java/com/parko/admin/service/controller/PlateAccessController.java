package com.parko.admin.service.controller;

import com.parko.admin.service.dto.PlateEntryResponse;
import com.parko.admin.service.dto.PlateExitResponse;
import com.parko.admin.service.dto.PlateRequest;
import com.parko.admin.service.service.PlateAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ingreso y egreso generados por un administrador a partir de la patente que escribe. */
@RestController
@RequestMapping("/api/v1/admin/access")
public class PlateAccessController {

    private final PlateAccessService plateAccessService;

    public PlateAccessController(PlateAccessService plateAccessService) {
        this.plateAccessService = plateAccessService;
    }

    @PostMapping("/entry")
    public ResponseEntity<PlateEntryResponse> entry(@RequestBody PlateRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(plateAccessService.enter(request == null ? null : request.plate(), authentication.getName()));
    }

    @PostMapping("/exit")
    public PlateExitResponse exit(@RequestBody PlateRequest request, Authentication authentication) {
        return plateAccessService.exit(request == null ? null : request.plate(), authentication.getName());
    }
}
