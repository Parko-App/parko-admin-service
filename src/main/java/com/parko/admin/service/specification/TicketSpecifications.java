package com.parko.admin.service.specification;

import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.entity.TicketEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<TicketEntity> issuedFrom(LocalDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("issuedAt"), from);
    }

    public static Specification<TicketEntity> issuedBefore(LocalDateTime toExclusive) {
        return (root, query, cb) -> cb.lessThan(root.get("issuedAt"), toExclusive);
    }

    public static Specification<TicketEntity> hasStatus(TicketStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
