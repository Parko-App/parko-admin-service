package com.parko.admin.service.repository;

import com.parko.persistence.core.model.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AdminTransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByParkingSession_IdIn(Collection<UUID> parkingSessionIds);

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
