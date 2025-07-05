package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.jobs.JobBartender;
import com.deathfrog.mctradepost.core.colony.jobs.JobGuestServices;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer.VacationState;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEMS_CRAFTED_DETAIL;
import java.util.HashSet;
import java.util.Set;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_GUESTSERVICES;

public class EntityAIWorkBartender extends AbstractEntityAICrafting<JobBartender, BuildingResort>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public enum BartenderAIState implements IAIState
    {
        STOCK_BUILDING;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    protected Set<ItemStorage> needsInInventory = new HashSet<>();

    /**
     * Crafting icon
     */
    private final static VisibleCitizenStatus CRAFTING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/bartender.png"),
            "com.mctradepost.gui.visiblestatus.bartender");

    /**
     * Initialize the bartender and add all their tasks.
     *
     * @param bartender the job they have.
     */
    public EntityAIWorkBartender(@NotNull final JobBartender bartender)
    {
        super(bartender);
        super.registerTargets(new AITarget<IAIState>(IDLE, START_WORKING, 2),
            new AITarget<IAIState>(START_WORKING, DECIDE, 2),
            new AITarget<IAIState>(BartenderAIState.STOCK_BUILDING, this::stockBuilding, 50),
            new AITarget<IAIState>(CRAFT, this::craft, 50));
        worker.setCanPickUpLoot(true);
    }

    /**
     * Find the guest services worker assigned to the building.
     *
     * @return the ICitizenData of the guest services worker, or null if none is found.
     */
    protected ICitizenData getGuestServices()
    {
        ICitizenData guestservices = null;

        Set<ICitizenData> citizens = building.getAllAssignedCitizen();
        for (ICitizenData citizen : citizens)
        {
            if (citizen.getJob() instanceof JobGuestServices)
            {
                guestservices = citizen;
                break;
            }
        }

        return guestservices;
    }

    /**
     * Stocks the building with the necessary remedy items from the bartender's inventory.
     * This method attempts to transfer each required remedy item from the bartender's inventory
     * into the building's inventory.
     *
     * @return the next state to transition to after attempting to stock the building.
     */
    protected IAIState stockBuilding()
    {

        for (final ItemStorage remedy : needsInInventory)
        {
            int amount = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), remedy.getItem());

            if (amount > 0)
            {
                TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Bartender: stocking the building with {}.", remedy));

                if (!walkToBuilding())
                {
                    return BartenderAIState.STOCK_BUILDING;
                }

                boolean stocked = InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandler(
                    worker.getInventoryCitizen(),
                    Vacationer.hasRemedyItem(remedy),
                    amount, building.getItemHandlerCap()
                );

                if (!stocked)
                {
                    TraceUtils.dynamicTrace(TRACE_GUESTSERVICES, () -> LOGGER.info("Bartender: failed to stock the building with {}.", remedy));
                }
            }
        }


        return decide();
    }

    protected IAIState decide()
    {
        needsInInventory.clear();

        building.getGuests().forEach(guest -> {
            if (guest.getState() != VacationState.CHECKED_OUT)
            {

                for (ItemStorage remedy : guest.getRemedyItems())
                {
                    int amount = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), remedy.getItem());
                    if (amount > 0)
                    {
                        needsInInventory.add(remedy);
                    }
                }
            }
        });

        if (needsInInventory.size() > 0)
        {
            return BartenderAIState.STOCK_BUILDING;
        }

        return super.decide();
    }

    /**
     * The building class this AI is intended to be used with.
     * 
     * @return the building class
     */
    @Override
    public Class<BuildingResort> getExpectedBuildingClass()
    {
        return BuildingResort.class;
    }

    /**
     * Perform the crafting operation for the bartender. This method is called by the {@link AbstractEntityAICrafting} class when the
     * AI is in the CRAFT state.
     * <p>This method is responsible for setting the visible status of the citizen to {@link #CRAFTING} and then calling the
     * superclass's {@link #craft()} method.
     * <p>The superclass's method will then perform the actual crafting operation and update the building's statistics accordingly.
     * <p>The visible status is set to {@link #CRAFTING} so that the client knows to display the bartender as being in the crafting
     * state.
     * <p>The superclass's method will also call this method recursively until all items have been crafted or the AI is interrupted.
     * <p>
     * 
     * @return the next AI state to transition to.
     */
    @Override
    protected IAIState craft()
    {
        // MCTradePostMod.LOGGER.info("Bartender crafting...");
        worker.getCitizenData().setVisibleStatus(CRAFTING);
        return super.craft();
    }

    /**
     * Records the items crafted in the building statistics.
     *
     * @param request the request that was completed.
     * @param recipe  the recipe that was used.
     */
    @Override
    protected void recordCraftingBuildingStats(IRequest<?> request, IRecipeStorage recipe)
    {
        // MCTradePostMod.LOGGER.info("Recording bartender stats");
        if (recipe == null)
        {
            return;
        }

        StatsUtil.trackStatByName(building,
            ITEMS_CRAFTED_DETAIL,
            recipe.getPrimaryOutput().getDescriptionId(),
            recipe.getPrimaryOutput().getCount());
    }
}
