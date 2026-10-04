package com.deathfrog.mctradepost.api.items.datacomponent;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Verifies directional Route Survey progress and reset behavior. */
class RouteSurveyRecordTest
{
    @SuppressWarnings("null")
    @Test
    void surveyProgressIsDirectionalAndResettable()
    {
        DimPos origin = new DimPos(Level.OVERWORLD, new BlockPos(1, 64, 2));
        DimPos destination = new DimPos(Level.OVERWORLD, new BlockPos(100, 70, 200));
        RouteSurveyRecord partial = RouteSurveyRecord.empty().withOrigin(origin, "Origin", 1);
        assertFalse(partial.isComplete());
        RouteSurveyRecord complete = partial.withDestination(destination, "Destination", 2);
        assertTrue(complete.isComplete());
        assertEquals(origin, complete.origin().orElseThrow());
        assertEquals(destination, complete.destination().orElseThrow());
        assertFalse(RouteSurveyRecord.empty().isComplete());
    }
}
