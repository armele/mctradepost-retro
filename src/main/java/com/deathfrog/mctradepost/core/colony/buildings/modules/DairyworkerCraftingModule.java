package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.Optional;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.util.CraftingUtils;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;

public class DairyworkerCraftingModule extends AbstractCraftingBuildingModule.Crafting
{
    /**
     * Create a new module.
     *
     * @param jobEntry the entry of the job.
     */
    public DairyworkerCraftingModule(final JobEntry jobEntry)
    {
        super(jobEntry);
    }

    /**
     * Check if the recipe is compatible with this module. First, it checks if the recipe is compatible with the super module. If not,
     * it returns false. If it is, it checks if the recipe is allowed based on its tags. If it is, it returns the value of the tag. If
     * not, it returns false.
     * 
     * @param recipe the recipe to check.
     * @return true if the recipe is compatible, false otherwise.
     */
    @Override
    public boolean isRecipeCompatible(final IGenericRecipe recipe)
    {
        final Optional<Boolean> isRecipeAllowed = CraftingUtils.isRecipeCompatibleBasedOnTags(recipe, MCTPModJobs.DAIRYWORKER_TAG);
        if (isRecipeAllowed.isPresent()) return isRecipeAllowed.get();

        return false;
    }
}
