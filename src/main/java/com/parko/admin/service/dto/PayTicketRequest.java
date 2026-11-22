package com.parko.admin.service.dto;

import java.math.BigDecimal;

/**
 * Cobro en efectivo de un ticket desde el panel. El monto es opcional: si se informa, tiene que coincidir con el
 * total a cobrar del ticket (el de la transacción pendiente que dejó el ingreso), y si no coincide se rechaza.
 */
public record PayTicketRequest(BigDecimal amount) {
}
