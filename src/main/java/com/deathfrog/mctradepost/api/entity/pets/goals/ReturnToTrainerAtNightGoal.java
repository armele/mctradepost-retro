package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ReturnToTrainerAtNightGoal<T extends Animal> extends Goal
{
    private static final String NBT_LAST_NIGHT_CYCLE = "mctradepost:lastNightCycle";

    // Tuneables
    private static final double START_DISTANCE = 2.0;
    private static final double ARRIVE_DISTANCE = 1.5;
    private static final int NIGHT_START = 12000;               // inclusive
    private static final int DAY_LENGTH = 24000;
    private static final int REPATH_COOLDOWN_TICKS = 40;        // 2 seconds @ 20tps

    private final T animal;
    private final @Nonnull BlockPos trainerPos;

    private boolean hasArrived = false;
    private int lastMoveCommandTick = -REPATH_COOLDOWN_TICKS;

    public ReturnToTrainerAtNightGoal(final T animal, final @Nonnull BlockPos trainerPos)
    {
        this.animal = animal;
        this.trainerPos = trainerPos;
        this.setFlags(Objects.requireNonNull(EnumSet.of(Goal.Flag.MOVE)));
    }

    /**
     * Gets the last night cycle saved in the animal's persistent data.
     * If no such data exists, returns -1L.
     * @return the last night cycle, or -1L if no data exists
     */
    private long getLastNightCycle()
    {
        final CompoundTag tag = animal.getPersistentData();
        return tag.contains(NBT_LAST_NIGHT_CYCLE) ? tag.getLong(NBT_LAST_NIGHT_CYCLE) : -1L;
    }

    /**
     * Sets the last night cycle in the animal's persistent data.
     *
     * @param cycle the last night cycle to save
     */
    private void setLastNightCycle(final long cycle)
    {
        animal.getPersistentData().putLong(NBT_LAST_NIGHT_CYCLE, cycle);
    }

    private static long cycleOf(final long dayTime) { return dayTime / DAY_LENGTH; }
    private static long timeOfDay(final long dayTime) { return dayTime % DAY_LENGTH; }


    /**
     * Determines if the given level is currently at night.
     *
     * Night is defined as any time of day greater than or equal to {@link #NIGHT_START}.
     *
     * @param level the level to check
     * @return true if the level is currently at night, false otherwise
     */
    private boolean isNight(final Level level)
    {
        return timeOfDay(level.getDayTime()) >= NIGHT_START;
    }

    /**
     * Checks if the animal is currently within a certain radius of the trainer's position.
     * 
     * @param radius the radius to check
     * @return true if the animal is within the given radius of the trainer, false otherwise
     */
    private boolean isNearTrainer(final double radius)
    {
        final Vec3 center = Objects.requireNonNull(Vec3.atCenterOf(trainerPos));
        return animal.blockPosition().closerToCenterThan(center, radius);
    }

    /**
     * Issues a move to command to the animal's navigation system to move to the trainer's position.
     * The target position is set to the center of the trainer's block position, and the speed is set to 1.0.
     * The last move command tick is updated to the current animal tick count.
     */
    private void issueMoveToTrainer()
    {
        animal.getNavigation().moveTo(
            trainerPos.getX() + 0.5,
            trainerPos.getY(),
            trainerPos.getZ() + 0.5,
            1.0
        );
        lastMoveCommandTick = animal.tickCount;
    }

    /**
     * Determines if the goal can be used.
     * 
     * This goal can be used if the animal is not on the client side, if the animal is alive, if the trainer's position is loaded, and if it is currently at night.
     * Additionally, the goal will not be used if the animal has already completed this night (persisted).
     * If the animal is already close enough to the trainer, the goal will mark completion immediately and not start pathing.
     * 
     * @return true if the goal can be used, false otherwise.
     */
    @Override
    public boolean canUse()
    {
        final Level level = animal.level();
        if (level.isClientSide || !animal.isAlive() || !level.isLoaded(trainerPos))
        {
            return false;
        }

        if (!isNight(level))
        {
            return false;
        }

        final long currentCycle = cycleOf(level.getDayTime());
        if (currentCycle == getLastNightCycle())
        {
            return false; // already completed this night (persisted)
        }

        // If we’re already close enough, mark completion immediately and don’t start pathing
        if (isNearTrainer(START_DISTANCE))
        {
            setLastNightCycle(currentCycle);
            return false;
        }

        return true;
    }

    /**
     * Determines if the goal should continue to run.
     * This goal will continue to run if the animal is not on the client side, if the animal is alive, if the trainer's position is loaded, and if it is currently at night.
     * Additionally, the goal will not continue if the animal has already arrived.
     * The navigation state is not checked, so the goal will continue to run even if the animal's navigation is not running.
     * @return true if the goal should continue to run, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        final Level level = animal.level();
        if (level.isClientSide || !animal.isAlive() || !level.isLoaded(trainerPos))
        {
            return false;
        }

        // Keep running during the night until we arrive (don’t depend on navigation state)
        return !hasArrived && isNight(level);
    }


    /**
     * Starts the goal by setting hasArrived to false and issuing a move command to the trainer.
     */
    @Override
    public void start()
    {
        hasArrived = false;
        issueMoveToTrainer();
    }

    /**
     * Called once per tick while this goal is active.
     * 
     * On each tick, it checks if the animal is near the trainer (within a certain distance). If so, it marks the goal as finished and stops the navigation.
     * Otherwise, it checks if the navigation is done or failed, and if so, re-issues a move command to the trainer at a rate-limited interval.
     */
    @Override
    public void tick()
    {
        final Level level = animal.level();
        if (level.isClientSide || !animal.isAlive() || !level.isLoaded(trainerPos))
        {
            return;
        }

        // Arrival check
        if (isNearTrainer(ARRIVE_DISTANCE))
        {
            hasArrived = true;
            setLastNightCycle(cycleOf(level.getDayTime()));
            animal.getNavigation().stop();
            return;
        }

        // If nav is done/failed, or just periodically, re-issue moveTo (rate-limited)
        final boolean cooldownReady = (animal.tickCount - lastMoveCommandTick) >= REPATH_COOLDOWN_TICKS;
        if (cooldownReady && animal.getNavigation().isDone())
        {
            issueMoveToTrainer();
        }
    }


    /**
     * Called when this goal is stopped, either due to completion, failure, or interruption.
     * Resets the hasArrived flag to false, and optionally stops the animal's navigation.
     * Goal transitions may share the MOVE flag, so we don't always stop navigation here to allow for smooth transitions.
     */
    @Override
    public void stop()
    {
        hasArrived = false;
    }
}
