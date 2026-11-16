package com.parko.admin.service.service;

import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TicketDetailResponse;
import com.parko.admin.service.dto.TicketSummaryResponse;
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
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketQueryServiceTest {

    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 11, 10, 9, 30);

    @Mock
    private AdminTicketRepository ticketRepository;

    @Mock
    private AdminTransactionRepository transactionRepository;

    private TicketQueryService service() {
        return new TicketQueryService(ticketRepository, transactionRepository, new BigDecimal("3000"));
    }


    @Test
    void getById_registeredTicket_returnsAllData() {
        TicketEntity ticket = registeredTicket();
        TransactionEntity charge = transaction(ticket.getParkingSession(), TransactionType.CHARGE,
                TransactionStatus.COMPLETED, "1500.00", ISSUED_AT.plusHours(1));
        when(ticketRepository.findWithDetailsById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(transactionRepository.findByParkingSession_IdIn(anyCollection())).thenReturn(List.of(charge));

        TicketDetailResponse response = service().getById(ticket.getId());

        assertEquals(ticket.getId(), response.id());
        assertEquals("T-0001", response.ticketNumber());
        assertEquals(TicketStatus.PAID, response.status());
        assertEquals("qr-data", response.qrData());
        assertEquals(ISSUED_AT, response.issuedAt());
        assertEquals(ISSUED_AT.plusHours(2), response.paidAt());
        assertEquals(SessionType.REGISTERED, response.session().type());
        assertEquals("AB123CD", response.session().plate());
        assertEquals("AB123CD", response.session().plateSnapshot());
        assertNull(response.session().visitorPlate());
        assertEquals("Toyota", response.vehicle().brand());
        assertEquals("Ana Pérez", response.owner().fullName());
        assertEquals("82116", response.owner().studentId());
        assertEquals(0, new BigDecimal("1500.00").compareTo(response.payment().amount()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(response.amount()));
        assertEquals(TransactionType.CHARGE, response.payment().type());
        assertEquals("BALANCE", response.payment().paymentProvider());
    }

    @Test
    void getById_visitorTicket_hasNoVehicleOwnerNorPayment() {
        TicketEntity ticket = visitorTicket();
        when(ticketRepository.findWithDetailsById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(transactionRepository.findByParkingSession_IdIn(anyCollection())).thenReturn(List.of());

        TicketDetailResponse response = service().getById(ticket.getId());

        assertEquals(SessionType.VISITOR, response.session().type());
        assertEquals("ZZ999XX", response.session().plate());
        assertEquals("ZZ999XX", response.session().visitorPlate());
        assertNull(response.vehicle());
        assertNull(response.owner());
        assertNull(response.payment());
        assertEquals(0, new BigDecimal("3000").compareTo(response.amount()));
    }

    @Test
    void getById_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(ticketRepository.findWithDetailsById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service().getById(id));
    }

    @Test
    void getByNumber_found_returnsDetail() {
        TicketEntity ticket = visitorTicket();
        when(ticketRepository.findWithDetailsByTicketNumber("T-0002")).thenReturn(Optional.of(ticket));
        when(transactionRepository.findByParkingSession_IdIn(anyCollection())).thenReturn(List.of());

        TicketDetailResponse response = service().getByNumber("T-0002");

        assertEquals("T-0002", response.ticketNumber());
    }

    @Test
    void getByNumber_notFound_throwsNoSuchElement() {
        when(ticketRepository.findWithDetailsByTicketNumber("NOPE")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service().getByNumber("NOPE"));
    }

    @Test
    void getById_prefersCompletedChargeAndIgnoresOtherTypes() {
        TicketEntity ticket = registeredTicket();
        ParkingSessionEntity session = ticket.getParkingSession();
        TransactionEntity failedLater = transaction(session, TransactionType.CHARGE, TransactionStatus.FAILED,
                "1500.00", ISSUED_AT.plusHours(5));
        TransactionEntity completedEarlier = transaction(session, TransactionType.CHARGE, TransactionStatus.COMPLETED,
                "1200.00", ISSUED_AT.plusHours(1));
        TransactionEntity refund = transaction(session, TransactionType.REFUND, TransactionStatus.COMPLETED,
                "999.00", ISSUED_AT.plusHours(9));
        when(ticketRepository.findWithDetailsById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(transactionRepository.findByParkingSession_IdIn(anyCollection()))
                .thenReturn(List.of(failedLater, completedEarlier, refund));

        TicketDetailResponse response = service().getById(ticket.getId());

        assertEquals(completedEarlier.getId(), response.payment().transactionId());
    }

    @Test
    void getById_sameStatus_picksMostRecentCharge() {
        TicketEntity ticket = registeredTicket();
        ParkingSessionEntity session = ticket.getParkingSession();
        TransactionEntity older = transaction(session, TransactionType.CHARGE, TransactionStatus.PENDING,
                "100.00", ISSUED_AT.plusHours(1));
        TransactionEntity newer = transaction(session, TransactionType.CHARGE, TransactionStatus.PENDING,
                "200.00", ISSUED_AT.plusHours(2));
        when(ticketRepository.findWithDetailsById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(transactionRepository.findByParkingSession_IdIn(anyCollection())).thenReturn(List.of(newer, older));

        TicketDetailResponse response = service().getById(ticket.getId());

        assertEquals(newer.getId(), response.payment().transactionId());
    }


    @Test
    @SuppressWarnings("unchecked")
    void list_withoutFilters_usesPageSize20SortedByIssuedAtDesc() {
        TicketEntity ticket = registeredTicket();
        TransactionEntity charge = transaction(ticket.getParkingSession(), TransactionType.CHARGE,
                TransactionStatus.COMPLETED, "1500.00", ISSUED_AT);
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(ticket), inv.getArgument(1, Pageable.class), 41));
        when(transactionRepository.findByParkingSession_IdIn(anyCollection())).thenReturn(List.of(charge));

        PageResponse<TicketSummaryResponse> response = service().list(null, null, null, 1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals("issuedAt: DESC", pageable.getValue().getSort().toString());

        assertEquals(1, response.page());
        assertEquals(20, response.size());
        assertEquals(41, response.totalElements());
        assertEquals(3, response.totalPages());
        assertEquals(1, response.content().size());
        TicketSummaryResponse row = response.content().get(0);
        assertEquals("T-0001", row.ticketNumber());
        assertEquals(SessionType.REGISTERED, row.sessionType());
        assertEquals("AB123CD", row.plate());
        assertEquals(0, new BigDecimal("1500.00").compareTo(row.amount()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_visitorWithoutTransaction_usesFixedVisitorFee() {
        TicketEntity ticket = visitorTicket();
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket), PageRequest.of(0, 20), 1));
        when(transactionRepository.findByParkingSession_IdIn(anyCollection())).thenReturn(List.of());

        PageResponse<TicketSummaryResponse> response = service().list(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), TicketStatus.PENDING_PAYMENT, 0);

        assertEquals("ZZ999XX", response.content().get(0).plate());
        assertEquals(0, new BigDecimal("3000").compareTo(response.content().get(0).amount()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_emptyResult_doesNotQueryTransactions() {
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<TicketSummaryResponse> response = service().list(null, null, null, 0);

        assertEquals(0, response.content().size());
        assertEquals(0, response.totalElements());
        verify(transactionRepository, never()).findByParkingSession_IdIn(anyCollection());
    }

    @Test
    void list_fromAfterTo_throwsIllegalArgument() {
        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate to = LocalDate.of(2026, 11, 1);

        assertThrows(IllegalArgumentException.class, () -> service().list(from, to, null, 0));
    }

    @Test
    void list_negativePage_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service().list(null, null, null, -1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_sameDayRange_isAccepted() {
        LocalDate day = LocalDate.of(2026, 11, 10);
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<TicketSummaryResponse> response = service().list(day, day, null, 0);

        assertNotNull(response);
    }


    private TicketEntity registeredTicket() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setStudentId("82116");
        user.setFullName("Ana Pérez");
        user.setEmail("ana@frc.utn.edu.ar");

        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(UUID.randomUUID());
        vehicle.setUser(user);
        vehicle.setPlate("AB123CD");
        vehicle.setBrand("Toyota");
        vehicle.setModel("Corolla");
        vehicle.setActive(true);

        ParkingSessionEntity session = session(SessionType.REGISTERED);
        session.setVehicle(vehicle);
        session.setPlateSnapshot("AB123CD");

        TicketEntity ticket = ticket(session, "T-0001");
        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaidAt(ISSUED_AT.plusHours(2));
        return ticket;
    }

    private TicketEntity visitorTicket() {
        ParkingSessionEntity session = session(SessionType.VISITOR);
        session.setVisitorPlate("ZZ999XX");

        TicketEntity ticket = ticket(session, "T-0002");
        ticket.setStatus(TicketStatus.PENDING_PAYMENT);
        return ticket;
    }

    private ParkingSessionEntity session(SessionType type) {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setSessionType(type);
        session.setStatus(SessionStatus.ACTIVE);
        session.setEntryAt(ISSUED_AT);
        return session;
    }

    private TicketEntity ticket(ParkingSessionEntity session, String number) {
        TicketEntity ticket = new TicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setParkingSession(session);
        ticket.setTicketNumber(number);
        ticket.setQrData("qr-data");
        ticket.setIssuedAt(ISSUED_AT);
        ticket.setCreatedAt(ISSUED_AT);
        ticket.setUpdatedAt(ISSUED_AT);
        return ticket;
    }

    private TransactionEntity transaction(ParkingSessionEntity session, TransactionType type,
                                          TransactionStatus status, String amount, LocalDateTime createdAt) {
        TransactionEntity tx = new TransactionEntity();
        tx.setId(UUID.randomUUID());
        tx.setParkingSession(session);
        tx.setType(type);
        tx.setStatus(status);
        tx.setAmount(new BigDecimal(amount));
        tx.setPaymentProvider("BALANCE");
        tx.setCreatedAt(createdAt);
        return tx;
    }
}
