package com.deathfrog.mctradepost.core.colony.buildings.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies resource accounting for escrowed outpost returns. */
class OutpostReturnShipmentTest
{
    /** Air returns debit twice the existing one-way route cost with overflow protection. */
    @Test
    void roundTripGasCostIsTwiceOneWayCost()
    {
        assertEquals(0, BuildingStationExportModule.roundTripGasCost(0));
        assertEquals(24, BuildingStationExportModule.roundTripGasCost(12));
        assertEquals(Integer.MAX_VALUE, BuildingStationExportModule.roundTripGasCost(Integer.MAX_VALUE));
    }
}
