package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

class ScrapOutputBufferTest
{
    @Test
    void fullOutputUsesBoundedRetriesAndEmitsAfterSpaceIsFreed()
    {
        final ScrapOutputBuffer buffer = new ScrapOutputBuffer();
        final AtomicInteger scans = new AtomicInteger();
        buffer.addPoints(32.0D, 16, 64, requested -> { scans.incrementAndGet(); return 0; });
        assertEquals(1, scans.get());

        for (int tick = 0; tick < ScrapOutputBuffer.RETRY_COOLDOWN_COLONY_TICKS - 1; tick++)
        {
            buffer.onColonyTick(16, 64, requested -> { scans.incrementAndGet(); return requested; });
        }
        assertEquals(1, scans.get());
        assertEquals(32.0D, buffer.points());

        final ScrapOutputBuffer.Emission retried = buffer.onColonyTick(16, 64, requested -> {
            scans.incrementAndGet();
            return requested;
        });
        assertEquals(2, scans.get());
        assertEquals(2, retried.produced());
        assertEquals(0.0D, buffer.points());
    }

    @Test
    void emitsMultipleStacksAndKeepsFractionalPoints()
    {
        final ScrapOutputBuffer buffer = new ScrapOutputBuffer();
        final AtomicInteger calls = new AtomicInteger();
        final ScrapOutputBuffer.Emission emission = buffer.addPoints(130.5D, 1, 64, requested -> {
            calls.incrementAndGet();
            return requested;
        });
        assertEquals(130, emission.produced());
        assertEquals(3, calls.get());
        assertEquals(0.5D, buffer.points());
    }

    @Test
    void thresholdChangeBypassesBlockedCooldown()
    {
        final ScrapOutputBuffer buffer = new ScrapOutputBuffer();
        buffer.addPoints(12.5D, 4, 64, requested -> 0);

        final ScrapOutputBuffer.Emission emission = buffer.onColonyTick(6, 64, requested -> requested);
        assertEquals(2, emission.produced());
        assertEquals(0.5D, buffer.points());
    }

    @Test
    void saveAndLoadPreservesFractionalAndCompletedPilePoints()
    {
        final ScrapOutputBuffer original = new ScrapOutputBuffer();
        original.addPoints(48.75D, 16, 64, requested -> 0);
        final CompoundTag saved = new CompoundTag();
        original.write(saved, "points");

        final ScrapOutputBuffer restored = new ScrapOutputBuffer();
        restored.read(saved, "points");
        assertEquals(48.75D, restored.points());
        final ScrapOutputBuffer.Emission emission = restored.onColonyTick(16, 64, requested -> requested);
        assertEquals(3, emission.produced());
        assertEquals(0.75D, restored.points());
    }

    @Test
    void partialInsertionConsumesOnlyAcceptedPiles()
    {
        final ScrapOutputBuffer buffer = new ScrapOutputBuffer();
        final ScrapOutputBuffer.Emission emission = buffer.addPoints(80.0D, 16, 64, requested -> 2);
        assertEquals(2, emission.produced());
        assertEquals(48.0D, buffer.points());
    }
}
