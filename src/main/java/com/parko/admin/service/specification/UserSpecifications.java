package com.parko.admin.service.specification;

import com.parko.domain.lib.model.UserRole;
import com.parko.persistence.core.model.entity.UserEntity;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<UserEntity> matches(String text) {
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("studentId")), pattern));
    }

    public static Specification<UserEntity> hasRole(UserRole role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<UserEntity> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
