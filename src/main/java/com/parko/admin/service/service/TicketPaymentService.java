package com.parko.admin.service.service;

import com.parko.admin.service.dto.TicketDetailResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminTicketRepository;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.domain.lib.model.SessionStatus;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class TicketPaymentService {

    public static final String CASH_PROVIDER = "CASH";
    public static final String OPERATOR_REFERENCE_PREFIX = "ADMIN:";

    private final AdminTicketRepository ticketRepository;
    private final AdminTransactionRepository transactionRepository;
    private final TicketQueryService ticketQueryService;
    private final BigDecimal visitorEntryFee;
    private final BigDecimal registeredEntryFee;

    public TicketPaymentService(AdminTicketRepository ticketRepository,
                                AdminTransactionRepository transactionRepository,
                                TicketQueryService ticketQueryService,
                                @Value("${parking.visitor-entry-fee}") BigDecimal visitorEntryFee,
                                @Value("${parking.entry-fee}") BigDecimal registeredEntryFee) {
        this.ticketRepository = ticketRepository;
        this.transactionRepository = transactionRepository;
        this.ticketQueryService = ticketQueryService;
        this.visitorEntryFee = visitorEntryFee;
        this.registeredEntryFee = registeredEntryFee;
    }

    public TicketDetailResponse payInCash(UUID ticketId, String operatorUid) {
        return payInCash(ticketId, null, operatorUid);
    }

    /**
     * Cobra el ticket en efectivo. Si el ingreso ya dejó una transacción CHARGE pendiente (por ejemplo, un usuario
     * registrado sin saldo suficiente), esa misma transacción pasa a CASH / COMPLETED: no se crea otra. El monto
     * informado, si viene, tiene que coincidir con el de esa transacción. Si no hay transacción previa (visitantes, o
     * un ingreso cuyo cobro falló), se crea una con la tarifa configurada.
     */
    public TicketDetailResponse payInCash(UUID ticketId, BigDecimal amount, String operatorUid) {
        TicketEntity ticket = ticketRepository.findForUpdateById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket no encontrado con id: " + ticketId));
        ParkingSessionEntity session = ticket.getParkingSession();

        if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT) {
            throw new ConflictException("El ticket no está pendiente de pago: " + ticket.getStatus());
        }
        boolean visitor = session.getSessionType() == SessionType.VISITOR;
        if (visitor && session.getStatus() != SessionStatus.ACTIVE) {
            throw new ConflictException("La sesión de estacionamiento no está activa: " + session.getStatus());
        }
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new ConflictException("La sesión de estacionamiento está cancelada");
        }

        Optional<TransactionEntity> pending = pendingCharge(session);
        BigDecimal total = pending.map(TransactionEntity::getAmount).orElse(visitor ? visitorEntryFee : registeredEntryFee);
        if (amount != null && amount.compareTo(total) != 0) {
            throw new IllegalArgumentException(
                    "El monto ingresado no coincide con el total a cobrar ($" + total.stripTrailingZeros().toPlainString() + ")");
        }

        LocalDateTime now = LocalDateTime.now();

        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaidAt(now);
        ticket.setUpdatedAt(now);
        ticketRepository.saveAndFlush(ticket);

        TransactionEntity payment = pending.orElseGet(() -> {
            TransactionEntity created = new TransactionEntity();
            created.setId(UUID.randomUUID());
            created.setParkingSession(session);
            created.setType(TransactionType.CHARGE);
            created.setAmount(total);
            created.setCreatedAt(now);
            return created;
        });
        payment.setStatus(TransactionStatus.COMPLETED);
        payment.setPaymentProvider(CASH_PROVIDER);
        payment.setExternalRef(OPERATOR_REFERENCE_PREFIX + operatorUid);
        payment.setUpdatedAt(now);
        transactionRepository.saveAndFlush(payment);

        return ticketQueryService.getById(ticketId);
    }

    /** La transacción de cobro pendiente de la sesión (la más reciente, si hubiera varias). */
    private Optional<TransactionEntity> pendingCharge(ParkingSessionEntity session) {
        return transactionRepository.findByParkingSession_IdIn(List.of(session.getId())).stream()
                .filter(tx -> tx.getType() == TransactionType.CHARGE && tx.getStatus() == TransactionStatus.PENDING)
                .max(Comparator.comparing(TransactionEntity::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    }
}
