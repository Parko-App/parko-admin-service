package com.parko.admin.service.controller;

import com.parko.admin.service.dto.CreateTopUpRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TopUpResponse;
import com.parko.admin.service.service.TopUpService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/top-ups")
public class TopUpController {

    private final TopUpService topUpService;

    public TopUpController(TopUpService topUpService) {
        this.topUpService = topUpService;
    }

    @GetMapping
    public PageResponse<TopUpResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return topUpService.list(search, page, size, sort);
    }

    @PostMapping
    public ResponseEntity<TopUpResponse> create(@RequestBody CreateTopUpRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(topUpService.create(request, authentication.getName()));
    }
}
