package com.parko.admin.service.service;

import com.parko.admin.service.dto.AccessLogResponse;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.repository.AdminAccessLogRepository;
import com.parko.admin.service.specification.AccessLogSpecifications;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.UserRole;
import com.parko.persistence.core.model.entity.AccessLogEntity;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AccessLogQueryService {

    public static final int PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    static final String VISITOR_NAME = "Visitante";
    static final String UNKNOWN_NAME = "Desconocido";

    /** Campos por los que el panel puede ordenar (nombre del panel -> propiedad de la entidad). */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "timestamp", "occurredAt",
            "occurredAt", "occurredAt",
            "movement", "eventType",
            "eventType", "eventType",
            "method", "accessMethod",
            "accessMethod", "accessMethod");

    private final AdminAccessLogRepository accessLogRepository;

    public AccessLogQueryService(AdminAccessLogRepository accessLogRepository) {
        this.accessLogRepository = accessLogRepository;
    }

    public PageResponse<AccessLogResponse> list(String search, AccessEventType movement, int page, Integer size, String sort) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        int pageSize = resolveSize(size);

        Specification<AccessLogEntity> spec = Specification.unrestricted();
        if (search != null && !search.isBlank()) {
            spec = spec.and(AccessLogSpecifications.plateContains(search));
        }
        if (movement != null) {
            spec = spec.and(AccessLogSpecifications.hasMovement(movement));
        }

        Page<AccessLogEntity> result = accessLogRepository.findAll(spec, PageRequest.of(page, pageSize, resolveSort(sort)));
        return PageResponse.from(result, this::toResponse);
    }

    private static int resolveSize(Integer size) {
        if (size == null) {
            return PAGE_SIZE;
        }
        if (size < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser mayor a cero");
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Formato "campo,asc|desc". Un campo no permitido se ignora y se usa el orden por defecto (más reciente primero). */
    private static Sort resolveSort(String sort) {
        Sort byDefault = Sort.by(Sort.Direction.DESC, "occurredAt");
        if (sort == null || sort.isBlank()) {
            return byDefault;
        }
        String[] parts = sort.split(",", 2);
        String property = SORTABLE_FIELDS.get(parts[0].trim());
        if (property == null) {
            return byDefault;
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        // desempate estable: a igual valor, lo más reciente primero
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "occurredAt"));
    }

    private AccessLogResponse toResponse(AccessLogEntity log) {
        ParkingSessionEntity session = log.getParkingSession();
        return new AccessLogResponse(
                log.getId(),
                log.getEventType(),
                log.getAccessMethod(),
                log.getResult(),
                log.getOccurredAt(),
                session != null ? session.getId() : null,
                session != null ? session.getSessionType() : null,
                session != null ? plateOf(session) : null,
                log.getEventType(),
                log.getOccurredAt(),
                methodOf(log.getAccessMethod()),
                userNameOf(session),
                userTypeOf(session)
        );
    }

    private String plateOf(ParkingSessionEntity session) {
        return session.getVisitorPlate() != null ? session.getVisitorPlate() : session.getPlateSnapshot();
    }

    /** Patente y llave de proximidad se leen solas (AUTOMATIC); el ticket lo presenta una persona (MANUAL). */
    private static String methodOf(AccessMethod method) {
        return method == AccessMethod.TICKET ? "MANUAL" : "AUTOMATIC";
    }

    private static String userNameOf(ParkingSessionEntity session) {
        if (session == null) {
            return UNKNOWN_NAME;
        }
        UserEntity user = userOf(session);
        if (session.getSessionType() == SessionType.VISITOR || user == null) {
            return VISITOR_NAME;
        }
        return user.getFullName();
    }

    private static String userTypeOf(ParkingSessionEntity session) {
        if (session == null || session.getSessionType() == SessionType.VISITOR) {
            return "VISITOR";
        }
        UserEntity user = userOf(session);
        return user != null && user.getRole() == UserRole.STAFF ? "STAFF" : "REGISTERED";
    }

    private static UserEntity userOf(ParkingSessionEntity session) {
        return session.getVehicle() != null ? session.getVehicle().getUser() : null;
    }
}
