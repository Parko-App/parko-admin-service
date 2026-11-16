package com.parko.admin.service.dto;

import java.time.LocalDate;

public record AccessDay(LocalDate date, long registeredEntries, long registeredExits,
                        long visitorEntries, long visitorExits) {
}
