package com.parko.admin.service.service;

import com.parko.admin.service.dto.AccessLogResponse;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.repository.AdminAccessLogRepository;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.UserRole;
import com.parko.persistence.core.model.entity.AccessLogEntity;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AccessLogQueryServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 10, 9, 30);

    @Mock
    private AdminAccessLogRepository accessLogRepository;

    private AccessLogQueryService service() {
        return new AccessLogQueryService(accessLogRepository);
    }

    private static AccessLogEntity log(ParkingSessionEntity session) {
        AccessLogEntity log = new AccessLogEntity();
        log.setId(UUID.randomUUID());
        log.setParkingSession(session);
        log.setEventType(AccessEventType.ENTRY);
        log.setAccessMethod(AccessMethod.PLATE);
        log.setResult(AccessResult.AUTHORIZED);
        log.setOccurredAt(OCCURRED_AT);
        return log;
    }

    private static ParkingSessionEntity registeredSession(UserRole role) {
        UserEntity user = new UserEntity();
        user.setFullName("Lucas Fernández");
        user.setRole(role);
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setUser(user);
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setSessionType(SessionType.REGISTERED);
        session.setPlateSnapshot("AB123CD");
        session.setVehicle(vehicle);
        return session;
    }

    private void returning(AccessLogEntity... entities) {
        when(accessLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(entities), inv.getArgument(1), entities.length));
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(accessLogRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void list_mapsRegisteredUserLog() {
        ParkingSessionEntity session = registeredSession(UserRole.STUDENT);
        AccessLogEntity entity = log(session);
        returning(entity);

        PageResponse<AccessLogResponse> result = service().list(null, null, 1, null, null);

        Pageable pageable = capturedPageable();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());

        AccessLogResponse row = result.content().getFirst();
        assertEquals(entity.getId(), row.id());
        assertEquals(AccessEventType.ENTRY, row.eventType());
        assertEquals(AccessMethod.PLATE, row.accessMethod());
        assertEquals(AccessResult.AUTHORIZED, row.result());
        assertEquals(OCCURRED_AT, row.occurredAt());
        assertEquals(session.getId(), row.parkingSessionId());
        assertEquals(SessionType.REGISTERED, row.sessionType());
        assertEquals("AB123CD", row.plate());
        // campos que consume el panel
        assertEquals(AccessEventType.ENTRY, row.movement());
        assertEquals(OCCURRED_AT, row.timestamp());
        assertEquals("AUTOMATIC", row.method());
        assertEquals("Lucas Fernández", row.userName());
        assertEquals("REGISTERED", row.userType());
    }

    @Test
    void list_staffUser_isStaffType() {
        returning(log(registeredSession(UserRole.STAFF)));

        assertEquals("STAFF", service().list(null, null, 0, null, null).content().getFirst().userType());
    }

    @Test
    void list_ticketAccess_isManual() {
        AccessLogEntity entity = log(registeredSession(UserRole.STUDENT));
        entity.setAccessMethod(AccessMethod.TICKET);
        returning(entity);

        assertEquals("MANUAL", service().list(null, null, 0, null, null).content().getFirst().method());
    }

    @Test
    void list_prefersVisitorPlate_andNamesVisitor() {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setSessionType(SessionType.VISITOR);
        session.setPlateSnapshot("OLD");
        session.setVisitorPlate("ZZ999XX");
        returning(log(session));

        AccessLogResponse row = service().list(null, null, 0, null, null).content().getFirst();

        assertEquals("ZZ999XX", row.plate());
        assertEquals("Visitante", row.userName());
        assertEquals("VISITOR", row.userType());
    }

    @Test
    void list_withoutSession_returnsNullSessionFields() {
        returning(log(null));

        AccessLogResponse row = service().list(null, null, 0, null, null).content().getFirst();

        assertNull(row.parkingSessionId());
        assertNull(row.sessionType());
        assertNull(row.plate());
        assertEquals("Desconocido", row.userName());
        assertEquals("VISITOR", row.userType());
    }

    @Test
    void list_defaultSort_isMostRecentFirst() {
        returning();

        service().list(null, null, 0, null, null);

        assertEquals(Sort.by(Sort.Direction.DESC, "occurredAt"), capturedPageable().getSort());
    }

    @Test
    void list_sortByWhitelistedField_mapsToEntityProperty() {
        returning();

        service().list(null, null, 0, null, "movement,asc");

        Sort sort = capturedPageable().getSort();
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("eventType").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("occurredAt").getDirection());
    }

    @Test
    void list_sortByUnknownField_fallsBackToDefault() {
        returning();

        service().list(null, null, 0, null, "password,asc");

        assertEquals(Sort.by(Sort.Direction.DESC, "occurredAt"), capturedPageable().getSort());
    }

    @Test
    void list_sizeIsCapped() {
        returning();

        service().list(null, null, 0, 5000, null);

        assertEquals(AccessLogQueryService.MAX_PAGE_SIZE, capturedPageable().getPageSize());
    }

    @Test
    void list_customSize_isUsed() {
        returning();

        service().list(null, null, 0, 50, null);

        assertEquals(50, capturedPageable().getPageSize());
    }

    @Test
    void list_zeroSize_throws() {
        assertThrows(IllegalArgumentException.class, () -> service().list(null, null, 0, 0, null));
        verifyNoInteractions(accessLogRepository);
    }

    @Test
    void list_negativePage_throws() {
        assertThrows(IllegalArgumentException.class, () -> service().list(null, null, -1, null, null));
        verifyNoInteractions(accessLogRepository);
    }
}
