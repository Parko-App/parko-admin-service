package com.parko.admin.service.dto;

import java.time.LocalDate;
import java.util.List;

public record OccupancyReport(LocalDate from, LocalDate to, List<OccupancyDay> days, int peak, double average) {
}
