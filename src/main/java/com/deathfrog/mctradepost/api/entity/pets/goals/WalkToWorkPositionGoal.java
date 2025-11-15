package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETGOALS;

import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.ItemHandlerHelpers;
import com.deathfrog.mctradepost.api.util.PathingUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkAnimalTrainer;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class WalkToWorkPositionGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAX_TRIES = 10;
    private static final int RETRY_COOLDOWN = 10;
    private static final String NBT_LAST_RUN_DAY = "mctradepost:walk_to_work_last_day";
    private final P mob;
    private BlockPos targetPos;
    private final double speedModifier;
    private final double stopDistanceSq; // squared distance to stop
    private boolean emergencyThisRun = false;
    private int pathTries = 0;
    private int feedingTries = 0;
    private int retryCooldown = 0;
    private int ticksRunning = 0;
    private static final int HARD_TIMEOUT_TICKS = 20 * 30; // ~30 seconds
    private Vec3 lastProgress = null;
    private int staleTicks = 0;
    private int chestCheckCooldown = 0;

    public WalkToWorkPositionGoal(P mob, @Nonnull BlockPos workPos, double speed, double stopDistance)
    {
        this.mob = mob;
        this.targetPos = PathingUtil.findTopOfWaterColumn(mob.level(), workPos);

        if (this.targetPos == null)
        {
            this.targetPos = workPos;
        }

        this.speedModifier = speed;
        this.stopDistanceSq = stopDistance * stopDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /**
     * Determines if the goal can be used.
     * 
     * This goal can be used if the pet is not on the client side, if the target position is not BlockPos.ZERO, if the chunk is loaded, and if one of the following conditions is true:
     * - the pet is injured 
     * - it is daytime and the pet has not already been to the work location today, and it is not within the stop distance of the target position.
     * 
     * @return true if the goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        if (mob.level().isClientSide()) return false;
        if (!mob.isAlive()) return false;
        if (BlockPos.ZERO.equals(targetPos) || targetPos == null) return false;
    
        if (!mob.level().isLoaded(targetPos)) return false; 
        if (!mob.level().isInWorldBounds(targetPos)) return false; 

        final long timeOfDay = mob.level().getDayTime() % 24000L;

        long today = currentDay();
        if (hasRunToday(today)) return false;

        // Emergency case: run any time of day if injured and no run made today.
        emergencyThisRun = mob.getHealth() < mob.getMaxHealth();
        if (emergencyThisRun)
        {
            TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Pet {} WalkToWorkPositionGoal.canUse: Emergency run to: {}", mob.getUUID(), targetPos));

            return true;
        }

        // Otherwise run only during the day.
        if (timeOfDay >= 12000)
        {
            return false;
        }

        return !isWithinDistance();
    }

    /**
     * Determines if the goal can continue to run.
     * 
     * This goal can continue to run if the pet is not on the client side, if the target position is not BlockPos.ZERO, if the pet is injured, or if the pet is not within the stop distance of the target position and the navigation is not done.
     * 
     * @return true if the goal can continue to run, false otherwise
     */
    @Override
    public boolean canContinueToUse() 
    {
        if (mob.level().isClientSide()) return false;
        if (!mob.isAlive()) return false;
        if (BlockPos.ZERO.equals(targetPos)) return false;
        if (ticksRunning >= HARD_TIMEOUT_TICKS) return false;

        final boolean stillInjured = mob.getHealth() < mob.getMaxHealth();

        if (emergencyThisRun) 
        {
            // End emergency if we’re no longer injured
            if (!stillInjured) return false;

            // If we can’t reach and we’ve exhausted retries, bail so others can run
            if (pathTries >= MAX_TRIES && mob.getNavigation().isDone() && !isWithinDistance()) 
            {
                return false;
            }

            // If we reached, allow up to MAX_TRIES “no food” checks; otherwise keep trying to move
            if (isWithinDistance()) 
            {
                return (feedingTries < MAX_TRIES);
            } 
            else 
            {
                // still trying to path there
                return pathTries < MAX_TRIES;
            }
        }

        // Normal (non-emergency) daytime run
        return !isWithinDistance() && !mob.getNavigation().isDone();
    }
    /**
     * Executes the tick logic for this goal. This function is called once per tick for the AI to perform its actions. It checks if the pet is within the stopping distance of the target position, and if so, attempts to pick up food from the work location if there is any.
     * <p>
     * If food is found, it is transferred into the pet's inventory and the AI's "emergency" mode is turned off.
     */
    @Override
    public void tick()
    {
        ticksRunning++;
        if (lastProgress == null) lastProgress = mob.position();

        if (mob.position().distanceToSqr(lastProgress) < 0.25) 
        {
            staleTicks++;
        }
        else
        {
            staleTicks = 0;
            lastProgress = mob.position();
        }

        if (ticksRunning >= HARD_TIMEOUT_TICKS || staleTicks >= 60)
        {
            mob.getNavigation().stop();
            ticksRunning = HARD_TIMEOUT_TICKS;
            return;
        }

        if (isWithinDistance())
        {
            if (chestCheckCooldown-- > 0) return;
            chestCheckCooldown = 5; // scan every 5 ticks

            BlockEntity be = mob.level().getBlockEntity(targetPos);

            if (be == null)
            {
                return;
            }

            TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Pet {} Looking for food in the work location.", mob.getUUID()));

            // Pick up food from the working location if there is any
            Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(mob.level(), targetPos, null);
            if (optProvider.isEmpty())
            {
                return;
            }

            IItemHandlerCapProvider chestHandlerOpt = optProvider.get();

            ItemStorage food = new ItemStorage(PetTypes.foodForPet(mob.getClass()), EntityAIWorkAnimalTrainer.PETFOOD_SIZE);
            int foodslot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(chestHandlerOpt, stack -> !stack.isEmpty() && stack.is(food.getItem()));

            if (foodslot >= 0)
            {
                TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Pet {} Picking up some food.", mob.getUUID()));
                boolean gotFood = InventoryUtils.transferItemStackIntoNextBestSlotFromProvider(chestHandlerOpt, foodslot, mob.getInventory());

                if (gotFood)
                {
                    feedingTries = MAX_TRIES; // No need to try more.
                    be.setChanged();
                }
                else
                {
                    feedingTries++;
                }

            }
            else
            {
                feedingTries++;
            }
        }
        else
        {
            if (retryCooldown > 0) retryCooldown--;

            if ((pathTries < MAX_TRIES) && mob.getNavigation().isDone() && retryCooldown <= 0)
            {
                TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("WalkToWorkPositionGoal.tick: Retry path to {}", targetPos));

                PathingUtil.flexiblePathing(mob, targetPos, speedModifier);
                pathTries++;
                feedingTries++;
                retryCooldown = RETRY_COOLDOWN;
            }
        }
    }

    /**
     * Called when the goal is stopped, either because the target has been reached or because the goal was cancelled. If the target has
     * been reached, pick up food from the working location if there is any.
     */
    @Override
    public void stop()
    {
        TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Pet {} WalkToWorkPositionGoal.stop: {}, {}, {}, {}, {}", mob.getUUID(), emergencyThisRun, feedingTries, pathTries, retryCooldown));

        emergencyThisRun = false;
        feedingTries = 0;
        pathTries = 0;
        retryCooldown = 0;
        mob.getNavigation().stop();
    }

    /**
     * Starts the goal by setting the target position for the pet's navigation system. If not in emergency mode, also updates the last run day
     * in the pet's persistent data.
     */
    @Override
    public void start()
    {

        if (!hasRunToday(currentDay())) 
        {
            mob.getPersistentData().putLong(NBT_LAST_RUN_DAY, currentDay());
        }
        
        ticksRunning = 0; 
        lastProgress = mob.position(); 
        staleTicks = 0;
        pathTries = 0;
        feedingTries = 0;
        retryCooldown = 0;

        PathingUtil.flexiblePathing(mob, targetPos, speedModifier);
    }

    /**
     * Checks if the pet is within the stopping distance to the target position.
     * This is used to determine if we should stop the goal.
     * @return true if within the stopping distance, false otherwise
     */
    private boolean isWithinDistance()
    {
        if (targetPos == null) return false;
        return mob.position().distanceToSqr(Vec3.atCenterOf(targetPos)) <= stopDistanceSq;
    }

    /**
     * Checks if the walk-to-work goal has been run today.
     * 
     * @param today the current day index
     * @return true if the goal has been run today, false otherwise
     */
    private boolean hasRunToday(long today)
    {
        CompoundTag tag = mob.getPersistentData();
        if (!tag.contains(NBT_LAST_RUN_DAY)) return false; // never ran yet

        return tag.getLong(NBT_LAST_RUN_DAY) == today;
    }

    /**
     * Get the current day index from the game time.
     * <p>The game time cycles every 24000 ticks, so we floor-divide to get the day index.</p>
     * 
     * @return the current day index
     */
    private long currentDay()
    {
        final long gameTime = mob.level().getGameTime();
        return Math.floorDiv(gameTime, 24000L);
    }
}
