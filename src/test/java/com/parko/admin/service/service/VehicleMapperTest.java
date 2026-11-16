package com.parko.admin.service.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VehicleMapperTest {

    @Test
    void visiblePlate_stripsReleaseSuffix() {
        assertEquals("AB123CD", VehicleMapper.visiblePlate("AB123CD#" + UUID.randomUUID()));
    }

    @Test
    void visiblePlate_withoutSuffix_isUnchanged() {
        assertEquals("AB123CD", VehicleMapper.visiblePlate("AB123CD"));
    }

    @Test
    void visiblePlate_null_isNull() {
        assertNull(VehicleMapper.visiblePlate(null));
    }

    @Test
    void releasedPlate_appendsVehicleId() {
        UUID id = UUID.randomUUID();

        assertEquals("AB123CD#" + id, VehicleMapper.releasedPlate("AB123CD", id));
    }
}
