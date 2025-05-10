package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import com.deathfrog.mctradepost.core.colony.jobs.JobShopkeeper;
import com.deathfrog.mctradepost.core.colony.jobs.buildings.workerbuildings.BuildingMarketplace;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.crafting.PublicCrafting;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.CitizenConstants.TICKS_20;
import static com.minecolonies.api.util.constant.Constants.*;

/**
 * Handles the Marketplace.
 */
public class EntityAIWorkShopkeeper extends AbstractEntityAICrafting<JobShopkeeper, BuildingMarketplace>
{
    /**
     * Base xp gain for the shopkeeper.
     */
    private static final double BASE_XP_GAIN = 5;

    /**
     * Walking position.
     */
    private BlockPos walkTo;

    /**
     * Initialize the stone smeltery and add all his tasks.
     *
     * @param alchemistJob the job he has.
     */
    @SuppressWarnings("unchecked")
    public EntityAIWorkShopkeeper(@NotNull final JobShopkeeper shopkeeperJob)
    {
        super(shopkeeperJob);
        super.registerTargets(
          /*
           * Check if tasks should be executed.
           */
          // TODO: Implement targets
        );
    }

    @Override
    protected IAIState decide()
    {
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
        if (job.getTaskQueue().isEmpty() || job.getCurrentTask() == null)
        {
            if (worker.getNavigation().isDone())
            {
                // TODO: Implement tasks

                if (building.isInBuilding(worker.blockPosition()))
                {
                    setDelay(TICKS_20 * 20);
                    EntityNavigationUtils.walkToRandomPosWithin(worker, 10, DEFAULT_SPEED, building.getCorners());
                }
                else
                {
                    walkToBuilding();
                }
            }
            return IDLE;
        }

        if (!walkToBuilding())
        {
            return START_WORKING;
        }

        if (job.getActionsDone() >= getActionsDoneUntilDumping())
        {
            // Wait to dump before continuing.
            return getState();
        }

        return getNextCraftingState();
    }

    @Override
    public Class<BuildingMarketplace> getExpectedBuildingClass()
    {
        return BuildingMarketplace.class;
    }

    @Override
    protected int getExtendedCount(final ItemStack stack)
    {
        if (currentRecipeStorage != null && currentRecipeStorage.getIntermediate() == Blocks.BREWING_STAND)
        {
            int count = 0;
            // TODO: Implement working locations.

            return count;
        }

        return 0;
    }

    @Override
    protected IAIState getRecipe()
    {
        final IRequest<? extends PublicCrafting> currentTask = job.getCurrentTask();

        if (currentTask == null)
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStackUtils.EMPTY);
            return START_WORKING;
        }

        job.setMaxCraftingCount(currentTask.getRequest().getCount());

        // TODO: Implement crafting, if any

        return super.getRecipe();
    }


    @Override
    protected IAIState checkForItems(@NotNull final IRecipeStorage storage)
    {
        // TODO: Implement inventory checks.

        return CRAFT;
    }

    @Override
    protected IAIState craft()
    {
        if (!walkToBuilding())
        {
            setDelay(STANDARD_DELAY);
            return getState();
        }

        if (currentRecipeStorage != null && currentRequest == null)
        {
            currentRequest = job.getCurrentTask();
        }

        if (currentRecipeStorage != null && currentRecipeStorage.getIntermediate() != Blocks.BREWING_STAND)
        {
            return super.craft();
        }

        // TODO: Integration with crafting.

        // Safety net, should get caught removing things from the brewingStand.
        if (currentRequest != null && job.getMaxCraftingCount() > 0 && job.getCraftCounter() >= job.getMaxCraftingCount())
        {
            job.finishRequest(true);
            currentRecipeStorage = null;
            currentRequest = null;
            resetValues();
            return INVENTORY_FULL;
        }

        if (currentRequest != null && (currentRequest.getState() == RequestState.CANCELLED || currentRequest.getState() == RequestState.FAILED))
        {
            incrementActionsDone(getActionRewardForCraftingSuccess());
            currentRecipeStorage = null;
            currentRequest = null;
            resetValues();
            return START_WORKING;
        }

        return IDLE;
    }
}
