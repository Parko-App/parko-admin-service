package com.parko.admin.service.security;

import com.parko.domain.lib.model.UserRole;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJwtAuthenticationConverterTest {

    @Mock
    private UserRepository userRepository;

    private Jwt jwt(String uid) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(uid)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private UserEntity user(UserRole role, boolean active) {
        UserEntity user = new UserEntity();
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private Authentication convert(String uid) {
        return new AdminJwtAuthenticationConverter(userRepository).convert(jwt(uid));
    }

    @Test
    void activeAdmin_getsRoleAdmin() {
        when(userRepository.findByFirebaseUid("uid-1")).thenReturn(Optional.of(user(UserRole.ADMIN, true)));

        Authentication auth = convert("uid-1");

        assertEquals("uid-1", auth.getName());
        assertTrue(auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals));
    }

    @Test
    void inactiveAdmin_getsNoAuthorities() {
        when(userRepository.findByFirebaseUid("uid-2")).thenReturn(Optional.of(user(UserRole.ADMIN, false)));

        assertTrue(convert("uid-2").getAuthorities().isEmpty());
    }

    @Test
    void nonAdminUser_getsNoAuthorities() {
        when(userRepository.findByFirebaseUid("uid-3")).thenReturn(Optional.of(user(UserRole.STUDENT, true)));

        assertTrue(convert("uid-3").getAuthorities().isEmpty());
    }

    @Test
    void unknownUser_getsNoAuthorities() {
        when(userRepository.findByFirebaseUid("uid-4")).thenReturn(Optional.empty());

        assertTrue(convert("uid-4").getAuthorities().isEmpty());
    }
}
