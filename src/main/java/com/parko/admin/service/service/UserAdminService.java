package com.parko.admin.service.service;

import com.parko.admin.service.dto.CreateUserRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.UpdateUserRequest;
import com.parko.admin.service.dto.UserDetailResponse;
import com.parko.admin.service.dto.UserSummaryResponse;
import com.parko.admin.service.dto.VehicleResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminUserRepository;
import com.parko.admin.service.repository.AdminVehicleRepository;
import com.parko.admin.service.specification.UserSpecifications;
import com.parko.domain.lib.model.Email;
import com.parko.domain.lib.model.User;
import com.parko.domain.lib.model.UserRole;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class UserAdminService {

    public static final int PAGE_SIZE = 20;

    private final AdminUserRepository userRepository;
    private final AdminVehicleRepository vehicleRepository;
    private final BalanceAccountRepository balanceAccountRepository;
    private final FirebaseUserGateway firebase;

    public UserAdminService(AdminUserRepository userRepository,
                            AdminVehicleRepository vehicleRepository,
                            BalanceAccountRepository balanceAccountRepository,
                            FirebaseUserGateway firebase) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.balanceAccountRepository = balanceAccountRepository;
        this.firebase = firebase;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> list(String text, UserRole role, Boolean active, int page) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        Specification<UserEntity> spec = Specification.unrestricted();
        if (text != null && !text.isBlank()) {
            spec = spec.and(UserSpecifications.matches(text));
        }
        if (role != null) {
            spec = spec.and(UserSpecifications.hasRole(role));
        }
        if (active != null) {
            spec = spec.and(UserSpecifications.isActive(active));
        }
        Page<UserEntity> result = userRepository.findAll(spec,
                PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public UserDetailResponse get(UUID id) {
        return toDetail(find(id));
    }

    public UserDetailResponse create(CreateUserRequest request) {
        String fullName = requireName(request.fullName());
        PasswordPolicy.validate(request.password());
        if (request.email() == null) {
            throw new IllegalArgumentException("El email no puede estar vacío");
        }
        Email email = new Email(request.email().trim());
        String studentId = request.studentId() != null ? request.studentId().trim() : email.getStudentId();
        User domainUser = new User(UUID.randomUUID(), studentId, fullName, email);

        UserRole role = request.role() != null ? request.role() : UserRole.STUDENT;
        if (role == UserRole.ADMIN) {
            throw new IllegalArgumentException("El rol de administrador no se asigna desde este recurso");
        }
        ensureEmailAvailable(email.getValue(), null);
        ensureStudentIdAvailable(email.getStudentId(), null);

        boolean emailVerified = Boolean.TRUE.equals(request.emailVerified());
        String firebaseUid = firebase.createUser(email.getValue(), request.password(), fullName, emailVerified);

        try {
            LocalDateTime now = LocalDateTime.now();
            UserEntity user = new UserEntity();
            user.setId(domainUser.getId());
            user.setStudentId(domainUser.getStudentId());
            user.setFullName(fullName);
            user.setEmail(email.getValue());
            user.setFirebaseUid(firebaseUid);
            user.setRole(role);
            user.setInstitutionalDomain(email.getInstitutionalDomain());
            user.setEmailVerified(emailVerified);
            user.setActive(true);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            UserEntity saved = userRepository.saveAndFlush(user);

            BalanceAccountEntity account = new BalanceAccountEntity();
            account.setId(UUID.randomUUID());
            account.setUser(saved);
            account.setAmount(BigDecimal.ZERO);
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            balanceAccountRepository.saveAndFlush(account);

            return toDetail(saved);
        } catch (DataIntegrityViolationException e) {
            firebase.deleteUser(firebaseUid);
            throw new ConflictException("Ya existe un usuario con ese email o legajo");
        } catch (RuntimeException e) {
            firebase.deleteUser(firebaseUid);
            throw e;
        }
    }

    public UserDetailResponse update(UUID id, UpdateUserRequest request) {
        UserEntity user = find(id);
        boolean changed = false;

        String newName = null;
        if (request.fullName() != null && !request.fullName().equals(user.getFullName())) {
            newName = requireName(request.fullName());
            user.setFullName(newName);
            changed = true;
        }

        String newEmail = null;
        if (request.email() != null && !request.email().trim().equals(user.getEmail())) {
            Email email = new Email(request.email().trim());
            if (request.studentId() != null && !request.studentId().equals(email.getStudentId())) {
                throw new IllegalArgumentException(
                        "El legajo no coincide con el legajo del email: " + request.studentId() + " vs " + email.getStudentId());
            }
            ensureEmailAvailable(email.getValue(), user.getId());
            ensureStudentIdAvailable(email.getStudentId(), user.getId());
            user.setEmail(email.getValue());
            user.setStudentId(email.getStudentId());
            user.setInstitutionalDomain(email.getInstitutionalDomain());
            newEmail = email.getValue();
            changed = true;
        } else if (request.studentId() != null && !request.studentId().equals(user.getStudentId())) {
            throw new IllegalArgumentException(
                    "El legajo se deriva del email: para cambiarlo hay que modificar el email");
        }

        if (request.role() != null && request.role() != user.getRole()) {
            if (request.role() == UserRole.ADMIN || user.getRole() == UserRole.ADMIN) {
                throw new IllegalArgumentException("El rol de administrador no se modifica desde este recurso");
            }
            user.setRole(request.role());
            changed = true;
        }

        Boolean newDisabled = null;
        if (request.active() != null && request.active() != user.isActive()) {
            user.setActive(request.active());
            newDisabled = !request.active();
            changed = true;
        }

        Boolean newEmailVerified = null;
        if (request.emailVerified() != null && request.emailVerified() != user.isEmailVerified()) {
            user.setEmailVerified(request.emailVerified());
            newEmailVerified = request.emailVerified();
            changed = true;
        }

        if (!changed) {
            return toDetail(user);
        }

        user.setUpdatedAt(LocalDateTime.now());
        UserEntity saved = userRepository.saveAndFlush(user);

        if (newName != null || newEmail != null || newDisabled != null || newEmailVerified != null) {
            firebase.updateUser(saved.getFirebaseUid(), newEmail, newName, newDisabled, newEmailVerified);
        }
        return toDetail(saved);
    }

    public void delete(UUID id) {
        UserEntity user = find(id);
        if (!user.isActive()) {
            return;
        }
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.saveAndFlush(user);
        firebase.updateUser(user.getFirebaseUid(), null, null, true, null);
    }

    private UserEntity find(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + id));
    }

    private String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        return fullName.trim();
    }

    private void ensureEmailAvailable(String email, UUID currentUserId) {
        userRepository.findByEmail(email)
                .filter(other -> !Objects.equals(other.getId(), currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Ya existe un usuario con el email: " + email);
                });
    }

    private void ensureStudentIdAvailable(String studentId, UUID currentUserId) {
        userRepository.findByStudentId(studentId)
                .filter(other -> !Objects.equals(other.getId(), currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Ya existe un usuario con el legajo: " + studentId);
                });
    }

    private UserSummaryResponse toSummary(UserEntity user) {
        return new UserSummaryResponse(
                user.getId(), user.getStudentId(), user.getFullName(), user.getEmail(), user.getRole(), user.isActive());
    }

    private UserDetailResponse toDetail(UserEntity user) {
        BigDecimal balance = balanceAccountRepository.findByUserId(user.getId())
                .map(account -> account.getAmount())
                .orElse(null);
        List<VehicleResponse> vehicles = vehicleRepository
                .findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(VehicleMapper::toResponse)
                .toList();
        return new UserDetailResponse(
                user.getId(),
                user.getStudentId(),
                user.getFullName(),
                user.getEmail(),
                user.getInstitutionalDomain(),
                user.getRole(),
                user.isActive(),
                user.isEmailVerified(),
                user.getFirebaseUid(),
                balance,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                vehicles
        );
    }
}
