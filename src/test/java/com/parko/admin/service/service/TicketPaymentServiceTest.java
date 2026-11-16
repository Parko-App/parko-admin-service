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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketPaymentServiceTest {

    @Mock
    private AdminTicketRepository ticketRepository;

    @Mock
    private AdminTransactionRepository transactionRepository;

    @Mock
    private TicketQueryService ticketQueryService;

    private TicketPaymentService service;

    @BeforeEach
    void setUp() {
        service = new TicketPaymentService(ticketRepository, transactionRepository, ticketQueryService,
                new BigDecimal("3000"), new BigDecimal("1500"));
    }

    private TicketEntity ticket(SessionType type, SessionStatus sessionStatus, TicketStatus ticketStatus) {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setSessionType(type);
        session.setStatus(sessionStatus);
        session.setVisitorPlate("ZZ999XX");

        TicketEntity ticket = new TicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setParkingSession(session);
        ticket.setTicketNumber("TCK-ABCD1234");
        ticket.setStatus(ticketStatus);
        ticket.setIssuedAt(LocalDateTime.of(2026, 11, 10, 9, 30));
        return ticket;
    }

    @Test
    void payInCash_pendingVisitorTicket_marksPaidAndRecordsCashCharge() {
        TicketEntity ticket = ticket(SessionType.VISITOR, SessionStatus.ACTIVE, TicketStatus.PENDING_PAYMENT);
        TicketDetailResponse detail = new TicketDetailResponse(ticket.getId(), "TCK-ABCD1234", TicketStatus.PAID, "qr",
                null, null, new BigDecimal("3000"), null, null, null, null, null, null);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(ticketQueryService.getById(ticket.getId())).thenReturn(detail);

        TicketDetailResponse response = service.payInCash(ticket.getId(), "admin-uid");

        assertSame(detail, response);
        assertEquals(TicketStatus.PAID, ticket.getStatus());
        assertNotNull(ticket.getPaidAt());
        assertNotNull(ticket.getUpdatedAt());
        verify(ticketRepository).saveAndFlush(ticket);

        ArgumentCaptor<TransactionEntity> payment = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).saveAndFlush(payment.capture());
        TransactionEntity saved = payment.getValue();
        assertNotNull(saved.getId());
        assertSame(ticket.getParkingSession(), saved.getParkingSession());
        assertNull(saved.getBalanceAccount());
        assertEquals(TransactionType.CHARGE, saved.getType());
        assertEquals(TransactionStatus.COMPLETED, saved.getStatus());
        assertEquals(0, new BigDecimal("3000").compareTo(saved.getAmount()));
        assertEquals("CASH", saved.getPaymentProvider());
        assertEquals("ADMIN:admin-uid", saved.getExternalRef());
        assertEquals(ticket.getPaidAt(), saved.getCreatedAt());
    }

    @Test
    void payInCash_doesNotCloseTicketNorSession() {
        TicketEntity ticket = ticket(SessionType.VISITOR, SessionStatus.ACTIVE, TicketStatus.PENDING_PAYMENT);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));

        service.payInCash(ticket.getId(), "admin-uid");

        assertEquals(TicketStatus.PAID, ticket.getStatus());
        assertEquals(SessionStatus.ACTIVE, ticket.getParkingSession().getStatus());
        assertNull(ticket.getParkingSession().getExitAt());
    }

    @Test
    void payInCash_unknownTicket_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(ticketRepository.findForUpdateById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.payInCash(id, "admin-uid"));
    }

    @Test
    void payInCash_registeredUserTicket_chargesRegisteredFee() {
        TicketEntity ticket = ticket(SessionType.REGISTERED, SessionStatus.ACTIVE, TicketStatus.PENDING_PAYMENT);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));

        service.payInCash(ticket.getId(), "admin-uid");

        assertEquals(TicketStatus.PAID, ticket.getStatus());
        ArgumentCaptor<TransactionEntity> payment = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).saveAndFlush(payment.capture());
        assertEquals(0, new BigDecimal("1500").compareTo(payment.getValue().getAmount()));
        assertEquals("CASH", payment.getValue().getPaymentProvider());
        assertEquals(TransactionStatus.COMPLETED, payment.getValue().getStatus());
    }

    @Test
    void payInCash_registeredUserTicketAfterExit_isAccepted() {
        TicketEntity ticket = ticket(SessionType.REGISTERED, SessionStatus.COMPLETED, TicketStatus.PENDING_PAYMENT);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));

        service.payInCash(ticket.getId(), "admin-uid");

        assertEquals(TicketStatus.PAID, ticket.getStatus());
        assertEquals(SessionStatus.COMPLETED, ticket.getParkingSession().getStatus());
        verify(transactionRepository).saveAndFlush(any());
    }

    @Test
    void payInCash_registeredUserCancelledSession_throwsConflict() {
        TicketEntity ticket = ticket(SessionType.REGISTERED, SessionStatus.CANCELLED, TicketStatus.PENDING_PAYMENT);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service.payInCash(ticket.getId(), "admin-uid"));
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void payInCash_alreadyPaid_throwsConflict() {
        TicketEntity ticket = ticket(SessionType.VISITOR, SessionStatus.ACTIVE, TicketStatus.PAID);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service.payInCash(ticket.getId(), "admin-uid"));
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void payInCash_usedTicket_throwsConflict() {
        TicketEntity ticket = ticket(SessionType.VISITOR, SessionStatus.COMPLETED, TicketStatus.USED);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service.payInCash(ticket.getId(), "admin-uid"));
    }

    @Test
    void payInCash_cancelledTicket_throwsConflict() {
        TicketEntity ticket = ticket(SessionType.VISITOR, SessionStatus.CANCELLED, TicketStatus.CANCELLED);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service.payInCash(ticket.getId(), "admin-uid"));
    }

    @Test
    void payInCash_pendingTicketOfInactiveSession_throwsConflict() {
        TicketEntity ticket = ticket(SessionType.VISITOR, SessionStatus.COMPLETED, TicketStatus.PENDING_PAYMENT);
        when(ticketRepository.findForUpdateById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service.payInCash(ticket.getId(), "admin-uid"));
        verify(ticketRepository, never()).saveAndFlush(any());
    }
}
