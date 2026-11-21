package com.parko.admin.service.controller;

import com.parko.admin.service.dto.DashboardSummary;
import com.parko.admin.service.dto.RecentAccessResponse;
import com.parko.admin.service.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/recent-access")
    public List<RecentAccessResponse> recentAccess(@RequestParam(required = false) Integer limit) {
        return dashboardService.recentAccess(limit);
    }
}
