package com.parko.admin.service.specification;

import com.parko.domain.lib.model.UserRole;
import com.parko.persistence.core.model.entity.UserEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class UserSpecificationsTest {

    @Mock
    private Root<UserEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path path;

    @Mock
    private Expression<String> lowered;

    @Mock
    private Predicate predicate;

    @Test
    void matches_searchesNameEmailAndStudentIdCaseInsensitive() {
        when(root.get(anyString())).thenReturn(path);
        when(cb.lower(any(Expression.class))).thenReturn(lowered);
        when(cb.like(lowered, "%ana%")).thenReturn(predicate);
        when(cb.or(predicate, predicate, predicate)).thenReturn(predicate);

        Predicate result = UserSpecifications.matches("  Ana ").toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(root).get("fullName");
        verify(root).get("email");
        verify(root).get("studentId");
    }

    @Test
    void hasRole_buildsEquals() {
        when(root.get("role")).thenReturn(path);
        when(cb.equal(path, UserRole.STAFF)).thenReturn(predicate);

        assertSame(predicate, UserSpecifications.hasRole(UserRole.STAFF).toPredicate(root, query, cb));
    }

    @Test
    void isActive_buildsEquals() {
        when(root.get("active")).thenReturn(path);
        when(cb.equal(path, true)).thenReturn(predicate);

        assertSame(predicate, UserSpecifications.isActive(true).toPredicate(root, query, cb));
    }
}
