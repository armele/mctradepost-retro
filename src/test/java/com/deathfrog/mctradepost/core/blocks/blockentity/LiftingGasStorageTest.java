package com.deathfrog.mctradepost.core.blocks.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies bounded and simulated operations of the shared Lifting Gas tank contract. */
class LiftingGasStorageTest
{
    @Test void fillAndDrainAreBoundedAndSupportSimulation()
    {
        TestTank tank = new TestTank();
        assertEquals(1000, tank.fillGas(1000, true));
        assertEquals(0, tank.gasAmount());
        assertEquals(4000, tank.fillGas(5000, false));
        assertEquals(4000, tank.gasAmount());
        assertEquals(1500, tank.drainGas(1500, true));
        assertEquals(4000, tank.gasAmount());
        assertEquals(4000, tank.drainGas(5000, false));
        assertEquals(0, tank.gasAmount());
    }

    private static final class TestTank implements LiftingGasStorage
    {
        private int amount;
        @Override public int gasAmount() { return amount; }
        @Override public int gasCapacity() { return 4000; }
        @Override public void setGasAmount(int amount) { this.amount = amount; }
    }
}
