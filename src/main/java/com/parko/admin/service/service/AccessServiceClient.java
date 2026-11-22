package com.parko.admin.service.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.parko.admin.service.exception.ExternalServiceException;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Cliente de access-service (POST /api/v1/access). access-service valida tokens de Keycloak, así que este cliente
 * pide uno con el flujo client_credentials (service account) y lo reutiliza hasta que está por vencer.
 */
@Component
public class AccessServiceClient {

    public static final String NOT_CONFIGURED =
            "access-service no está configurado en el panel (faltan KEYCLOAK_CLIENT_ID y KEYCLOAK_CLIENT_SECRET)";
    public static final String UNREACHABLE = "No se pudo comunicar con access-service. Verificá que esté en funcionamiento.";

    private static final Duration TOKEN_MARGIN = Duration.ofSeconds(10);
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofSeconds(60);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient accessClient;
    private final RestClient tokenClient;
    private final Clock clock;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    /** El cliente HTTP se arma acá: esta versión de Spring Boot no expone un RestClient.Builder como bean. */
    @Autowired
    public AccessServiceClient(Clock clock,
                               @Value("${access-service.base-url}") String baseUrl,
                               @Value("${access-service.keycloak.token-uri}") String tokenUri,
                               @Value("${access-service.keycloak.client-id}") String clientId,
                               @Value("${access-service.keycloak.client-secret}") String clientSecret) {
        this(accessClientOf(baseUrl), tokenClientOf(), clock, tokenUri, clientId, clientSecret);
    }

    /** Constructor con los clientes HTTP ya armados (los tests les ponen un servidor simulado). */
    AccessServiceClient(RestClient accessClient, RestClient tokenClient, Clock clock,
                        String tokenUri, String clientId, String clientSecret) {
        this.accessClient = accessClient;
        this.tokenClient = tokenClient;
        this.clock = clock;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private static RestClient accessClientOf(String baseUrl) {
        return withTimeouts(RestClient.builder()).baseUrl(baseUrl).build();
    }

    private static RestClient tokenClientOf() {
        return withTimeouts(RestClient.builder()).build();
    }

    private static RestClient.Builder withTimeouts(RestClient.Builder builder) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return builder.requestFactory(factory);
    }

    /**
     * Pide a access-service que resuelva un acceso. Si el token fue rechazado (por ejemplo, venció antes de lo
     * esperado), pide uno nuevo y reintenta una sola vez.
     */
    public Response resolve(AccessMethod method, String identifier, String visitorPlate, String deviceId) {
        Request request = new Request(method, identifier, deviceId, null, visitorPlate);
        try {
            return post(request);
        } catch (HttpClientErrorException.Unauthorized expired) {
            invalidateToken();
            return postOrFail(request);
        } catch (RestClientException e) {
            throw new ExternalServiceException(UNREACHABLE, e);
        }
    }

    private Response postOrFail(Request request) {
        try {
            return post(request);
        } catch (RestClientException e) {
            throw new ExternalServiceException(UNREACHABLE, e);
        }
    }

    private Response post(Request request) {
        Response response = accessClient.post()
                .uri("/api/v1/access")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Response.class);
        if (response == null) {
            throw new ExternalServiceException("access-service respondió sin contenido", null);
        }
        return response;
    }

    private synchronized String token() {
        if (cachedToken != null && clock.instant().isBefore(tokenExpiresAt.minus(TOKEN_MARGIN))) {
            return cachedToken;
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ExternalServiceException(NOT_CONFIGURED, null);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        TokenResponse token;
        try {
            token = tokenClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException(
                    "No se pudo autenticar contra Keycloak para llamar a access-service", e);
        }
        if (token == null || token.accessToken() == null) {
            throw new ExternalServiceException("Keycloak no devolvió un token", null);
        }
        Duration ttl = token.expiresIn() != null ? Duration.ofSeconds(token.expiresIn()) : DEFAULT_TOKEN_TTL;
        cachedToken = token.accessToken();
        tokenExpiresAt = clock.instant().plus(ttl);
        return cachedToken;
    }

    private synchronized void invalidateToken() {
        cachedToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    /** Cuerpo de POST /api/v1/access. */
    public record Request(
            AccessMethod accessMethod,
            String identifier,
            String deviceId,
            LocalDateTime occurredAt,
            String visitorPlate
    ) {
    }

    /** Respuesta de access-service. ticketPdf (base64) no se usa: el panel genera su propio PDF del ticket. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(AccessResult result, AccessEventType eventType, String message, String ticketPdf) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") Long expiresIn) {
    }
}
