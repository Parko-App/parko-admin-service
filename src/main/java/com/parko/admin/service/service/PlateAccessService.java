package com.parko.admin.service.service;

import com.parko.admin.service.dto.PlateEntryResponse;
import com.parko.admin.service.dto.PlateExitResponse;
import com.parko.admin.service.exception.ConflictException;
import com.parko.admin.service.repository.AdminParkingSessionRepository;
import com.parko.admin.service.repository.AdminTicketRepository;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import com.parko.domain.lib.model.SessionType;
import com.parko.domain.lib.model.TicketStatus;
import com.parko.persistence.core.model.entity.ParkingSessionEntity;
import com.parko.persistence.core.model.entity.TicketEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * Ingreso y egreso generados a mano por un administrador, para quien no tiene patente legible (o no pasa por la
 * cámara). Las reglas de acceso las sigue decidiendo access-service: este servicio solo valida lo que el panel
 * tiene que garantizar antes de pedírselo y arma la respuesta.
 *
 * No es transaccional a propósito: no se quiere retener una conexión a la base mientras se espera a access-service.
 */
@Service
public class PlateAccessService {

    public static final String DEVICE_PREFIX = "admin:";

    private static final Pattern OLD_PLATE = Pattern.compile("^[A-Z]{3}\\d{3}$");
    private static final Pattern MERCOSUR_PLATE = Pattern.compile("^[A-Z]{2}\\d{3}[A-Z]{2}$");

    private final AccessServiceClient accessClient;
    private final AdminParkingSessionRepository sessionRepository;
    private final AdminTicketRepository ticketRepository;

    public PlateAccessService(AccessServiceClient accessClient,
                              AdminParkingSessionRepository sessionRepository,
                              AdminTicketRepository ticketRepository) {
        this.accessClient = accessClient;
        this.sessionRepository = sessionRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Genera el ingreso de una patente. access-service la trata como usuario registrado si el vehículo está
     * registrado y activo, y como visitante si no. Si la patente ya está adentro se rechaza: con una sesión activa,
     * access-service interpretaría el pedido como un egreso, que no es lo que el administrador pidió.
     */
    public PlateEntryResponse enter(String rawPlate, String operatorUid) {
        String plate = normalize(rawPlate);
        if (!sessionRepository.findActiveByPlate(plate).isEmpty()) {
            throw new ConflictException("La patente " + plate + " ya tiene un ingreso activo");
        }

        AccessServiceClient.Response response =
                accessClient.resolve(AccessMethod.PLATE, plate, null, DEVICE_PREFIX + operatorUid);
        if (response.result() != AccessResult.AUTHORIZED) {
            throw new ConflictException(messageOrDefault(response, "access-service no autorizó el ingreso"));
        }
        if (response.eventType() != AccessEventType.ENTRY) {
            throw new ConflictException(
                    "La patente " + plate + " ya estaba adentro: se registró un egreso en lugar de un ingreso");
        }

        ParkingSessionEntity session = activeSession(plate)
                .orElseThrow(() -> new IllegalStateException("access-service autorizó el ingreso pero no hay sesión activa"));
        TicketEntity ticket = ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId())
                .orElseThrow(() -> new IllegalStateException("El ingreso se generó pero no se encontró su ticket"));

        return new PlateEntryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getStatus(),
                session.getSessionType(),
                plate,
                entryMessage(session.getSessionType(), ticket.getStatus()));
    }

    /**
     * Genera el egreso de una patente con una sesión activa. Solo se permite si su ticket ya fue pagado.
     * Se resuelve con el método TICKET (el de un tótem con ticket), así queda registrado como acceso manual.
     */
    public PlateExitResponse exit(String rawPlate, String operatorUid) {
        String plate = normalize(rawPlate);
        ParkingSessionEntity session = activeSession(plate)
                .orElseThrow(() -> new NoSuchElementException("La patente " + plate + " no tiene un ingreso activo"));
        TicketEntity ticket = ticketRepository.findFirstByParkingSession_IdOrderByIssuedAtDesc(session.getId())
                .orElseThrow(() -> new NoSuchElementException("La patente " + plate + " no tiene un ticket"));
        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new ConflictException("El ticket " + ticket.getTicketNumber() + " no está pagado ("
                    + describe(ticket.getStatus()) + "): no se puede generar el egreso");
        }

        AccessServiceClient.Response response = accessClient.resolve(
                AccessMethod.TICKET, session.getId().toString(), null, DEVICE_PREFIX + operatorUid);
        if (response.result() != AccessResult.AUTHORIZED) {
            throw new ConflictException(messageOrDefault(response, "access-service no autorizó el egreso"));
        }

        return new PlateExitResponse(ticket.getId(), ticket.getTicketNumber(), session.getSessionType(), plate,
                "Egreso generado para " + plate);
    }

    private java.util.Optional<ParkingSessionEntity> activeSession(String plate) {
        List<ParkingSessionEntity> sessions = sessionRepository.findActiveByPlate(plate);
        return sessions.stream().findFirst();
    }

    /** Sin espacios ni guiones y en mayúsculas; acepta el formato viejo (ABC123) y el Mercosur (AB123CD). */
    static String normalize(String rawPlate) {
        if (rawPlate == null || rawPlate.isBlank()) {
            throw new IllegalArgumentException("La patente es requerida");
        }
        String plate = rawPlate.replaceAll("[\\s-]+", "").toUpperCase();
        if (!OLD_PLATE.matcher(plate).matches() && !MERCOSUR_PLATE.matcher(plate).matches()) {
            throw new IllegalArgumentException("La patente no es válida. Usá el formato ABC123 o AB123CD");
        }
        return plate;
    }

    private static String entryMessage(SessionType type, TicketStatus ticketStatus) {
        if (type == SessionType.VISITOR) {
            return "Ingreso de visitante generado. Entregá el ticket para que pueda pagar.";
        }
        return ticketStatus == TicketStatus.PENDING_PAYMENT
                ? "Ingreso de usuario registrado generado. El ticket quedó pendiente de pago (saldo insuficiente)."
                : "Ingreso de usuario registrado generado.";
    }

    private static String describe(TicketStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> "pendiente de pago";
            case USED -> "ya utilizado";
            case CANCELLED -> "cancelado";
            case PAID -> "pagado";
        };
    }

    private static String messageOrDefault(AccessServiceClient.Response response, String fallback) {
        return response.message() != null && !response.message().isBlank() ? response.message() : fallback;
    }
}
