package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;
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
    private int tries = 0;
    private int retryCooldown = 0;


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

        // Emergency case: run anytime if injured
        emergencyThisRun = mob.getHealth() < mob.getMaxHealth();
        if (emergencyThisRun)
        {
            TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("WalkToWorkPositionGoal.canUse: Emergency run to: ", targetPos));

            return (tries < MAX_TRIES);
        }

        // Otherwise run only duing the day.
        if (timeOfDay >= 12000)
        {
            return false;
        }

        long today = currentDay();
        if (hasRunToday(today)) return false;

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


        emergencyThisRun = mob.getHealth() < mob.getMaxHealth();

        if (emergencyThisRun) 
        {
            return (tries < MAX_TRIES);  
        }

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
        if (isWithinDistance())
        {
            BlockEntity be = mob.level().getBlockEntity(targetPos);

            if (be == null)
            {
                return;
            }

            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Looking for food in the work location."));

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
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Picking up some food."));
                boolean gotFood = InventoryUtils.transferItemStackIntoNextBestSlotFromProvider(chestHandlerOpt, foodslot, mob.getInventory());

                if (gotFood)
                {
                    tries = MAX_TRIES; // No need to try more.
                }

            }
            
            be.setChanged();
        }
        else
        {
            if (retryCooldown > 0) retryCooldown--;

            if ((tries < MAX_TRIES) && mob.getNavigation().isDone() && retryCooldown <= 0)
            {
                TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("WalkToWorkPositionGoal.tick: Retry path to {}", targetPos));

                PathingUtil.flexiblePathing(mob, targetPos, speedModifier);
                tries++;
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
        TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("WalkToWorkPositionGoal.stop"));

        emergencyThisRun = false;
        tries = 0;
        retryCooldown = 0;
    }

    /**
     * Starts the goal by setting the target position for the pet's navigation system. If not in emergency mode, also updates the last run day
     * in the pet's persistent data.
     */
    @Override
    public void start()
    {
        tries = 0;
        retryCooldown = 0;

        if (!emergencyThisRun)
        {
            long today = currentDay();
            mob.getPersistentData().putLong(NBT_LAST_RUN_DAY, today);
        }

        PathingUtil.flexiblePathing(mob, targetPos, speedModifier);
    }

    /**
     * Checks if the pet is within the stopping distance to the target position.
     * This is used to determine if we should stop the goal.
     * @return true if within the stopping distance, false otherwise
     */
    private boolean isWithinDistance()
    {
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
