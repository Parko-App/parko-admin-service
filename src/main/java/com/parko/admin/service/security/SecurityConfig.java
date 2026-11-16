package com.parko.admin.service.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String FIREBASE_JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
    private static final String FIREBASE_ISSUER_PREFIX = "https://securetoken.google.com/";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AdminJwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${firebase.project-id}") String firebaseProjectId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(FIREBASE_JWK_SET_URI).build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(FIREBASE_ISSUER_PREFIX + firebaseProjectId);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, audience -> audience != null && audience.contains(firebaseProjectId));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }
}
