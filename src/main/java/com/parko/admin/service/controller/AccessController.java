package com.parko.admin.service.controller;

import com.parko.admin.service.dto.AccessLogResponse;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.service.AccessLogQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/accesos")
public class AccessController {

    private final AccessLogQueryService accessLogQueryService;

    public AccessController(AccessLogQueryService accessLogQueryService) {
        this.accessLogQueryService = accessLogQueryService;
    }

    @GetMapping
    public PageResponse<AccessLogResponse> list(@RequestParam(defaultValue = "0") int page) {
        return accessLogQueryService.list(page);
    }
}
