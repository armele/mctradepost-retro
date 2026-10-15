package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.List;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;

class WaterRouteRegistryTest
{
    @Test
    void presentsThePersistedPathInEitherDockDirection()
    {
        BlockPos first = new BlockPos(1, 62, 1);
        BlockPos middle = new BlockPos(2, 62, 1);
        BlockPos second = new BlockPos(3, 62, 1);
        WaterRouteRegistry.WaterLink link = new WaterRouteRegistry.WaterLink(first, second,
            List.of(first, middle, second), WaterRouteRegistry.Status.OPERATIONAL, 10L, null, null);

        assertEquals(List.of(first, middle, second), link.pathFrom(first));
        assertEquals(List.of(second, middle, first), link.pathFrom(second));
        assertEquals(2, link.distance());
    }

    @Test
    void brokenLinksAreNeverUsable()
    {
        BlockPos first = BlockPos.ZERO;
        BlockPos second = first.east();
        WaterRouteRegistry.WaterLink link = new WaterRouteRegistry.WaterLink(first, second,
            List.of(first, second), WaterRouteRegistry.Status.BROKEN, 10L, first, second);

        assertFalse(link.usable());
    }
}
