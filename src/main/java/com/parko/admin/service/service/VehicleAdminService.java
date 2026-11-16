package com.parko.admin.service.service;

import com.parko.admin.service.dto.CreateVehicleRequest;
import com.parko.admin.service.dto.PageResponse;
import com.parko.admin.service.dto.UpdateVehicleRequest;
import com.parko.admin.service.dto.VehicleResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminUserRepository;
import com.parko.admin.service.repository.AdminVehicleRepository;
import com.parko.admin.service.specification.VehicleSpecifications;
import com.parko.domain.lib.model.Vehicle;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.model.entity.VehicleEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class VehicleAdminService {

    public static final int PAGE_SIZE = 20;

    private final AdminVehicleRepository vehicleRepository;
    private final AdminUserRepository userRepository;
    private final EntityManager entityManager;
    private final int maxActivePerUser;

    public VehicleAdminService(AdminVehicleRepository vehicleRepository,
                               AdminUserRepository userRepository,
                               EntityManager entityManager,
                               @Value("${vehicles.max-active-per-user}") int maxActivePerUser) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.maxActivePerUser = maxActivePerUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> list(String plate, UUID userId, Boolean active, int page) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        Specification<VehicleEntity> spec = Specification.unrestricted();
        if (plate != null && !plate.isBlank()) {
            spec = spec.and(VehicleSpecifications.plateContains(plate));
        }
        if (userId != null) {
            spec = spec.and(VehicleSpecifications.belongsTo(userId));
        }
        if (active != null) {
            spec = spec.and(VehicleSpecifications.isActive(active));
        }
        Page<VehicleEntity> result = vehicleRepository.findAll(spec,
                PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, VehicleMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(UUID id) {
        return VehicleMapper.toResponse(find(id));
    }

    public VehicleResponse create(CreateVehicleRequest request) {
        UserEntity owner = lockOwner(request.userId());
        Vehicle vehicle = new Vehicle(owner.getId(), request.plate().trim(), request.brand().trim(), request.model().trim());
        ensurePlateAvailable(vehicle.getPlate(), null);
        ensureActiveLimitNotReached(owner.getId());

        LocalDateTime now = LocalDateTime.now();
        VehicleEntity entity = new VehicleEntity();
        entity.setId(UUID.randomUUID());
        entity.setUser(owner);
        entity.setPlate(vehicle.getPlate());
        entity.setBrand(vehicle.getBrand());
        entity.setModel(vehicle.getModel());
        entity.setActive(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return VehicleMapper.toResponse(vehicleRepository.saveAndFlush(entity));
    }

    public VehicleResponse update(UUID id, UpdateVehicleRequest request) {
        VehicleEntity entity = find(id);

        UserEntity owner = entity.getUser();
        boolean ownerChanged = request.userId() != null && !request.userId().equals(owner.getId());
        if (ownerChanged) {
            owner = lockOwner(request.userId());
        }
        boolean wasActive = entity.isActive();
        boolean active = request.active() != null ? request.active() : wasActive;
        String plate = request.plate() != null ? request.plate().trim() : VehicleMapper.visiblePlate(entity.getPlate());
        String brand = request.brand() != null ? request.brand().trim() : entity.getBrand();
        String model = request.model() != null ? request.model().trim() : entity.getModel();

        Vehicle validated = new Vehicle(owner.getId(), plate, brand, model);

        if (active) {
            ensurePlateAvailable(validated.getPlate(), entity.getId());
            if (!wasActive || ownerChanged) {
                ensureActiveLimitNotReached(owner.getId());
            }
        }

        entity.setUser(owner);
        entity.setPlate(active ? validated.getPlate() : VehicleMapper.releasedPlate(validated.getPlate(), entity.getId()));
        entity.setBrand(validated.getBrand());
        entity.setModel(validated.getModel());
        entity.setActive(active);
        entity.setUpdatedAt(LocalDateTime.now());
        return VehicleMapper.toResponse(vehicleRepository.saveAndFlush(entity));
    }

    public void delete(UUID id) {
        VehicleEntity entity = find(id);
        if (!entity.isActive()) {
            return;
        }
        entity.setActive(false);
        entity.setPlate(VehicleMapper.releasedPlate(entity.getPlate(), entity.getId()));
        entity.setUpdatedAt(LocalDateTime.now());
        vehicleRepository.saveAndFlush(entity);
    }

    private VehicleEntity find(UUID id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vehículo no encontrado con id: " + id));
    }

    private UserEntity lockOwner(UUID userId) {
        UserEntity owner = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + userId));
        entityManager.lock(owner, LockModeType.PESSIMISTIC_WRITE);
        return owner;
    }

    private void ensurePlateAvailable(String plate, UUID currentVehicleId) {
        vehicleRepository.findByPlate(plate)
                .filter(other -> !other.getId().equals(currentVehicleId))
                .ifPresent(other -> {
                    throw new ConflictException("La patente ya está registrada: " + plate);
                });
    }

    private void ensureActiveLimitNotReached(UUID userId) {
        long active = vehicleRepository.countByUser_IdAndActiveTrue(userId);
        if (active >= maxActivePerUser) {
            throw new IllegalArgumentException(
                    "El usuario ya tiene el máximo de " + maxActivePerUser + " vehículos activos");
        }
    }
}
