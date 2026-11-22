package com.parko.admin.service.service;

import com.parko.admin.service.exception.ExternalServiceException;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccessServiceClientTest {

    private static final String ACCESS_URL = "http://access.local/api/v1/access";
    private static final String TOKEN_URL = "http://keycloak.local/token";
    private static final String TOKEN_OK = "{\"access_token\":\"tok-1\",\"expires_in\":300}";
    private static final String ENTRY_OK =
            "{\"result\":\"AUTHORIZED\",\"eventType\":\"ENTRY\",\"message\":null,\"ticketPdf\":\"UERG\",\"extra\":1}";

    /** Reloj que se puede adelantar para probar el vencimiento del token. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-21T12:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MockRestServiceServer server;
    private MutableClock clock;
    private AccessServiceClient client;

    private AccessServiceClient clientWith(String clientId, String clientSecret) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        return new AccessServiceClient(
                builder.clone().baseUrl("http://access.local").build(),
                builder.clone().build(),
                clock, TOKEN_URL, clientId, clientSecret);
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        client = clientWith("access-service", "s3cret");
    }

    @Test
    void resolve_asksKeycloakForATokenAndCallsAccessServiceWithIt() {
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=client_credentials"),
                        org.hamcrest.Matchers.containsString("client_id=access-service"),
                        org.hamcrest.Matchers.containsString("client_secret=s3cret"))))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ACCESS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(jsonPath("$.accessMethod").value("PLATE"))
                .andExpect(jsonPath("$.identifier").value("AB123CD"))
                .andExpect(jsonPath("$.deviceId").value("admin:uid-1"))
                .andRespond(withSuccess(ENTRY_OK, MediaType.APPLICATION_JSON));

        AccessServiceClient.Response response = client.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:uid-1");

        assertEquals(AccessResult.AUTHORIZED, response.result());
        assertEquals(AccessEventType.ENTRY, response.eventType());
        assertEquals("UERG", response.ticketPdf());
        assertNull(response.message());
        server.verify();
    }

    @Test
    void resolve_reusesTheTokenWhileItIsValid() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(ACCESS_URL))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withSuccess(ENTRY_OK, MediaType.APPLICATION_JSON));

        client.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:u");
        clock.advanceSeconds(200); // el token dura 300 s
        client.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:u");

        server.verify();
    }

    @Test
    void resolve_asksForANewTokenWhenTheCurrentOneIsAboutToExpire() {
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(TOKEN_URL))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(ACCESS_URL))
                .andRespond(withSuccess(ENTRY_OK, MediaType.APPLICATION_JSON));

        client.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:u");
        clock.advanceSeconds(295); // quedan 5 s: menos que el margen de 10 s
        client.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:u");

        server.verify();
    }

    @Test
    void resolve_whenAccessServiceRejectsTheToken_getsANewOneAndRetriesOnce() {
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(TOKEN_URL))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ACCESS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo(ACCESS_URL)).andRespond(withSuccess(ENTRY_OK, MediaType.APPLICATION_JSON));

        AccessServiceClient.Response response = client.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:u");

        assertEquals(AccessResult.AUTHORIZED, response.result());
        server.verify();
    }

    @Test
    void resolve_ticketMethodSendsTheSessionIdAsIdentifier() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ACCESS_URL))
                .andExpect(jsonPath("$.accessMethod").value("TICKET"))
                .andExpect(jsonPath("$.identifier").value("11111111-1111-1111-1111-111111111111"))
                .andRespond(withSuccess(
                        "{\"result\":\"AUTHORIZED\",\"eventType\":\"EXIT\"}", MediaType.APPLICATION_JSON));

        AccessServiceClient.Response response =
                client.resolve(AccessMethod.TICKET, "11111111-1111-1111-1111-111111111111", null, "admin:u");

        assertEquals(AccessEventType.EXIT, response.eventType());
        server.verify();
    }

    @Test
    void resolve_withoutCredentials_failsWithAClearMessageAndNeverCallsOut() {
        AccessServiceClient unconfigured = clientWith("", "");

        ExternalServiceException error = assertThrows(ExternalServiceException.class,
                () -> unconfigured.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:u"));

        assertEquals(AccessServiceClient.NOT_CONFIGURED, error.getMessage());
        server.verify(); // no hubo ningún pedido
    }

    @Test
    void resolve_whenKeycloakFails_throwsExternalServiceException() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ExternalServiceException error = assertThrows(ExternalServiceException.class,
                () -> client.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:u"));

        assertEquals("No se pudo autenticar contra Keycloak para llamar a access-service", error.getMessage());
    }

    @Test
    void resolve_whenAccessServiceFails_throwsExternalServiceException() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ACCESS_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ExternalServiceException error = assertThrows(ExternalServiceException.class,
                () -> client.resolve(AccessMethod.PLATE, "AB123CD", null, "admin:u"));

        assertEquals(AccessServiceClient.UNREACHABLE, error.getMessage());
    }
}
