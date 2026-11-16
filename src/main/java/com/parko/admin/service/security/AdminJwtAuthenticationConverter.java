package com.parko.admin.service.security;

import com.parko.domain.lib.model.UserRole;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final UserRepository userRepository;

    public AdminJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        boolean admin = userRepository.findByFirebaseUid(jwt.getSubject())
                .filter(this::isActiveAdmin)
                .isPresent();
        List<GrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority(ROLE_ADMIN))
                : List.of();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private boolean isActiveAdmin(UserEntity user) {
        return user.isActive() && user.getRole() == UserRole.ADMIN;
    }
}
