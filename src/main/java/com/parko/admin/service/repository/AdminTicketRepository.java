package com.parko.admin.service.repository;

import com.parko.persistence.core.model.entity.TicketEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AdminTicketRepository extends JpaRepository<TicketEntity, UUID>, JpaSpecificationExecutor<TicketEntity> {

    @EntityGraph(attributePaths = {"parkingSession", "parkingSession.vehicle", "parkingSession.vehicle.user"})
    Optional<TicketEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"parkingSession", "parkingSession.vehicle", "parkingSession.vehicle.user"})
    Optional<TicketEntity> findWithDetailsByTicketNumber(String ticketNumber);

    @Override
    @EntityGraph(attributePaths = {"parkingSession", "parkingSession.vehicle", "parkingSession.vehicle.user"})
    Page<TicketEntity> findAll(Specification<TicketEntity> spec, Pageable pageable);
}
