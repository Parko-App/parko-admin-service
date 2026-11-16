package com.parko.admin.service.controller;

import com.parko.admin.service.dto.CreateUserRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.UpdateUserRequest;
import com.parko.admin.service.dto.UserDetailResponse;
import com.parko.admin.service.dto.UserSummaryResponse;
import com.parko.admin.service.service.UserAdminService;
import com.parko.domain.lib.model.UserRole;
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
@RequestMapping("/api/v1/admin/users")
public class UserController {

    private final UserAdminService userAdminService;

    public UserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public PageResponse<UserSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page) {
        return userAdminService.list(q, role, active, page);
    }

    @GetMapping("/{id}")
    public UserDetailResponse getById(@PathVariable UUID id) {
        return userAdminService.get(id);
    }

    @PostMapping
    public ResponseEntity<UserDetailResponse> create(@RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.create(request));
    }

    @PatchMapping("/{id}")
    public UserDetailResponse update(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        return userAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
