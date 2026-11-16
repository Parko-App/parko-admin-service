package com.parko.admin.service.repository;

import com.parko.persistence.core.model.entity.VehicleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminVehicleRepository extends JpaRepository<VehicleEntity, UUID>, JpaSpecificationExecutor<VehicleEntity> {

    @Override
    @EntityGraph(attributePaths = {"user"})
    Page<VehicleEntity> findAll(Specification<VehicleEntity> spec, Pageable pageable);

    Optional<VehicleEntity> findByPlate(String plate);

    long countByUser_IdAndActiveTrue(UUID userId);

    List<VehicleEntity> findByUser_IdOrderByCreatedAtDesc(UUID userId);
}
