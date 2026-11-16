package com.parko.admin.service.repository;

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
}
