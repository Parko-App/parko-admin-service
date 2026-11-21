package com.parko.admin.service.service;

import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TicketDetailResponse;
import com.parko.admin.service.dto.TicketSummaryResponse;
import com.parko.admin.service.repository.AdminBalanceAccountRepository;
import com.parko.admin.service.repository.AdminTicketRepository;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.admin.service.repository.AdminUserRepository;
import com.parko.admin.service.specification.TicketSpecifications;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TicketQueryService {

    public static final int PAGE_SIZE = 20;

    static final String METHOD_CASH = "CASH";
    static final String METHOD_MERCADO_PAGO = "MERCADO_PAGO";
    static final String METHOD_BALANCE = "BALANCE";

    /** Columnas por las que el panel puede ordenar el listado. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("ticketNumber", "issuedAt", "paidAt", "status");

    private final AdminTicketRepository ticketRepository;
    private final AdminTransactionRepository transactionRepository;
    private final AdminUserRepository userRepository;
    private final AdminBalanceAccountRepository balanceAccountRepository;
    private final BigDecimal visitorEntryFee;

    public TicketQueryService(AdminTicketRepository ticketRepository,
                              AdminTransactionRepository transactionRepository,
                              AdminUserRepository userRepository,
                              AdminBalanceAccountRepository balanceAccountRepository,
                              @Value("${parking.visitor-entry-fee}") BigDecimal visitorEntryFee) {
        this.ticketRepository = ticketRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.balanceAccountRepository = balanceAccountRepository;
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
        return list(from, to, status, page, null);
    }

    /** sort: "campo,asc|desc" con campo en ticketNumber, issuedAt, paidAt o status. Por defecto, el más reciente primero. */
    public PageResponse<TicketSummaryResponse> list(LocalDate from, LocalDate to, TicketStatus status, int page, String sort) {
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

        PageRequest pageable = PageRequest.of(page, PAGE_SIZE, resolveSort(sort));
        Page<TicketEntity> result = ticketRepository.findAll(spec, pageable);

        Map<UUID, TransactionEntity> paymentBySession = paymentsBySession(result.getContent().stream()
                .map(t -> t.getParkingSession().getId())
                .toList());
        Map<String, String> cashiers = cashierNames(paymentBySession.values());

        return PageResponse.from(result, ticket -> toSummary(ticket,
                paymentBySession.get(ticket.getParkingSession().getId()), cashiers));
    }

    /** Un campo no permitido se ignora y se usa el orden por defecto. A igual valor, lo más reciente primero. */
    private static Sort resolveSort(String sort) {
        Sort byDefault = Sort.by(Sort.Direction.DESC, "issuedAt");
        if (sort == null || sort.isBlank()) {
            return byDefault;
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(property)) {
            return byDefault;
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort requested = Sort.by(direction, property);
        return "issuedAt".equals(property) ? requested : requested.and(byDefault);
    }

    private TicketSummaryResponse toSummary(TicketEntity ticket, TransactionEntity payment, Map<String, String> cashiers) {
        ParkingSessionEntity session = ticket.getParkingSession();
        boolean paid = isCompleted(payment);
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getStatus(),
                ticket.getIssuedAt(),
                ticket.getPaidAt(),
                session.getSessionType(),
                plateOf(session),
                amountOf(session, payment),
                paid ? methodOf(payment.getPaymentProvider()) : null,
                paid ? cashiers.get(operatorUidOf(payment)) : null
        );
    }

    private static boolean isCompleted(TransactionEntity payment) {
        return payment != null && payment.getStatus() == TransactionStatus.COMPLETED;
    }

    private static String methodOf(String provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case TicketPaymentService.CASH_PROVIDER -> METHOD_CASH;
            case "MERCADO_PAGO" -> METHOD_MERCADO_PAGO;
            case "INTERNAL_BALANCE", "BALANCE" -> METHOD_BALANCE;
            default -> null;
        };
    }

    /** UID del administrador que cobró en efectivo ("ADMIN:<uid>"); null en cualquier otro caso. */
    private static String operatorUidOf(TransactionEntity payment) {
        String ref = payment.getExternalRef();
        boolean cash = TicketPaymentService.CASH_PROVIDER.equals(payment.getPaymentProvider());
        if (!cash || ref == null || !ref.startsWith(TicketPaymentService.OPERATOR_REFERENCE_PREFIX)) {
            return null;
        }
        return ref.substring(TicketPaymentService.OPERATOR_REFERENCE_PREFIX.length());
    }

    /** Nombre de los administradores que registraron cobros en efectivo, en una sola consulta (uid -> nombre). */
    private Map<String, String> cashierNames(Collection<TransactionEntity> payments) {
        Set<String> uids = new HashSet<>();
        for (TransactionEntity payment : payments) {
            String uid = isCompleted(payment) ? operatorUidOf(payment) : null;
            if (uid != null) {
                uids.add(uid);
            }
        }
        Map<String, String> names = new HashMap<>();
        if (uids.isEmpty()) {
            return names;
        }
        for (UserEntity user : userRepository.findByFirebaseUidIn(uids)) {
            names.put(user.getFirebaseUid(), user.getFullName());
        }
        return names;
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
                        owner.getEmail(),
                        balanceOf(owner)),
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

    private BigDecimal balanceOf(UserEntity owner) {
        return balanceAccountRepository.findByUser_Id(owner.getId())
                .map(BalanceAccountEntity::getAmount)
                .orElse(null);
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
