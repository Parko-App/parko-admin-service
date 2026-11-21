package com.parko.admin.service.service;

import com.parko.admin.service.dto.AccessLogResponse;
import com.parko.admin.service.dto.PageResponse;
import com.parko.persistence.core.model.entity.AccessLogEntity;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.repository.AccessLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccessLogQueryService {

    public static final int PAGE_SIZE = 20;

    private final AccessLogRepository accessLogRepository;

    public AccessLogQueryService(AccessLogRepository accessLogRepository) {
        this.accessLogRepository = accessLogRepository;
    }

    public PageResponse<AccessLogResponse> list(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        return PageResponse.from(
                accessLogRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(page, PAGE_SIZE)),
                this::toResponse);
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
                session != null ? plateOf(session) : null
        );
    }

    private String plateOf(ParkingSessionEntity session) {
        return session.getVisitorPlate() != null ? session.getVisitorPlate() : session.getPlateSnapshot();
    }
}
