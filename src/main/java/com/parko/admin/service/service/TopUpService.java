package com.parko.admin.service.service;

import com.parko.admin.service.dto.CreateTopUpRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TopUpResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminBalanceAccountRepository;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.admin.service.repository.AdminUserRepository;
import com.parko.admin.service.specification.TopUpSpecifications;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TopUpService {

    public static final int PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String CASH_PROVIDER = TicketPaymentService.CASH_PROVIDER;
    public static final String OPERATOR_REFERENCE_PREFIX = TicketPaymentService.OPERATOR_REFERENCE_PREFIX;

    /** Campos por los que el panel puede ordenar (nombre del panel -> propiedad de la entidad). */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "transactionNumber", "id",
            "userName", "balanceAccount.user.fullName",
            "timestamp", "createdAt",
            "amount", "amount",
            "method", "paymentProvider",
            "status", "status");

    private final AdminTransactionRepository transactionRepository;
    private final AdminBalanceAccountRepository balanceAccountRepository;
    private final AdminUserRepository userRepository;
    private final Clock clock;
    private final BigDecimal maxAmount;

    public TopUpService(AdminTransactionRepository transactionRepository,
                        AdminBalanceAccountRepository balanceAccountRepository,
                        AdminUserRepository userRepository,
                        Clock clock,
                        @Value("${topups.max-amount}") BigDecimal maxAmount) {
        this.transactionRepository = transactionRepository;
        this.balanceAccountRepository = balanceAccountRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxAmount = maxAmount;
    }

    @Transactional(readOnly = true)
    public PageResponse<TopUpResponse> list(String search, int page, Integer size, String sort) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        int pageSize = resolveSize(size);

        Specification<TransactionEntity> spec = TopUpSpecifications.isTopUp();
        if (search != null && !search.isBlank()) {
            spec = spec.and(TopUpSpecifications.matches(search));
        }
        Page<TransactionEntity> result = transactionRepository.findAll(spec,
                PageRequest.of(page, pageSize, resolveSort(sort)));
        return PageResponse.from(result, this::toResponse);
    }

    /**
     * Acredita saldo a un usuario desde el panel: suma el monto a su cuenta y deja una transacción TOPUP
     * COMPLETED (método efectivo, con el operador que la hizo). Todo en la misma transacción de base de datos.
     */
    @Transactional
    public TopUpResponse create(CreateTopUpRequest request, String operatorUid) {
        BigDecimal amount = validateAmount(request);
        if (request.userId() == null) {
            throw new IllegalArgumentException("El usuario es requerido");
        }
        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + request.userId()));
        if (!user.isActive()) {
            throw new ConflictException("El usuario está dado de baja");
        }
        BalanceAccountEntity account = balanceAccountRepository.findForUpdateByUserId(user.getId())
                .orElseThrow(() -> new NoSuchElementException("El usuario no tiene cuenta de saldo"));

        LocalDateTime now = LocalDateTime.now(clock);
        account.setAmount(account.getAmount().add(amount));
        account.setUpdatedAt(now);
        balanceAccountRepository.save(account);

        TransactionEntity topUp = new TransactionEntity();
        topUp.setId(UUID.randomUUID());
        topUp.setBalanceAccount(account);
        topUp.setType(TransactionType.TOPUP);
        topUp.setStatus(TransactionStatus.COMPLETED);
        topUp.setAmount(amount);
        topUp.setPaymentProvider(CASH_PROVIDER);
        topUp.setExternalRef(OPERATOR_REFERENCE_PREFIX + operatorUid);
        topUp.setCreatedAt(now);
        topUp.setUpdatedAt(now);
        transactionRepository.save(topUp);

        return toResponse(topUp);
    }

    private BigDecimal validateAmount(CreateTopUpRequest request) {
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto no puede tener más de 2 decimales");
        }
        if (amount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("El monto excede el máximo permitido de " + maxAmount.toPlainString());
        }
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    private static int resolveSize(Integer size) {
        if (size == null) {
            return PAGE_SIZE;
        }
        if (size < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser mayor a cero");
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Formato "campo,asc|desc". Un campo no permitido se ignora y se usa el orden por defecto (más reciente primero). */
    private static Sort resolveSort(String sort) {
        Sort byDefault = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort == null || sort.isBlank()) {
            return byDefault;
        }
        String[] parts = sort.split(",", 2);
        String property = SORTABLE_FIELDS.get(parts[0].trim());
        if (property == null) {
            return byDefault;
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property).and(byDefault);
    }

    private TopUpResponse toResponse(TransactionEntity tx) {
        UserEntity user = tx.getBalanceAccount() != null ? tx.getBalanceAccount().getUser() : null;
        return new TopUpResponse(
                tx.getId(),
                "TX-" + tx.getId().toString().substring(0, 6).toUpperCase(),
                user != null ? user.getFullName() : null,
                user != null ? user.getEmail() : null,
                tx.getCreatedAt().atZone(clock.getZone()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                tx.getAmount(),
                CASH_PROVIDER.equals(tx.getPaymentProvider()) ? "CASH" : "MERCADO_PAGO",
                statusOf(tx.getStatus()));
    }

    private static String statusOf(TransactionStatus status) {
        return switch (status) {
            case COMPLETED -> "CREDITED";
            case PENDING -> "PENDING";
            case FAILED -> "REJECTED";
        };
    }
}
