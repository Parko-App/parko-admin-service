package com.parko.admin.service.specification;

import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.TransactionEntity;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TopUpSpecifications {

    private static final Pattern HEX_PREFIX = Pattern.compile("[0-9a-f-]+");

    private TopUpSpecifications() {
    }

    public static Specification<TransactionEntity> isTopUp() {
        return (root, query, cb) -> cb.equal(root.get("type"), TransactionType.TOPUP);
    }

    /** Busca por nombre o correo del usuario, o por el número de transacción ("TX-54EFBE" = primeros 6 del id). */
    public static Specification<TransactionEntity> matches(String text) {
        String needle = text.trim().toLowerCase();
        return (root, query, cb) -> {
            var user = root.join("balanceAccount", JoinType.LEFT).join("user", JoinType.LEFT);
            String pattern = "%" + needle + "%";
            List<Predicate> any = new ArrayList<>();
            any.add(cb.like(cb.lower(user.get("fullName")), pattern));
            any.add(cb.like(cb.lower(user.get("email")), pattern));

            String hex = needle.startsWith("tx-") ? needle.substring(3) : needle;
            if (!hex.isEmpty() && HEX_PREFIX.matcher(hex).matches()) {
                any.add(cb.like(cb.lower(root.get("id").as(String.class)), hex + "%"));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
    }
}
