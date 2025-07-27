package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ReturnToTrainerAtNightGoal<T extends Animal> extends Goal
{
    private final T animal;
    private final BlockPos trainerPos;
    private boolean hasArrived = false;
    private long lastNightCycle = -1;

    public ReturnToTrainerAtNightGoal(T animal, BlockPos trainerPos)
    {
        this.animal = animal;
        this.trainerPos = trainerPos;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        Level level = animal.level();
        if (level.isClientSide) return false;

        long time = level.getDayTime();
        long currentCycle = time / 24000;
        long timeOfDay = time % 24000;

        if (timeOfDay < 12000) return false; // Only active after sundown
        if (currentCycle == lastNightCycle) return false; // Already completed this night

        return !animal.blockPosition().closerToCenterThan(Vec3.atCenterOf(trainerPos), 2.0);
    }

    @Override
    public boolean canContinueToUse()
    {
        long time = animal.level().getDayTime() % 24000;
        return !hasArrived && time >= 12000 && !animal.getNavigation().isDone();
    }

    @Override
    public void start()
    {
        hasArrived = false;
        animal.getNavigation().moveTo(trainerPos.getX() + 0.5, trainerPos.getY(), trainerPos.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick()
    {
        if (animal.blockPosition().closerToCenterThan(Vec3.atCenterOf(trainerPos), 1.5))
        {
            hasArrived = true;

            long time = animal.level().getDayTime();
            lastNightCycle = time / 24000; // Mark night as completed
        }
    }

    @Override
    public void stop()
    {
        hasArrived = false;
    }
}
