package com.parko.admin.service.controller;

import com.parko.admin.service.dto.AccessReport;
import com.parko.admin.service.dto.OccupancyReport;
import com.parko.admin.service.dto.RevenueReport;
import com.parko.admin.service.service.AccessReportService;
import com.parko.admin.service.service.OccupancyReportService;
import com.parko.admin.service.service.RevenueReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportController {

    private final OccupancyReportService occupancyReportService;
    private final RevenueReportService revenueReportService;
    private final AccessReportService accessReportService;

    public ReportController(OccupancyReportService occupancyReportService,
                            RevenueReportService revenueReportService,
                            AccessReportService accessReportService) {
        this.occupancyReportService = occupancyReportService;
        this.revenueReportService = revenueReportService;
        this.accessReportService = accessReportService;
    }

    @GetMapping("/occupancy")
    public OccupancyReport occupancy(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return occupancyReportService.report(from, to);
    }

    @GetMapping("/revenue")
    public RevenueReport revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return revenueReportService.report(from, to);
    }

    @GetMapping("/access")
    public AccessReport access(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accessReportService.report(from, to);
    }
}
