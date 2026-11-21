package com.parko.admin.service.specification;

import com.parko.domain.lib.model.AccessEventType;
import com.parko.persistence.core.model.entity.AccessLogEntity;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class AccessLogSpecifications {

    private AccessLogSpecifications() {
    }

    /** Patente parcial (sin distinguir mayúsculas ni espacios), sobre la patente de visitante o la del vehículo. */
    public static Specification<AccessLogEntity> plateContains(String text) {
        String pattern = "%" + text.replaceAll("\\s+", "").toUpperCase() + "%";
        return (root, query, cb) -> {
            var session = root.join("parkingSession", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.upper(session.get("visitorPlate")), pattern),
                    cb.like(cb.upper(session.get("plateSnapshot")), pattern));
        };
    }

    public static Specification<AccessLogEntity> hasMovement(AccessEventType movement) {
        return (root, query, cb) -> cb.equal(root.get("eventType"), movement);
    }
}
