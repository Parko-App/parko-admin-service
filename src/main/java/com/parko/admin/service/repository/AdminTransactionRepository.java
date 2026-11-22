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

    /**
     * Recaudación del período, con la misma definición del reporte de recaudación (ver sumRevenueByDay):
     * cargas de saldo completadas (efectivo o Mercado Pago) más cobros de tickets en efectivo completados.
     * No incluye los cobros con saldo interno, que ya se contaron al cargarse el saldo.
     */
    @Query("""
            select coalesce(sum(t.amount), 0) from TransactionEntity t
            where t.status = com.parko.domain.lib.model.TransactionStatus.COMPLETED
              and (t.type = com.parko.domain.lib.model.TransactionType.TOPUP
                   or (t.type = com.parko.domain.lib.model.TransactionType.CHARGE and t.paymentProvider = 'CASH'))
              and t.updatedAt >= :start and t.updatedAt < :end
            """)
    BigDecimal sumRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

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

    /**
     * Recaudación por día: lo que efectivamente ingresó a Parko.
     * - TOPUP completados: cargas de saldo de usuarios, en efectivo o por Mercado Pago.
     * - CHARGE completados cuyo proveedor es CASH: cobros de tickets en efectivo.
     * No incluye los cobros con saldo interno (INTERNAL_BALANCE): ese dinero ya se contó al cargarse el saldo.
     * El día es el de la acreditación (updated_at): una carga por Mercado Pago queda pendiente hasta que se paga.
     */
    @Query(value = """
            select to_char(t.updated_at, 'YYYY-MM-DD') as day,
                   case when t.type = 'TOPUP' then 'TOPUP' else 'CASH' end as source,
                   sum(t.amount) as total
            from transactions t
            where t.status = 'COMPLETED'
              and (t.type = 'TOPUP' or (t.type = 'CHARGE' and t.payment_provider = 'CASH'))
              and t.updated_at >= :start
              and t.updated_at < :end
            group by to_char(t.updated_at, 'YYYY-MM-DD'), case when t.type = 'TOPUP' then 'TOPUP' else 'CASH' end
            """, nativeQuery = true)
    List<RevenueRow> sumRevenueByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
