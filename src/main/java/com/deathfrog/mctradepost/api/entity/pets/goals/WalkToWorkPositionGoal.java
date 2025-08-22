package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.ItemHandlerHelpers;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkAnimalTrainer;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class WalkToWorkPositionGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String NBT_LAST_RUN_DAY = "mctradepost:walk_to_work_last_day";
    private final P mob;
    private final BlockPos targetPos;
    private final double speedModifier;
    private final double stopDistanceSq; // squared distance to stop
    private boolean emergencyThisRun = false;

    public WalkToWorkPositionGoal(P mob, BlockPos targetPos, double speed, double stopDistance)
    {
        this.mob = mob;
        this.targetPos = targetPos;
        this.speedModifier = speed;
        this.stopDistanceSq = stopDistance * stopDistance;
    }

    /**
     * Determines if the goal can be used.
     * 
     * This goal can be used if the pet is not on the client side, if the target position is not BlockPos.ZERO, if the chunk is loaded, and if one of the following conditions is true:
     * - the pet is injured (i.e. at less than 60% health)
     * - it is daytime and the pet has not already run today
     * - it is not within the stop distance of the target position.
     * 
     * @return true if the goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        if (mob.level().isClientSide()) return false;
        if (BlockPos.ZERO.equals(targetPos)) return false;
    
        if (!mob.level().isLoaded(targetPos)) return false; 
        if (!mob.level().isInWorldBounds(targetPos)) return false; 

        final long timeOfDay = mob.level().getDayTime() % 24000L;

        // Emergency case: run anytime if injured
        emergencyThisRun = mob.getHealth() < mob.getMaxHealth() * 0.6f;
        if (emergencyThisRun)
        {
            return !isWithinDistance();
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

    @Override
    public boolean canContinueToUse()
    {
        if (mob.level().isClientSide()) return false;
        if (BlockPos.ZERO.equals(targetPos)) return false;

        return !isWithinDistance() && !mob.getNavigation().isDone();
    }

    /**
     * Called when the goal is stopped, either because the target has been reached or because the goal was cancelled. If the target has
     * been reached, pick up food from the working location if there is any.
     */
    @Override
    public void stop()
    {
        emergencyThisRun = false;

        if (isWithinDistance())
        {
            BlockEntity be = mob.level().getBlockEntity(targetPos);

            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Looking for food in the work location."));

            // Pick up food from the working location if there is any
            if (be instanceof Container container)
            {
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
                    InventoryUtils
                        .transferItemStackIntoNextBestSlotFromProvider(chestHandlerOpt, foodslot, mob.getInventory());
                }
                
                be.setChanged();
            }
        }
    }

    /**
     * Starts the goal by setting the target position for the pet's navigation system. If not in emergency mode, also updates the last run day
     * in the pet's persistent data.
     */
    @Override
    public void start()
    {
        if (!emergencyThisRun)
        {
            long today = currentDay();
            mob.getPersistentData().putLong(NBT_LAST_RUN_DAY, today);
        }

        mob.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, speedModifier);
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
        // Cache not required; read/write is cheap and persistent across saves
        long last = mob.getPersistentData().getLong(NBT_LAST_RUN_DAY);
        return last == today;
    }

    /**
     * Get the current day index from the game time.
     * 
     * <p>
     * The game time cycles every 24000 ticks, so we floor-divide to get the day index.
     * </p>
     * 
     * @return the current day index
     */
    private long currentDay()
    {
        // Use day index, not time-of-day
        final long gameTime = mob.level().getDayTime(); // cycles but increases overall with days
        return Math.floorDiv(gameTime, 24000L);
    }
}
