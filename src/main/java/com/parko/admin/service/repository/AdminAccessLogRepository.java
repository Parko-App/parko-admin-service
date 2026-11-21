package com.parko.admin.service.repository;

import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessResult;
import com.parko.persistence.core.model.entity.AccessLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AdminAccessLogRepository
        extends JpaRepository<AccessLogEntity, UUID>, JpaSpecificationExecutor<AccessLogEntity> {

    long countByEventTypeAndResultAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            AccessEventType eventType, AccessResult result, LocalDateTime start, LocalDateTime end);

    @Override
    @EntityGraph(attributePaths = {"parkingSession", "parkingSession.vehicle", "parkingSession.vehicle.user"})
    Page<AccessLogEntity> findAll(Specification<AccessLogEntity> spec, Pageable pageable);

    @Query(value = """
            select to_char(l.occurred_at, 'YYYY-MM-DD') as day,
                   l.event_type as eventType,
                   s.session_type as sessionType,
                   count(*) as total
            from access_log l
            join parking_sessions s on s.id = l.parking_session_id
            where l.result = 'AUTHORIZED'
              and l.occurred_at >= :start
              and l.occurred_at < :end
            group by to_char(l.occurred_at, 'YYYY-MM-DD'), l.event_type, s.session_type
            """, nativeQuery = true)
    List<AccessRow> countByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
