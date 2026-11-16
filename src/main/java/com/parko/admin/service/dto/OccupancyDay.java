package com.parko.admin.service.dto;

import java.time.LocalDate;

public record OccupancyDay(LocalDate date, int peak, double average) {
}
