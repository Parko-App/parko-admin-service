package com.parko.admin.service.repository;

import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.TransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AdminTransactionRepository
        extends JpaRepository<TransactionEntity, UUID>, JpaSpecificationExecutor<TransactionEntity> {

    @Override
    @EntityGraph(attributePaths = {"balanceAccount", "balanceAccount.user"})
    Page<TransactionEntity> findAll(Specification<TransactionEntity> spec, Pageable pageable);

    List<TransactionEntity> findByParkingSession_IdIn(Collection<UUID> parkingSessionIds);

    @Query("""
            select coalesce(sum(t.amount), 0) from TransactionEntity t
            where t.type = com.parko.domain.lib.model.TransactionType.CHARGE
              and t.status = com.parko.domain.lib.model.TransactionStatus.COMPLETED
              and t.createdAt >= :start and t.createdAt < :end
            """)
    BigDecimal sumCompletedCharges(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(t.amount), 0) from TransactionEntity t
            where t.type = com.parko.domain.lib.model.TransactionType.CHARGE
              and t.status = com.parko.domain.lib.model.TransactionStatus.COMPLETED
              and t.parkingSession.sessionType = com.parko.domain.lib.model.SessionType.VISITOR
              and t.createdAt >= :start and t.createdAt < :end
            """)
    BigDecimal sumCompletedVisitorCharges(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByTypeAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            TransactionType type, TransactionStatus status, LocalDateTime start, LocalDateTime end);

    @Query(value = """
            select to_char(t.created_at, 'YYYY-MM-DD') as day,
                   s.session_type as sessionType,
                   sum(t.amount) as total
            from transactions t
            join parking_sessions s on s.id = t.parking_session_id
            where t.type = 'CHARGE'
              and t.status = 'COMPLETED'
              and t.created_at >= :start
              and t.created_at < :end
            group by to_char(t.created_at, 'YYYY-MM-DD'), s.session_type
            """, nativeQuery = true)
    List<RevenueRow> sumChargesByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
