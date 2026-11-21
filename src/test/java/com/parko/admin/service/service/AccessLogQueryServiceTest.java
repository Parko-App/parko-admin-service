package com.parko.admin.service.service;

import com.parko.admin.service.dto.AccessLogResponse;
import com.parko.admin.service.dto.PageResponse;
import com.parko.persistence.core.repository.AccessLogRepository;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionType;
import com.parko.persistence.core.model.entity.AccessLogEntity;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
class AccessLogQueryServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 10, 9, 30);

    @Mock
    private AccessLogRepository accessLogRepository;

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

    @Test
    void list_mapsLogsWithRequestedPage() {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setId(UUID.randomUUID());
        session.setSessionType(SessionType.REGISTERED);
        session.setPlateSnapshot("AB123CD");
        AccessLogEntity entity = log(session);
        when(accessLogRepository.findAllByOrderByOccurredAtDesc(any()))
                .thenAnswer(inv -> new PageImpl<>(List.of(entity), inv.getArgument(0), 1));

        PageResponse<AccessLogResponse> result = service().list(1);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(accessLogRepository).findAllByOrderByOccurredAtDesc(captor.capture());
        assertEquals(PageRequest.of(1, 20), captor.getValue());

        AccessLogResponse row = result.content().getFirst();
        assertEquals(entity.getId(), row.id());
        assertEquals(AccessEventType.ENTRY, row.eventType());
        assertEquals(AccessMethod.PLATE, row.accessMethod());
        assertEquals(AccessResult.AUTHORIZED, row.result());
        assertEquals(OCCURRED_AT, row.occurredAt());
        assertEquals(session.getId(), row.parkingSessionId());
        assertEquals(SessionType.REGISTERED, row.sessionType());
        assertEquals("AB123CD", row.plate());
    }

    @Test
    void list_prefersVisitorPlate() {
        ParkingSessionEntity session = new ParkingSessionEntity();
        session.setSessionType(SessionType.VISITOR);
        session.setPlateSnapshot("OLD");
        session.setVisitorPlate("ZZ999XX");
        when(accessLogRepository.findAllByOrderByOccurredAtDesc(any())).thenReturn(new PageImpl<>(List.of(log(session))));

        assertEquals("ZZ999XX", service().list(0).content().getFirst().plate());
    }

    @Test
    void list_withoutSession_returnsNullSessionFields() {
        when(accessLogRepository.findAllByOrderByOccurredAtDesc(any())).thenReturn(new PageImpl<>(List.of(log(null))));

        AccessLogResponse row = service().list(0).content().getFirst();

        assertNull(row.parkingSessionId());
        assertNull(row.sessionType());
        assertNull(row.plate());
    }

    @Test
    void list_negativePage_throws() {
        assertThrows(IllegalArgumentException.class, () -> service().list(-1));
        verifyNoInteractions(accessLogRepository);
    }
}
