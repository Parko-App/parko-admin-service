package com.parko.admin.service.service;

import com.parko.admin.service.dto.CreateTopUpRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.TopUpResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminBalanceAccountRepository;
import com.parko.admin.service.repository.AdminTransactionRepository;
import com.parko.admin.service.repository.AdminUserRepository;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class TopUpServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 30);

    @Mock
    private AdminTransactionRepository transactionRepository;
    @Mock
    private AdminBalanceAccountRepository balanceAccountRepository;
    @Mock
    private AdminUserRepository userRepository;

    private TopUpService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new TopUpService(transactionRepository, balanceAccountRepository, userRepository, clock,
                new BigDecimal("100000"));
    }

    private static UserEntity user(boolean active) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setFullName("Lucas Fernández");
        user.setEmail("lucas@frc.utn.edu.ar");
        user.setActive(active);
        return user;
    }

    private static BalanceAccountEntity account(UserEntity user, String amount) {
        BalanceAccountEntity account = new BalanceAccountEntity();
        account.setId(UUID.randomUUID());
        account.setUser(user);
        account.setAmount(new BigDecimal(amount));
        return account;
    }

    // ---- create ----

    @Test
    void create_addsAmountToBalanceAndRecordsCompletedCashTopUp() {
        UserEntity user = user(true);
        BalanceAccountEntity account = account(user, "500.00");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(balanceAccountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));

        TopUpResponse response = service.create(new CreateTopUpRequest(user.getId(), new BigDecimal("1500")), "admin-uid");

        assertEquals(new BigDecimal("2000.00"), account.getAmount());
        assertEquals(NOW, account.getUpdatedAt());
        verify(balanceAccountRepository).save(account);

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        TransactionEntity tx = captor.getValue();
        assertEquals(TransactionType.TOPUP, tx.getType());
        assertEquals(TransactionStatus.COMPLETED, tx.getStatus());
        assertEquals(new BigDecimal("1500.00"), tx.getAmount());
        assertEquals("CASH", tx.getPaymentProvider());
        assertEquals("ADMIN:admin-uid", tx.getExternalRef());
        assertEquals(account, tx.getBalanceAccount());
        assertEquals(NOW, tx.getCreatedAt());

        assertEquals("CASH", response.method());
        assertEquals("CREDITED", response.status());
        assertEquals("Lucas Fernández", response.userName());
        assertEquals("2026-11-21T10:30:00-03:00", response.timestamp());
        assertEquals("TX-" + tx.getId().toString().substring(0, 6).toUpperCase(), response.transactionNumber());
    }

    @Test
    void create_rejectsNonPositiveAmounts() {
        UUID id = UUID.randomUUID();
        for (String amount : new String[]{"0", "-5"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(new CreateTopUpRequest(id, new BigDecimal(amount)), "op"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateTopUpRequest(id, null), "op"));
        verifyNoInteractions(userRepository, balanceAccountRepository, transactionRepository);
    }

    @Test
    void create_rejectsMoreThanTwoDecimals() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateTopUpRequest(UUID.randomUUID(), new BigDecimal("10.001")), "op"));
        verifyNoInteractions(balanceAccountRepository, transactionRepository);
    }

    @Test
    void create_acceptsTrailingZerosBeyondTwoDecimals() {
        UserEntity user = user(true);
        BalanceAccountEntity account = account(user, "0.00");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(balanceAccountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));

        service.create(new CreateTopUpRequest(user.getId(), new BigDecimal("10.500")), "op");

        assertEquals(new BigDecimal("10.50"), account.getAmount());
    }

    @Test
    void create_rejectsAmountOverMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateTopUpRequest(UUID.randomUUID(), new BigDecimal("100000.01")), "op"));
        verifyNoInteractions(balanceAccountRepository, transactionRepository);
    }

    @Test
    void create_requiresUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateTopUpRequest(null, BigDecimal.TEN), "op"));
    }

    @Test
    void create_unknownUser_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class,
                () -> service.create(new CreateTopUpRequest(id, BigDecimal.TEN), "op"));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void create_inactiveUser_throwsConflict() {
        UserEntity user = user(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(ConflictException.class,
                () -> service.create(new CreateTopUpRequest(user.getId(), BigDecimal.TEN), "op"));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void create_userWithoutBalanceAccount_throwsNotFound() {
        UserEntity user = user(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(balanceAccountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class,
                () -> service.create(new CreateTopUpRequest(user.getId(), BigDecimal.TEN), "op"));
        verify(transactionRepository, never()).save(any());
    }

    // ---- list ----

    private static TransactionEntity tx(String provider, TransactionStatus status) {
        UserEntity user = user(true);
        TransactionEntity tx = new TransactionEntity();
        tx.setId(UUID.fromString("54efbe00-0000-0000-0000-000000000000"));
        tx.setBalanceAccount(account(user, "0"));
        tx.setType(TransactionType.TOPUP);
        tx.setStatus(status);
        tx.setAmount(new BigDecimal("5000.00"));
        tx.setPaymentProvider(provider);
        tx.setCreatedAt(NOW);
        return tx;
    }

    private Pageable listAndCapturePageable(String search, Integer size, String sort, TransactionEntity... rows) {
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(rows), inv.getArgument(1), rows.length));
        service.list(search, 0, size, sort);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void list_mapsMethodStatusAndTransactionNumber() {
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> new PageImpl<>(
                List.of(tx("MERCADO_PAGO", TransactionStatus.PENDING), tx("CASH", TransactionStatus.FAILED)),
                inv.getArgument(1), 2));

        PageResponse<TopUpResponse> page = service.list(null, 0, null, null);

        assertEquals("TX-54EFBE", page.content().get(0).transactionNumber());
        assertEquals("MERCADO_PAGO", page.content().get(0).method());
        assertEquals("PENDING", page.content().get(0).status());
        assertEquals("CASH", page.content().get(1).method());
        assertEquals("REJECTED", page.content().get(1).status());
    }

    @Test
    void list_defaultsToMostRecentFirstAndPageSize20() {
        Pageable pageable = listAndCapturePageable(null, null, null);

        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getSort());
    }

    @Test
    void list_sortByUserName_mapsToNestedProperty() {
        Pageable pageable = listAndCapturePageable("lucas", 10, "userName,asc");

        assertEquals(10, pageable.getPageSize());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("balanceAccount.user.fullName").getDirection());
    }

    @Test
    void list_unknownSortField_fallsBackToDefault_andSizeIsCapped() {
        Pageable pageable = listAndCapturePageable(null, 9999, "externalRef,asc");

        assertEquals(TopUpService.MAX_PAGE_SIZE, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getSort());
    }

    @Test
    void list_negativePage_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.list(null, -1, null, null));
        verifyNoInteractions(transactionRepository);
    }
}
