package com.parko.admin.service.specification;

import com.parko.persistence.core.model.entity.VehicleEntity;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class VehicleSpecificationsTest {

    @Mock
    private Root<VehicleEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path path;

    @Mock
    private Path userPath;

    @Mock
    private Path idPath;

    @Mock
    private Expression<String> upper;

    @Mock
    private Predicate predicate;

    @Test
    void plateContains_searchesUppercase() {
        when(root.get("plate")).thenReturn(path);
        when(cb.upper(any(Expression.class))).thenReturn(upper);
        when(cb.like(upper, "%AB1%")).thenReturn(predicate);

        assertSame(predicate, VehicleSpecifications.plateContains(" ab1 ").toPredicate(root, query, cb));
    }

    @Test
    void belongsTo_comparesOwnerId() {
        UUID userId = UUID.randomUUID();
        when(root.get("user")).thenReturn(userPath);
        when(userPath.get("id")).thenReturn(idPath);
        when(cb.equal(idPath, userId)).thenReturn(predicate);

        assertSame(predicate, VehicleSpecifications.belongsTo(userId).toPredicate(root, query, cb));
    }

    @Test
    void isActive_buildsEquals() {
        when(root.get("active")).thenReturn(path);
        when(cb.equal(path, false)).thenReturn(predicate);

        assertSame(predicate, VehicleSpecifications.isActive(false).toPredicate(root, query, cb));
    }
}
