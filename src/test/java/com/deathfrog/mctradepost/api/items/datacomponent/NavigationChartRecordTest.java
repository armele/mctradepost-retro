package com.deathfrog.mctradepost.api.items.datacomponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

class NavigationChartRecordTest
{
    @Test
    void persistentCodecRoundTripsSelectedDock()
    {
        @SuppressWarnings("null")
        NavigationChartRecord original =
            new NavigationChartRecord(Optional.of(new DimPos(Level.OVERWORLD, new BlockPos(12, 63, -8))), "Harbor");

        var json = NavigationChartRecord.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        
        NavigationChartRecord decoded = NavigationChartRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        
        assertEquals(original, decoded);
    }
}
