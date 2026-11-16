package com.parko.admin.service.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClockConfigTest {

    @Test
    void clock_usesConfiguredZone() {
        Clock clock = new ClockConfig().clock("America/Argentina/Buenos_Aires");

        assertEquals("America/Argentina/Buenos_Aires", clock.getZone().getId());
    }
}
