package com.parko.admin.service.repository;

import java.math.BigDecimal;

public interface RevenueRow {

    String getDay();

    /** Origen del dinero: TOPUP (carga de saldo) o CASH (cobro en efectivo de un ticket). */
    String getSource();

    BigDecimal getTotal();
}
