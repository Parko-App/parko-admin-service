package com.parko.admin.service.repository;

import com.parko.persistence.core.model.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AdminTransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByParkingSession_IdIn(Collection<UUID> parkingSessionIds);
}
