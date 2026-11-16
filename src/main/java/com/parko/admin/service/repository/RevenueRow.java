package com.parko.admin.service.repository;

import java.math.BigDecimal;

public interface RevenueRow {

    String getDay();

    String getSessionType();

    BigDecimal getTotal();
}
