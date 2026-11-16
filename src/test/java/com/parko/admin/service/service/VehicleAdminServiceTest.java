package com.parko.admin.service.service;

import com.parko.admin.service.dto.CreateVehicleRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.UpdateVehicleRequest;
import com.parko.admin.service.dto.VehicleResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminUserRepository;
import com.parko.admin.service.repository.AdminVehicleRepository;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleAdminServiceTest {

    @Mock
    private AdminVehicleRepository vehicleRepository;

    @Mock
    private AdminUserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    private VehicleAdminService service;

    @BeforeEach
    void setUp() {
        service = new VehicleAdminService(vehicleRepository, userRepository, entityManager, 3);
    }

    private UserEntity owner() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setFullName("Ana Pérez");
        user.setEmail("82116@frc.utn.edu.ar");
        return user;
    }

    private VehicleEntity vehicle(UserEntity owner, String plate, boolean active) {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(UUID.randomUUID());
        vehicle.setUser(owner);
        vehicle.setPlate(plate);
        vehicle.setBrand("Ford");
        vehicle.setModel("Ka");
        vehicle.setActive(active);
        vehicle.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        vehicle.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return vehicle;
    }

    private void stubSaveReturnsArgument() {
        when(vehicleRepository.saveAndFlush(any(VehicleEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void get_returnsVehicleWithOwnerAndVisiblePlate() {
        UserEntity owner = owner();
        VehicleEntity released = vehicle(owner, "AB123CD#abc", false);
        when(vehicleRepository.findById(released.getId())).thenReturn(Optional.of(released));

        VehicleResponse response = service.get(released.getId());

        assertEquals("AB123CD", response.plate());
        assertEquals("Ana Pérez", response.ownerName());
        assertEquals(owner.getId(), response.userId());
        assertFalse(response.active());
    }

    @Test
    void get_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.get(id));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_appliesFiltersAndPageSize() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD", true);
        when(vehicleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(vehicle), inv.getArgument(1, Pageable.class), 21));

        PageResponse<VehicleResponse> response = service.list("ab1", owner.getId(), true, 1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(vehicleRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(2, response.totalPages());
        assertEquals("AB123CD", response.content().get(0).plate());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_withoutFilters_isAllowed() {
        when(vehicleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertEquals(0, service.list(" ", null, null, 0).content().size());
    }

    @Test
    void list_negativePage_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, null, -1));
    }

    @Test
    void create_normalizesPlateAndLocksOwner() {
        UserEntity owner = owner();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.empty());
        when(vehicleRepository.countByUser_IdAndActiveTrue(owner.getId())).thenReturn(2L);
        stubSaveReturnsArgument();

        VehicleResponse response = service.create(new CreateVehicleRequest(owner.getId(), " ab123cd ", "Ford", "Ka"));

        assertEquals("AB123CD", response.plate());
        assertTrue(response.active());
        assertEquals(owner.getId(), response.userId());
        verify(entityManager).lock(owner, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void create_unknownOwner_throwsNoSuchElement() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CreateVehicleRequest request = new CreateVehicleRequest(userId, "AB123CD", "Ford", "Ka");

        assertThrows(NoSuchElementException.class, () -> service.create(request));
    }

    @Test
    void create_invalidPlate_isRejected() {
        UserEntity owner = owner();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        CreateVehicleRequest request = new CreateVehicleRequest(owner.getId(), "123", "Ford", "Ka");

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(vehicleRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_brandTooLong_isRejected() {
        UserEntity owner = owner();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        CreateVehicleRequest request = new CreateVehicleRequest(owner.getId(), "AB123CD", "x".repeat(16), "Ka");

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void create_plateAlreadyRegistered_throwsConflict() {
        UserEntity owner = owner();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.of(vehicle(owner(), "AB123CD", true)));

        CreateVehicleRequest request = new CreateVehicleRequest(owner.getId(), "AB123CD", "Ford", "Ka");

        assertThrows(ConflictException.class, () -> service.create(request));
    }

    @Test
    void create_ownerAtActiveLimit_isRejected() {
        UserEntity owner = owner();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.empty());
        when(vehicleRepository.countByUser_IdAndActiveTrue(owner.getId())).thenReturn(3L);

        CreateVehicleRequest request = new CreateVehicleRequest(owner.getId(), "AB123CD", "Ford", "Ka");

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(vehicleRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_brandAndModel_keepsPlateAndOwner() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.of(vehicle));
        stubSaveReturnsArgument();

        VehicleResponse response = service.update(vehicle.getId(),
                new UpdateVehicleRequest(null, null, "Renault", "Clio", null));

        assertEquals("Renault", response.brand());
        assertEquals("Clio", response.model());
        assertEquals("AB123CD", response.plate());
        verify(entityManager, never()).lock(any(), any());
    }

    @Test
    void update_plate_isNormalizedAndCheckedForConflicts() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByPlate("XY987ZW")).thenReturn(Optional.empty());
        stubSaveReturnsArgument();

        VehicleResponse response = service.update(vehicle.getId(),
                new UpdateVehicleRequest(null, "xy987zw", null, null, null));

        assertEquals("XY987ZW", response.plate());
    }

    @Test
    void update_plateOfAnotherVehicle_throwsConflict() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD", true);
        VehicleEntity other = vehicle(owner(), "XY987ZW", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByPlate("XY987ZW")).thenReturn(Optional.of(other));

        UpdateVehicleRequest request = new UpdateVehicleRequest(null, "XY987ZW", null, null, null);

        assertThrows(ConflictException.class, () -> service.update(vehicle.getId(), request));
    }

    @Test
    void update_invalidPlate_isRejected() {
        VehicleEntity vehicle = vehicle(owner(), "AB123CD", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        UpdateVehicleRequest request = new UpdateVehicleRequest(null, "INVALIDA", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(vehicle.getId(), request));
        verify(vehicleRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_reassignToAnotherOwner_locksNewOwnerAndChecksLimit() {
        UserEntity oldOwner = owner();
        UserEntity newOwner = owner();
        VehicleEntity vehicle = vehicle(oldOwner, "AB123CD", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(newOwner.getId())).thenReturn(Optional.of(newOwner));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.countByUser_IdAndActiveTrue(newOwner.getId())).thenReturn(1L);
        stubSaveReturnsArgument();

        VehicleResponse response = service.update(vehicle.getId(),
                new UpdateVehicleRequest(newOwner.getId(), null, null, null, null));

        assertEquals(newOwner.getId(), response.userId());
        verify(entityManager).lock(newOwner, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void update_reassignToOwnerAtLimit_isRejected() {
        UserEntity oldOwner = owner();
        UserEntity newOwner = owner();
        VehicleEntity vehicle = vehicle(oldOwner, "AB123CD", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(newOwner.getId())).thenReturn(Optional.of(newOwner));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.countByUser_IdAndActiveTrue(newOwner.getId())).thenReturn(3L);

        UpdateVehicleRequest request = new UpdateVehicleRequest(newOwner.getId(), null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.update(vehicle.getId(), request));
        verify(vehicleRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_reassignToUnknownOwner_throwsNoSuchElement() {
        VehicleEntity vehicle = vehicle(owner(), "AB123CD", true);
        UUID unknown = UUID.randomUUID();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        UpdateVehicleRequest request = new UpdateVehicleRequest(unknown, null, null, null, null);

        assertThrows(NoSuchElementException.class, () -> service.update(vehicle.getId(), request));
    }

    @Test
    void update_deactivate_releasesPlate() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        stubSaveReturnsArgument();

        VehicleResponse response = service.update(vehicle.getId(),
                new UpdateVehicleRequest(null, null, null, null, false));

        assertFalse(response.active());
        assertEquals("AB123CD", response.plate());
        assertEquals("AB123CD#" + vehicle.getId(), vehicle.getPlate());
        verify(vehicleRepository, never()).findByPlate(any());
    }

    @Test
    void update_reactivate_restoresVisiblePlateAndChecksLimit() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD#old-id", false);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.empty());
        when(vehicleRepository.countByUser_IdAndActiveTrue(owner.getId())).thenReturn(2L);
        stubSaveReturnsArgument();

        VehicleResponse response = service.update(vehicle.getId(),
                new UpdateVehicleRequest(null, null, null, null, true));

        assertTrue(response.active());
        assertEquals("AB123CD", vehicle.getPlate());
    }

    @Test
    void update_reactivateWithPlateInUse_throwsConflict() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD#old-id", false);
        VehicleEntity holder = vehicle(owner(), "AB123CD", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.of(holder));

        UpdateVehicleRequest request = new UpdateVehicleRequest(null, null, null, null, true);

        assertThrows(ConflictException.class, () -> service.update(vehicle.getId(), request));
    }

    @Test
    void update_reactivateOwnerAtLimit_isRejected() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD#old-id", false);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByPlate("AB123CD")).thenReturn(Optional.empty());
        when(vehicleRepository.countByUser_IdAndActiveTrue(owner.getId())).thenReturn(3L);

        UpdateVehicleRequest request = new UpdateVehicleRequest(null, null, null, null, true);

        assertThrows(IllegalArgumentException.class, () -> service.update(vehicle.getId(), request));
    }

    @Test
    void update_inactiveVehicleStaysInactive_editsKeepPlateReleased() {
        UserEntity owner = owner();
        VehicleEntity vehicle = vehicle(owner, "AB123CD#old-id", false);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        stubSaveReturnsArgument();

        service.update(vehicle.getId(), new UpdateVehicleRequest(null, "XY987ZW", "Fiat", null, null));

        assertEquals("XY987ZW#" + vehicle.getId(), vehicle.getPlate());
        assertEquals("Fiat", vehicle.getBrand());
        verify(vehicleRepository, never()).findByPlate(any());
    }

    @Test
    void update_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        UpdateVehicleRequest request = new UpdateVehicleRequest(null, null, "X", null, null);

        assertThrows(NoSuchElementException.class, () -> service.update(id, request));
    }

    @Test
    void delete_activeVehicle_isDeactivatedAndPlateReleased() {
        VehicleEntity vehicle = vehicle(owner(), "AB123CD", true);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        stubSaveReturnsArgument();

        service.delete(vehicle.getId());

        assertFalse(vehicle.isActive());
        assertEquals("AB123CD#" + vehicle.getId(), vehicle.getPlate());
    }

    @Test
    void delete_alreadyInactive_isNoOp() {
        VehicleEntity vehicle = vehicle(owner(), "AB123CD#x", false);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        service.delete(vehicle.getId());

        verify(vehicleRepository, never()).saveAndFlush(any());
    }

    @Test
    void delete_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.delete(id));
    }
}
