package com.parko.admin.service.service;

import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TicketDetailResponse;
import com.parko.admin.service.dto.TicketSummaryResponse;
import com.parko.admin.service.repository.AdminTicketRepository;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.admin.service.specification.TicketSpecifications;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TicketQueryService {

    public static final int PAGE_SIZE = 20;

    private final AdminTicketRepository ticketRepository;
    private final AdminTransactionRepository transactionRepository;
    private final BigDecimal visitorEntryFee;

    public TicketQueryService(AdminTicketRepository ticketRepository,
                              AdminTransactionRepository transactionRepository,
                              @Value("${parking.visitor-entry-fee}") BigDecimal visitorEntryFee) {
        this.ticketRepository = ticketRepository;
        this.transactionRepository = transactionRepository;
        this.visitorEntryFee = visitorEntryFee;
    }

    public TicketDetailResponse getById(UUID id) {
        TicketEntity ticket = ticketRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NoSuchElementException("Ticket no encontrado con id: " + id));
        return toDetail(ticket);
    }

    public TicketDetailResponse getByNumber(String ticketNumber) {
        TicketEntity ticket = ticketRepository.findWithDetailsByTicketNumber(ticketNumber)
                .orElseThrow(() -> new NoSuchElementException("Ticket no encontrado con número: " + ticketNumber));
        return toDetail(ticket);
    }

    public PageResponse<TicketSummaryResponse> list(LocalDate from, LocalDate to, TicketStatus status, int page) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta");
        }

        Specification<TicketEntity> spec = Specification.unrestricted();
        if (from != null) {
            spec = spec.and(TicketSpecifications.issuedFrom(from.atStartOfDay()));
        }
        if (to != null) {
            spec = spec.and(TicketSpecifications.issuedBefore(to.plusDays(1).atStartOfDay()));
        }
        if (status != null) {
            spec = spec.and(TicketSpecifications.hasStatus(status));
        }

        PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "issuedAt"));
        Page<TicketEntity> result = ticketRepository.findAll(spec, pageable);

        Map<UUID, TransactionEntity> paymentBySession = paymentsBySession(result.getContent().stream()
                .map(t -> t.getParkingSession().getId())
                .toList());

        return PageResponse.from(result, ticket -> toSummary(ticket,
                paymentBySession.get(ticket.getParkingSession().getId())));
    }

    private TicketSummaryResponse toSummary(TicketEntity ticket, TransactionEntity payment) {
        ParkingSessionEntity session = ticket.getParkingSession();
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getStatus(),
                ticket.getIssuedAt(),
                ticket.getPaidAt(),
                session.getSessionType(),
                plateOf(session),
                amountOf(session, payment)
        );
    }

    private TicketDetailResponse toDetail(TicketEntity ticket) {
        ParkingSessionEntity session = ticket.getParkingSession();
        VehicleEntity vehicle = session.getVehicle();
        UserEntity owner = vehicle != null ? vehicle.getUser() : null;
        TransactionEntity payment = paymentsBySession(List.of(session.getId())).get(session.getId());

        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getStatus(),
                ticket.getQrData(),
                ticket.getIssuedAt(),
                ticket.getPaidAt(),
                amountOf(session, payment),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                new TicketDetailResponse.SessionInfo(
                        session.getId(),
                        session.getSessionType(),
                        session.getStatus(),
                        plateOf(session),
                        session.getPlateSnapshot(),
                        session.getVisitorPlate(),
                        session.getEntryAt(),
                        session.getExitAt()),
                vehicle == null ? null : new TicketDetailResponse.VehicleInfo(
                        vehicle.getId(),
                        vehicle.getPlate(),
                        vehicle.getBrand(),
                        vehicle.getModel(),
                        vehicle.isActive()),
                owner == null ? null : new TicketDetailResponse.OwnerInfo(
                        owner.getId(),
                        owner.getStudentId(),
                        owner.getFullName(),
                        owner.getEmail()),
                payment == null ? null : new TicketDetailResponse.PaymentInfo(
                        payment.getId(),
                        payment.getType(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getPaymentProvider(),
                        payment.getExternalRef(),
                        payment.getCreatedAt())
        );
    }

    private BigDecimal amountOf(ParkingSessionEntity session, TransactionEntity payment) {
        if (payment != null) {
            return payment.getAmount();
        }
        return session.getSessionType() == SessionType.VISITOR ? visitorEntryFee : null;
    }

    private String plateOf(ParkingSessionEntity session) {
        return session.getVisitorPlate() != null ? session.getVisitorPlate() : session.getPlateSnapshot();
    }

    private Map<UUID, TransactionEntity> paymentsBySession(Collection<UUID> sessionIds) {
        Map<UUID, TransactionEntity> result = new HashMap<>();
        if (sessionIds.isEmpty()) {
            return result;
        }
        Comparator<TransactionEntity> preferred = Comparator
                .comparing((TransactionEntity t) -> t.getStatus() == TransactionStatus.COMPLETED)
                .thenComparing(TransactionEntity::getCreatedAt);

        for (TransactionEntity tx : transactionRepository.findByParkingSession_IdIn(sessionIds)) {
            if (tx.getType() != TransactionType.CHARGE) {
                continue;
            }
            result.merge(tx.getParkingSession().getId(), tx,
                    (a, b) -> preferred.compare(a, b) >= 0 ? a : b);
        }
        return result;
    }
}
