package com.deathfrog.mctradepost.api.entity.pets.navigation;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class VanillaNavResult implements IPetNavResult
{
    private final Mob mob;
    @Nonnull private final Entity target;
    private final double acceptableDist;

    private boolean moveIssuedOk;
    private int startTick;
    private double startDist;

    public VanillaNavResult(Mob mob, @Nonnull Entity target, boolean moveIssuedOk, double acceptableDist)
    {
        this.mob = mob;
        this.target = target;
        this.moveIssuedOk = moveIssuedOk;
        this.acceptableDist = acceptableDist;
        this.startTick = mob.tickCount;
        this.startDist = mob.distanceTo(target);
    }

    @Override
    public boolean failedToReachDestination()
    {
        // If we couldn't even create/issue a path, treat as failure.
        if (!moveIssuedOk)
            return true;

        // If we're close enough, it's not a failure.
        if (mob.distanceTo(target) <= acceptableDist)
            return false;

        // If navigation is done but we're still not close, that's a failure signal.
        if (mob.getNavigation().isDone())
            return true;

        // "stalled" heuristic: after N ticks, no progress -> failure.
        final int maxNoProgressTicks = 40;
        if ((mob.tickCount - startTick) > maxNoProgressTicks)
        {
            double nowDist = mob.distanceTo(target);
            if (nowDist >= (startDist - 0.25)) // barely improved
                return true;
        }

        return false;
    }
}
