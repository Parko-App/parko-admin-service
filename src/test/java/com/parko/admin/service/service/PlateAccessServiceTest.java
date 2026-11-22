package com.parko.admin.service.service;

import com.parko.admin.service.dto.PlateEntryResponse;
import com.parko.admin.service.dto.PlateExitResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.exception.ExternalServiceException;
import com.parko.admin.service.repository.AdminParkingSessionRepository;
import com.parko.admin.service.repository.AdminTicketRepository;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlateAccessServiceTest {

    @Mock
    private AccessServiceClient accessClient;
    @Mock
    private AdminParkingSessionRepository sessionRepository;
    @Mock
    private AdminTicketRepository ticketRepository;

    private PlateAccessService service;

    @BeforeEach
    void setUp() {
        service = new PlateAccessService(accessClient, sessionRepository, ticketRepository);
    }

    private static ParkingSessionEntity session(SessionType type) {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setSessionType(type);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    private static TicketEntity ticket(ParkingSessionEntity session, TicketStatus status) {
        TicketEntity ticket = new TicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setParkingSession(session);
        ticket.setTicketNumber("TCK-ABCD1234");
        ticket.setStatus(status);
        return ticket;
    }

    private static AccessServiceClient.Response authorized(AccessEventType type, String pdf) {
        return new AccessServiceClient.Response(AccessResult.AUTHORIZED, type, null, pdf);
    }

    // ---------------------------------------------------------------- ingreso

    @Test
    void enter_visitor_callsAccessServiceWithPlateAndReturnsTheTicket() {
        ParkingSessionEntity session = session(SessionType.VISITOR);
        TicketEntity ticket = ticket(session, TicketStatus.PENDING_PAYMENT);
        // sin sesión activa antes del pedido; con la sesión nueva después
        when(sessionRepository.findActiveByPlate("AB123CD")).thenReturn(List.of()).thenReturn(List.of(session));
        when(accessClient.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:uid-1"))
                .thenReturn(authorized(AccessEventType.ENTRY, "UERG"));
        when(ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId()))
                .thenReturn(Optional.of(ticket));

        PlateEntryResponse response = service.enter("ab-123 cd", "uid-1");

        assertEquals(ticket.getId(), response.ticketId());
        assertEquals("TCK-ABCD1234", response.ticketNumber());
        assertEquals(SessionType.VISITOR, response.sessionType());
        assertEquals(TicketStatus.PENDING_PAYMENT, response.ticketStatus());
        assertEquals("AB123CD", response.plate());
        assertEquals("Ingreso de visitante generado. Entregá el ticket para que pueda pagar.", response.message());
    }

    @Test
    void enter_registeredUser_isReportedAsRegisteredAndHasNoPdf() {
        ParkingSessionEntity session = session(SessionType.REGISTERED);
        TicketEntity ticket = ticket(session, TicketStatus.PAID);
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of()).thenReturn(List.of(session));
        when(accessClient.resolve(eq(AccessMethod.PLATE), eq("ABC123"), any(), anyString()))
                .thenReturn(authorized(AccessEventType.ENTRY, null));
        when(ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId()))
                .thenReturn(Optional.of(ticket));

        PlateEntryResponse response = service.enter("ABC123", "uid-1");

        assertEquals(SessionType.REGISTERED, response.sessionType());
        assertEquals("Ingreso de usuario registrado generado.", response.message());
    }

    @Test
    void enter_registeredUserWithoutBalance_warnsTheTicketIsPending() {
        ParkingSessionEntity session = session(SessionType.REGISTERED);
        TicketEntity ticket = ticket(session, TicketStatus.PENDING_PAYMENT);
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of()).thenReturn(List.of(session));
        when(accessClient.resolve(any(), anyString(), any(), anyString()))
                .thenReturn(authorized(AccessEventType.ENTRY, null));
        when(ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId()))
                .thenReturn(Optional.of(ticket));

        assertEquals("Ingreso de usuario registrado generado. El ticket quedó pendiente de pago (saldo insuficiente).",
                service.enter("ABC123", "uid-1").message());
    }

    @Test
    void enter_plateAlreadyInside_isRejectedWithoutCallingAccessService() {
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of(session(SessionType.REGISTERED)));

        ConflictException error = assertThrows(ConflictException.class, () -> service.enter("ABC123", "uid-1"));

        assertEquals("La patente ABC123 ya tiene un ingreso activo", error.getMessage());
        verifyNoInteractions(accessClient);
    }

    @Test
    void enter_invalidPlate_isRejectedBeforeAnyLookup() {
        for (String bad : new String[]{"", "   ", "AB12", "ABCD123", "A1B2C3", "AB 123 C"}) {
            assertThrows(IllegalArgumentException.class, () -> service.enter(bad, "uid-1"), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> service.enter(null, "uid-1"));
        verifyNoInteractions(accessClient, sessionRepository);
    }

    @Test
    void enter_acceptsBothPlateFormatsRegardlessOfCaseSpacesAndDashes() {
        assertEquals("ABC123", PlateAccessService.normalize("abc 123"));
        assertEquals("AB123CD", PlateAccessService.normalize(" ab-123-cd "));
    }

    @Test
    void enter_whenAccessServiceDenies_throwsConflictWithItsMessage() {
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of());
        when(accessClient.resolve(any(), anyString(), any(), anyString())).thenReturn(
                new AccessServiceClient.Response(AccessResult.DENIED, AccessEventType.ENTRY, "Sin cupo", null));

        ConflictException error = assertThrows(ConflictException.class, () -> service.enter("ABC123", "uid-1"));

        assertEquals("Sin cupo", error.getMessage());
    }

    @Test
    void enter_whenAccessServiceRegisteredAnExitInstead_isReportedAsConflict() {
        // carrera: entre la validación y el pedido, la patente entró por otro lado
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of());
        when(accessClient.resolve(any(), anyString(), any(), anyString())).thenReturn(authorized(AccessEventType.EXIT, null));

        ConflictException error = assertThrows(ConflictException.class, () -> service.enter("ABC123", "uid-1"));

        assertEquals("La patente ABC123 ya estaba adentro: se registró un egreso en lugar de un ingreso", error.getMessage());
    }

    @Test
    void enter_whenAccessServiceIsDown_propagatesTheExternalError() {
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of());
        when(accessClient.resolve(any(), anyString(), any(), anyString()))
                .thenThrow(new ExternalServiceException(AccessServiceClient.UNREACHABLE, null));

        assertThrows(ExternalServiceException.class, () -> service.enter("ABC123", "uid-1"));
    }

    // ----------------------------------------------------------------- egreso

    @Test
    void exit_paidTicket_callsAccessServiceWithTheSessionIdAsTicketMethod() {
        ParkingSessionEntity session = session(SessionType.VISITOR);
        TicketEntity ticket = ticket(session, TicketStatus.PAID);
        when(sessionRepository.findActiveByPlate("AB123CD")).thenReturn(List.of(session));
        when(ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId()))
                .thenReturn(Optional.of(ticket));
        when(accessClient.resolve(AccessMethod.TICKET, session.getId().toString(), null, "admin:uid-1"))
                .thenReturn(authorized(AccessEventType.EXIT, null));

        PlateExitResponse response = service.exit("ab123cd", "uid-1");

        assertEquals("TCK-ABCD1234", response.ticketNumber());
        assertEquals(SessionType.VISITOR, response.sessionType());
        assertEquals("AB123CD", response.plate());
        assertEquals("Egreso generado para AB123CD", response.message());
    }

    @Test
    void exit_unpaidTicket_isRejectedAndAccessServiceIsNotCalled() {
        ParkingSessionEntity session = session(SessionType.REGISTERED);
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of(session));
        when(ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId()))
                .thenReturn(Optional.of(ticket(session, TicketStatus.PENDING_PAYMENT)));

        ConflictException error = assertThrows(ConflictException.class, () -> service.exit("ABC123", "uid-1"));

        assertEquals("El ticket TCK-ABCD1234 no está pagado (pendiente de pago): no se puede generar el egreso",
                error.getMessage());
        verifyNoInteractions(accessClient);
    }

    @Test
    void exit_plateWithoutActiveSession_isNotFound() {
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of());

        NoSuchElementException error = assertThrows(NoSuchElementException.class, () -> service.exit("ABC123", "uid-1"));

        assertEquals("La patente ABC123 no tiene un ingreso activo", error.getMessage());
        verifyNoInteractions(accessClient);
    }

    @Test
    void exit_sessionWithoutTicket_isNotFound() {
        ParkingSessionEntity session = session(SessionType.VISITOR);
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of(session));
        when(ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.exit("ABC123", "uid-1"));
        verify(accessClient, never()).resolve(any(), any(), any(), any());
    }

    @Test
    void exit_whenAccessServiceDenies_throwsConflictWithItsMessage() {
        ParkingSessionEntity session = session(SessionType.VISITOR);
        when(sessionRepository.findActiveByPlate("ABC123")).thenReturn(List.of(session));
        when(ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId()))
                .thenReturn(Optional.of(ticket(session, TicketStatus.PAID)));
        when(accessClient.resolve(any(), anyString(), any(), anyString())).thenReturn(
                new AccessServiceClient.Response(AccessResult.DENIED, AccessEventType.EXIT, "Sesión no activa", null));

        ConflictException error = assertThrows(ConflictException.class, () -> service.exit("ABC123", "uid-1"));

        assertEquals("Sesión no activa", error.getMessage());
    }

    @Test
    void exit_invalidPlate_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.exit("XX", "uid-1"));
        verifyNoInteractions(accessClient, sessionRepository, ticketRepository);
    }
}
