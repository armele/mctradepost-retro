package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.jobs.JobBartender;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;

import net.minecraft.resources.ResourceLocation;

public class EntityAIWorkBartender  extends AbstractEntityAICrafting<JobBartender, BuildingResort> {
        /**
     * Crafting icon
     */
    private final static VisibleCitizenStatus CRAFTING =
      new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/bartender.png"), "com.mctradepost.gui.visiblestatus.bartender");

    /**
     * Initialize the sawmill and add all his tasks.
     *
     * @param sawmill the job he has.
     */
    public EntityAIWorkBartender(@NotNull final JobBartender bartender)
    {
        super(bartender);
    }

    @Override
    public Class<BuildingResort> getExpectedBuildingClass()
    {
        return BuildingResort.class;
    }

    @Override
    protected IAIState craft()
    {
        worker.getCitizenData().setVisibleStatus(CRAFTING);
        return super.craft();
    }
}
