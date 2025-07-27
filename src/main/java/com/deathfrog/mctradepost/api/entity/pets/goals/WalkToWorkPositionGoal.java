package com.deathfrog.mctradepost.api.entity.pets.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class WalkToWorkPositionGoal extends Goal
{
    private final PathfinderMob mob;
    private final BlockPos targetPos;
    private final double speedModifier;
    private final double stopDistanceSq; // squared distance to stop

    public WalkToWorkPositionGoal(PathfinderMob mob, BlockPos targetPos, double speed, double stopDistance)
    {
        this.mob = mob;
        this.targetPos = targetPos;
        this.speedModifier = speed;
        this.stopDistanceSq = stopDistance * stopDistance;
    }

    @Override
    public boolean canUse()
    {
        if (BlockPos.ZERO.equals(targetPos)) return false;

        long timeOfDay = mob.level().getDayTime() % 24000;
        if (timeOfDay >= 12000) return false; // only run during day

        return !isWithinDistance();
    }

    @Override
    public boolean canContinueToUse()
    {
        if (BlockPos.ZERO.equals(targetPos)) return false;
        
        return !isWithinDistance() && !mob.getNavigation().isDone();
    }

    @Override
    public void start()
    {
        mob.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, speedModifier);
    }

    private boolean isWithinDistance()
    {
        return mob.position().distanceToSqr(Vec3.atCenterOf(targetPos)) <= stopDistanceSq;
    }
}
