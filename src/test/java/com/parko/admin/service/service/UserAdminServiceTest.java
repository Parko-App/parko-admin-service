package com.parko.admin.service.service;

import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.UpdateUserRequest;
import com.parko.admin.service.dto.UserDetailResponse;
import com.parko.admin.service.dto.UserSummaryResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminUserRepository;
import com.parko.admin.service.repository.AdminVehicleRepository;
import com.parko.domain.lib.model.InstitutionalDomain;
import com.parko.domain.lib.model.UserRole;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private AdminUserRepository userRepository;

    @Mock
    private AdminVehicleRepository vehicleRepository;

    @Mock
    private BalanceAccountRepository balanceAccountRepository;

    @Mock
    private FirebaseUserGateway firebase;

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userRepository, vehicleRepository, balanceAccountRepository, firebase);
    }

    private UserEntity user(UserRole role, boolean active) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setStudentId("82116");
        user.setFullName("Ana Pérez");
        user.setEmail("82116@frc.utn.edu.ar");
        user.setFirebaseUid("uid-82116");
        user.setRole(role);
        user.setInstitutionalDomain(InstitutionalDomain.FRC);
        user.setActive(active);
        user.setEmailVerified(true);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return user;
    }

    private void stubDetailLookups(UserEntity user, BigDecimal balance) {
        BalanceAccountEntity account = new BalanceAccountEntity();
        account.setAmount(balance);
        when(balanceAccountRepository.findByUserId(user.getId())).thenReturn(Optional.of(account));
        when(vehicleRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
    }

    private void stubSaveReturnsArgument() {
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void get_returnsFullDetailWithBalanceAndVehicles() {
        UserEntity user = user(UserRole.STUDENT, true);
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(UUID.randomUUID());
        vehicle.setUser(user);
        vehicle.setPlate("AB123CD");
        vehicle.setBrand("Ford");
        vehicle.setModel("Ka");
        vehicle.setActive(true);
        BalanceAccountEntity account = new BalanceAccountEntity();
        account.setAmount(new BigDecimal("2500.00"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(balanceAccountRepository.findByUserId(user.getId())).thenReturn(Optional.of(account));
        when(vehicleRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(vehicle));

        UserDetailResponse response = service.get(user.getId());

        assertEquals("82116", response.studentId());
        assertEquals(InstitutionalDomain.FRC, response.institutionalDomain());
        assertEquals("uid-82116", response.firebaseUid());
        assertTrue(response.emailVerified());
        assertEquals(0, new BigDecimal("2500.00").compareTo(response.balance()));
        assertEquals(1, response.vehicles().size());
        assertEquals("AB123CD", response.vehicles().get(0).plate());
    }

    @Test
    void get_withoutBalanceAccount_hasNullBalance() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(balanceAccountRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(vehicleRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());

        assertNull(service.get(user.getId()).balance());
    }

    @Test
    void get_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.get(id));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_appliesPageSizeAndMapsRows() {
        UserEntity user = user(UserRole.STAFF, true);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(user), inv.getArgument(1, Pageable.class), 45));

        PageResponse<UserSummaryResponse> response = service.list("ana", UserRole.STAFF, true, 1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(45, response.totalElements());
        assertEquals(3, response.totalPages());
        assertEquals("Ana Pérez", response.content().get(0).fullName());
        assertEquals(UserRole.STAFF, response.content().get(0).role());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_withoutFilters_isAllowed() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertEquals(0, service.list(" ", null, null, 0).content().size());
    }

    @Test
    void list_negativePage_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, null, -1));
    }

    @Test
    void update_fullName_syncsFirebaseDisplayName() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubSaveReturnsArgument();
        stubDetailLookups(user, BigDecimal.TEN);

        UserDetailResponse response = service.update(user.getId(),
                new UpdateUserRequest("Ana María Pérez", null, null, null, null, null));

        assertEquals("Ana María Pérez", response.fullName());
        verify(firebase).updateUser("uid-82116", null, "Ana María Pérez", null, null);
    }

    @Test
    void update_email_derivesStudentIdAndDomainAndSyncsFirebase() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("90001@computos.frc.utn.edu.ar")).thenReturn(Optional.empty());
        when(userRepository.findByStudentId("90001")).thenReturn(Optional.empty());
        stubSaveReturnsArgument();
        stubDetailLookups(user, BigDecimal.ZERO);

        UserDetailResponse response = service.update(user.getId(),
                new UpdateUserRequest(null, "90001@computos.frc.utn.edu.ar", "90001", null, null, null));

        assertEquals("90001@computos.frc.utn.edu.ar", response.email());
        assertEquals("90001", response.studentId());
        assertEquals(InstitutionalDomain.COMPUTOS, response.institutionalDomain());
        verify(firebase).updateUser("uid-82116", "90001@computos.frc.utn.edu.ar", null, null, null);
    }

    @Test
    void update_emailWithMismatchedStudentId_isRejected() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest(null, "90001@frc.utn.edu.ar", "11111", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(user.getId(), request));
        verifyNoInteractions(firebase);
    }

    @Test
    void update_invalidEmail_isRejected() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest(null, "no-es-un-email", null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(user.getId(), request));
    }

    @Test
    void update_emailUsedByAnotherUser_throwsConflict() {
        UserEntity user = user(UserRole.STUDENT, true);
        UserEntity other = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("90001@frc.utn.edu.ar")).thenReturn(Optional.of(other));

        UpdateUserRequest request = new UpdateUserRequest(null, "90001@frc.utn.edu.ar", null, null, null, null);

        assertThrows(ConflictException.class, () -> service.update(user.getId(), request));
        verifyNoInteractions(firebase);
    }

    @Test
    void update_studentIdOfAnotherUser_throwsConflict() {
        UserEntity user = user(UserRole.STUDENT, true);
        UserEntity other = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("90001@frc.utn.edu.ar")).thenReturn(Optional.empty());
        when(userRepository.findByStudentId("90001")).thenReturn(Optional.of(other));

        UpdateUserRequest request = new UpdateUserRequest(null, "90001@frc.utn.edu.ar", null, null, null, null);

        assertThrows(ConflictException.class, () -> service.update(user.getId(), request));
    }

    @Test
    void update_studentIdAloneThatDiffersFromCurrent_isRejected() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest(null, null, "99999", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(user.getId(), request));
    }

    @Test
    void update_sameValues_doesNothing() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubDetailLookups(user, BigDecimal.ONE);

        service.update(user.getId(), new UpdateUserRequest(
                "Ana Pérez", "82116@frc.utn.edu.ar", "82116", UserRole.STUDENT, true, true));

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(firebase);
    }

    @Test
    void update_role_isSavedWithoutTouchingFirebase() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubSaveReturnsArgument();
        stubDetailLookups(user, BigDecimal.ZERO);

        UserDetailResponse response = service.update(user.getId(),
                new UpdateUserRequest(null, null, null, UserRole.STAFF, null, null));

        assertEquals(UserRole.STAFF, response.role());
        verifyNoInteractions(firebase);
    }

    @Test
    void update_toAdminRole_isRejected() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest(null, null, null, UserRole.ADMIN, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(user.getId(), request));
    }

    @Test
    void update_roleOfAnAdmin_isRejected() {
        UserEntity admin = user(UserRole.ADMIN, true);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        UpdateUserRequest request = new UpdateUserRequest(null, null, null, UserRole.STUDENT, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(admin.getId(), request));
    }

    @Test
    void update_deactivate_disablesFirebaseAccount() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubSaveReturnsArgument();
        stubDetailLookups(user, BigDecimal.ZERO);

        UserDetailResponse response = service.update(user.getId(),
                new UpdateUserRequest(null, null, null, null, false, null));

        assertFalse(response.active());
        verify(firebase).updateUser("uid-82116", null, null, true, null);
    }

    @Test
    void update_reactivate_enablesFirebaseAccount() {
        UserEntity user = user(UserRole.STUDENT, false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubSaveReturnsArgument();
        stubDetailLookups(user, BigDecimal.ZERO);

        UserDetailResponse response = service.update(user.getId(),
                new UpdateUserRequest(null, null, null, null, true, null));

        assertTrue(response.active());
        verify(firebase).updateUser("uid-82116", null, null, false, null);
    }

    @Test
    void update_emailVerified_syncsFirebase() {
        UserEntity user = user(UserRole.STUDENT, true);
        user.setEmailVerified(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubSaveReturnsArgument();
        stubDetailLookups(user, BigDecimal.ZERO);

        UserDetailResponse response = service.update(user.getId(),
                new UpdateUserRequest(null, null, null, null, null, true));

        assertTrue(response.emailVerified());
        verify(firebase).updateUser("uid-82116", null, null, null, true);
    }

    @Test
    void update_blankName_isRejected() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest("  ", null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(user.getId(), request));
    }

    @Test
    void update_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        UpdateUserRequest request = new UpdateUserRequest("X", null, null, null, null, null);

        assertThrows(NoSuchElementException.class, () -> service.update(id, request));
    }

    @Test
    void delete_activeUser_isDeactivatedAndFirebaseDisabled() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubSaveReturnsArgument();

        service.delete(user.getId());

        assertFalse(user.isActive());
        verify(firebase).updateUser(eq("uid-82116"), eq(null), eq(null), eq(true), eq(null));
    }

    @Test
    void delete_alreadyInactive_isNoOp() {
        UserEntity user = user(UserRole.STUDENT, false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.delete(user.getId());

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(firebase);
    }

    @Test
    void delete_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.delete(id));
    }

    @Test
    void update_firebaseFailure_propagatesSoTransactionRollsBack() {
        UserEntity user = user(UserRole.STUDENT, true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubSaveReturnsArgument();
        doThrow(new IllegalStateException("firebase caído")).when(firebase)
                .updateUser(any(), any(), any(), any(), any());

        UpdateUserRequest request = new UpdateUserRequest("Nuevo Nombre", null, null, null, null, null);

        assertThrows(IllegalStateException.class, () -> service.update(user.getId(), request));
    }
}
