package com.parko.admin.service.service;

import com.parko.admin.service.dto.TicketDetailResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminTicketRepository;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class TicketPaymentService {

    public static final String CASH_PROVIDER = "CASH";
    public static final String OPERATOR_REFERENCE_PREFIX = "ADMIN:";

    private final AdminTicketRepository ticketRepository;
    private final AdminTransactionRepository transactionRepository;
    private final TicketQueryService ticketQueryService;
    private final BigDecimal visitorEntryFee;
    private final BigDecimal registeredEntryFee;

    public TicketPaymentService(AdminTicketRepository ticketRepository,
                                AdminTransactionRepository transactionRepository,
                                TicketQueryService ticketQueryService,
                                @Value("${parking.visitor-entry-fee}") BigDecimal visitorEntryFee,
                                @Value("${parking.entry-fee}") BigDecimal registeredEntryFee) {
        this.ticketRepository = ticketRepository;
        this.transactionRepository = transactionRepository;
        this.ticketQueryService = ticketQueryService;
        this.visitorEntryFee = visitorEntryFee;
        this.registeredEntryFee = registeredEntryFee;
    }

    public TicketDetailResponse payInCash(UUID ticketId, String operatorUid) {
        TicketEntity ticket = ticketRepository.findForUpdateById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket no encontrado con id: " + ticketId));
        ParkingSessionEntity session = ticket.getParkingSession();

        if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT) {
            throw new ConflictException("El ticket no está pendiente de pago: " + ticket.getStatus());
        }
        boolean visitor = session.getSessionType() == SessionType.VISITOR;
        if (visitor && session.getStatus() != SessionStatus.ACTIVE) {
            throw new ConflictException("La sesión de estacionamiento no está activa: " + session.getStatus());
        }
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new ConflictException("La sesión de estacionamiento está cancelada");
        }

        LocalDateTime now = LocalDateTime.now();

        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaidAt(now);
        ticket.setUpdatedAt(now);
        ticketRepository.saveAndFlush(ticket);

        TransactionEntity payment = new TransactionEntity();
        payment.setId(UUID.randomUUID());
        payment.setParkingSession(session);
        payment.setType(TransactionType.CHARGE);
        payment.setStatus(TransactionStatus.COMPLETED);
        payment.setAmount(visitor ? visitorEntryFee : registeredEntryFee);
        payment.setPaymentProvider(CASH_PROVIDER);
        payment.setExternalRef(OPERATOR_REFERENCE_PREFIX + operatorUid);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        transactionRepository.saveAndFlush(payment);

        return ticketQueryService.getById(ticketId);
    }
}
