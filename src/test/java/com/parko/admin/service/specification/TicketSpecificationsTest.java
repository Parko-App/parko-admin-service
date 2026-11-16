package com.parko.admin.service.specification;

import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.entity.TicketEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class TicketSpecificationsTest {

    @Mock
    private Root<TicketEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path path;

    @Mock
    private Predicate predicate;

    @Test
    void issuedFrom_buildsGreaterThanOrEqual() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 1, 0, 0);
        when(root.get("issuedAt")).thenReturn(path);
        when(cb.greaterThanOrEqualTo(path, from)).thenReturn(predicate);

        Predicate result = TicketSpecifications.issuedFrom(from).toPredicate(root, query, cb);

        assertSame(predicate, result);
    }

    @Test
    void issuedBefore_buildsLessThan() {
        LocalDateTime to = LocalDateTime.of(2026, 11, 16, 0, 0);
        when(root.get("issuedAt")).thenReturn(path);
        when(cb.lessThan(path, to)).thenReturn(predicate);

        Predicate result = TicketSpecifications.issuedBefore(to).toPredicate(root, query, cb);

        assertSame(predicate, result);
    }

    @Test
    void hasStatus_buildsEquals() {
        when(root.get("status")).thenReturn(path);
        when(cb.equal(path, TicketStatus.PAID)).thenReturn(predicate);

        Specification<TicketEntity> spec = TicketSpecifications.hasStatus(TicketStatus.PAID);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).equal(path, TicketStatus.PAID);
    }
}
