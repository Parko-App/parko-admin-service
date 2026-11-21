package com.parko.admin.service.repository;

import com.parko.persistence.core.model.entity.TicketEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AdminTicketRepository extends JpaRepository<TicketEntity, UUID>, JpaSpecificationExecutor<TicketEntity> {

    @EntityGraph(attributePaths = {"parkingSession", "parkingSession.vehicle", "parkingSession.vehicle.user"})
    Optional<TicketEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"parkingSession", "parkingSession.vehicle", "parkingSession.vehicle.user"})
    Optional<TicketEntity> findWithDetailsByTicketNumber(String ticketNumber);

    @Query("""
            select count(t) from TicketEntity t
            where t.status = com.parko.domain.lib.model.TicketStatus.PENDING_PAYMENT
              and t.parkingSession.sessionType = com.parko.domain.lib.model.SessionType.VISITOR
              and t.issuedAt >= :start and t.issuedAt < :end
            """)
    long countPendingVisitorTickets(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketEntity t where t.id = :id")
    Optional<TicketEntity> findForUpdateById(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"parkingSession", "parkingSession.vehicle", "parkingSession.vehicle.user"})
    Page<TicketEntity> findAll(Specification<TicketEntity> spec, Pageable pageable);
}
