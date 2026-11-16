package com.parko.admin.service.controller;

import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TicketDetailResponse;
import com.parko.admin.service.dto.TicketSummaryResponse;
import com.parko.admin.service.service.TicketPaymentService;
import com.parko.admin.service.service.TicketQueryService;
import com.parko.domain.lib.model.TicketStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tickets")
public class TicketController {

    private final TicketQueryService ticketQueryService;
    private final TicketPaymentService ticketPaymentService;

    public TicketController(TicketQueryService ticketQueryService, TicketPaymentService ticketPaymentService) {
        this.ticketQueryService = ticketQueryService;
        this.ticketPaymentService = ticketPaymentService;
    }

    @GetMapping
    public PageResponse<TicketSummaryResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") int page) {
        return ticketQueryService.list(from, to, status, page);
    }

    @GetMapping("/{id}")
    public TicketDetailResponse getById(@PathVariable UUID id) {
        return ticketQueryService.getById(id);
    }

    @GetMapping("/number/{ticketNumber}")
    public TicketDetailResponse getByNumber(@PathVariable String ticketNumber) {
        return ticketQueryService.getByNumber(ticketNumber);
    }

    @PostMapping("/{id}/pay")
    public TicketDetailResponse payInCash(@PathVariable UUID id, Authentication authentication) {
        return ticketPaymentService.payInCash(id, authentication.getName());
    }
}
