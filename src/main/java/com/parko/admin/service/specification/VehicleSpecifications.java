package com.parko.admin.service.specification;

import com.parko.persistence.core.model.entity.VehicleEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class VehicleSpecifications {

    private VehicleSpecifications() {
    }

    public static Specification<VehicleEntity> plateContains(String text) {
        String pattern = "%" + text.trim().toUpperCase() + "%";
        return (root, query, cb) -> cb.like(cb.upper(root.get("plate")), pattern);
    }

    public static Specification<VehicleEntity> belongsTo(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<VehicleEntity> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
