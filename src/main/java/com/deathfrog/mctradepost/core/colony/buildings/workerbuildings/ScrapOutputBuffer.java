package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.function.IntUnaryOperator;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;

/**
 * Persistent Scrap Point balance plus transient, bounded output retry state.
 * The inserter returns the number of piles it actually accepted, allowing
 * callers to commit directly without a separate simulation pass.
 */
final class ScrapOutputBuffer
{
    static final int RETRY_COOLDOWN_COLONY_TICKS = 4;

    private double points;
    private int retryCooldown;
    private int lastThreshold = -1;

    double points()
    {
        return points;
    }

    void read(final CompoundTag tag, final @Nonnull String key)
    {
        points = Math.max(0.0D, tag.getDouble(key));
        retryCooldown = 0;
        lastThreshold = -1;
    }

    void write(final CompoundTag tag, final @Nonnull String key)
    {
        tag.putDouble(key, points);
    }

    Emission addPoints(final double addedPoints, final int threshold, final int maxStackSize, final IntUnaryOperator inserter)
    {
        lastThreshold = Math.max(1, threshold);
        final double acceptedPoints = Math.max(0.0D, addedPoints);
        if (acceptedPoints > 0.0D)
        {
            points += acceptedPoints;
        }

        final Emission emission = retryCooldown == 0
            ? emit(threshold, maxStackSize, inserter)
            : Emission.NONE;
        return new Emission(emission.produced(), acceptedPoints > 0.0D || emission.changed());
    }

    Emission onColonyTick(final int threshold, final int maxStackSize, final IntUnaryOperator inserter)
    {
        final int boundedThreshold = Math.max(1, threshold);
        if (boundedThreshold != lastThreshold)
        {
            lastThreshold = boundedThreshold;
            retryCooldown = 0;
        }
        else if (retryCooldown > 0)
        {
            retryCooldown--;
            if (retryCooldown > 0)
            {
                return Emission.NONE;
            }
        }

        return emit(boundedThreshold, maxStackSize, inserter);
    }

    private Emission emit(final int threshold, final int maxStackSize, final IntUnaryOperator inserter)
    {
        final int boundedThreshold = Math.max(1, threshold);
        final int boundedMaxStack = Math.max(1, maxStackSize);
        int available = (int) Math.min(Integer.MAX_VALUE, Math.floor(points / boundedThreshold));
        if (available <= 0)
        {
            retryCooldown = 0;
            return Emission.NONE;
        }

        int produced = 0;
        while (available > 0)
        {
            final int requested = Math.min(available, boundedMaxStack);
            final int inserted = Math.max(0, Math.min(requested, inserter.applyAsInt(requested)));
            if (inserted > 0)
            {
                points -= (double) inserted * boundedThreshold;
                produced += inserted;
                available -= inserted;
            }
            if (inserted < requested)
            {
                retryCooldown = RETRY_COOLDOWN_COLONY_TICKS;
                break;
            }
        }

        return new Emission(produced, produced > 0);
    }

    record Emission(int produced, boolean changed)
    {
        private static final Emission NONE = new Emission(0, false);
    }
}
