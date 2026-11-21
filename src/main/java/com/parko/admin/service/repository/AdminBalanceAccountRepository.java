package com.parko.admin.service.repository;

import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminBalanceAccountRepository extends JpaRepository<BalanceAccountEntity, UUID> {

    Optional<BalanceAccountEntity> findByUser_Id(UUID userId);

    /** Bloquea la cuenta para que dos cargas simultáneas no pisen el saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BalanceAccountEntity a where a.user.id = :userId")
    Optional<BalanceAccountEntity> findForUpdateByUserId(@Param("userId") UUID userId);
}
