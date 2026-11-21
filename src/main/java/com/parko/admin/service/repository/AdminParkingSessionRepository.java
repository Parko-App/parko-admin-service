package com.parko.admin.service.repository;

import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.SessionType;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AdminParkingSessionRepository extends JpaRepository<ParkingSessionEntity, UUID> {

    @Query("""
            select s from ParkingSessionEntity s
            where s.status <> com.parko.domain.lib.model.SessionStatus.CANCELLED
              and s.entryAt < :end
              and (s.exitAt is null or s.exitAt > :start)
            """)
    List<ParkingSessionEntity> findOverlapping(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByStatus(SessionStatus status);

    long countByStatusAndSessionType(SessionStatus status, SessionType sessionType);

    @Query("""
            select count(s) from ParkingSessionEntity s
            where s.sessionType = :type
              and s.status <> com.parko.domain.lib.model.SessionStatus.CANCELLED
              and s.entryAt >= :start and s.entryAt < :end
            """)
    long countEntered(@Param("type") SessionType type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select count(distinct s.visitorPlate) from ParkingSessionEntity s
            where s.sessionType = com.parko.domain.lib.model.SessionType.VISITOR
              and s.visitorPlate is not null
              and s.status <> com.parko.domain.lib.model.SessionStatus.CANCELLED
              and s.entryAt >= :start and s.entryAt < :end
            """)
    long countDistinctVisitorPlates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
