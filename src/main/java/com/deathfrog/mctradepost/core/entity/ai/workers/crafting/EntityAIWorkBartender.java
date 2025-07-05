package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.jobs.JobBartender;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import net.minecraft.resources.ResourceLocation;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEMS_CRAFTED_DETAIL;

public class EntityAIWorkBartender extends AbstractEntityAICrafting<JobBartender, BuildingResort>
{
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
        super.registerTargets(
          new AITarget<IAIState>(IDLE, START_WORKING, 2),
          new AITarget<IAIState>(START_WORKING, DECIDE, 2),
          new AITarget<IAIState>(CRAFT, this::craft, 50)
        );
        worker.setCanPickUpLoot(true);
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
     * Perform the crafting operation for the bartender. This method is called by the
     * {@link AbstractEntityAICrafting} class when the AI is in the CRAFT state.
     * <p>
     * This method is responsible for setting the visible status of the citizen to
     * {@link #CRAFTING} and then calling the superclass's {@link #craft()} method.
     * <p>
     * The superclass's method will then perform the actual crafting operation and
     * update the building's statistics accordingly.
     * <p>
     * The visible status is set to {@link #CRAFTING} so that the client knows to
     * display the bartender as being in the crafting state.
     * <p>
     * The superclass's method will also call this method recursively until all
     * items have been crafted or the AI is interrupted.
     * <p>
     * @return the next AI state to transition to.
     */
    @Override
    protected IAIState craft()
    {
        MCTradePostMod.LOGGER.info("Bartender crafting...");
        worker.getCitizenData().setVisibleStatus(CRAFTING);
        return super.craft();
    }

    /**
     * Records the items crafted in the building statistics.
     *
     * @param request  the request that was completed.
     * @param recipe   the recipe that was used.
     */
    @Override
    protected void recordCraftingBuildingStats(IRequest<?> request, IRecipeStorage recipe)
    {
        MCTradePostMod.LOGGER.info("Recording bartender stats");
        if (recipe == null) 
        {
            return;
        }

        StatsUtil.trackStatByName(building, ITEMS_CRAFTED_DETAIL, recipe.getPrimaryOutput().getDescriptionId(), recipe.getPrimaryOutput().getCount());
    }
}
