package com.parko.admin.service.dto;

import java.time.LocalDate;
import java.util.List;

public record AccessReport(LocalDate from, LocalDate to, List<AccessDay> days, long registeredEntries,
                           long registeredExits, long visitorEntries, long visitorExits) {
}
