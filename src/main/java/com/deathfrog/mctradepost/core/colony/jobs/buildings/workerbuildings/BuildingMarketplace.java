package com.deathfrog.mctradepost.core.colony.jobs.buildings.workerbuildings;

import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.minecolonies.api.util.constant.BuildingConstants.CONST_DEFAULT_MAX_BUILDING_LEVEL;
import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;


/**
 * Class of the marketplace building.
 */
public class BuildingMarketplace extends AbstractBuilding
{

    public BuildingMarketplace(@NotNull IColony colony, BlockPos pos) {
        super(colony, pos);
        setRotationMirror(RotationMirror.NONE);
        keepX.put(itemStack -> ItemStackUtils.hasEquipmentLevel(itemStack, ModEquipmentTypes.shears.get(), TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), new Tuple<>(1, true));
        keepX.put(itemStack ->  itemStack.getItem() == Items.NETHER_WART, new Tuple<>(16, false));
        keepX.put(itemStack -> ItemStackUtils.hasEquipmentLevel(itemStack, ModEquipmentTypes.axe.get(), TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), new Tuple<>(1, true));
    }

    /**
     * Description string of the building.
     */
    // TODO: Consistency between building (MARKETPLACE) and job (SHOPKEEPER)
    // TODO: Upgrading the building throws an exception - possibly due to schematics not implemented as a data pack.
    private static final String SHOPKEEPER = "shopkeeper";


    @NotNull
    @Override
    public String getSchematicName()
    {
        return SHOPKEEPER;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return CONST_DEFAULT_MAX_BUILDING_LEVEL;
    }

    @Override
    public void registerBlockPosition(@NotNull final BlockState block, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        super.registerBlockPosition(block, pos, world);
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {

    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);

        return compound;
    }

    public static class CraftingModule extends AbstractCraftingBuildingModule.Crafting
    {
        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public CraftingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }

        @Override
        public boolean isRecipeCompatible(@NotNull final IGenericRecipe recipe)
        {
            if (!super.isRecipeCompatible(recipe))
                return false;

            return recipe.getPrimaryOutput().getItem() == ModItems.magicpotion;
        }

        @Override
        public Set<CraftingType> getSupportedCraftingTypes()
        {
            return Collections.emptySet();
        }

        @Override
        public @NotNull List<IGenericRecipe> getAdditionalRecipesForDisplayPurposesOnly(@NotNull final Level world)
        {
            final List<IGenericRecipe> recipes = new ArrayList<>(super.getAdditionalRecipesForDisplayPurposesOnly(world));

            return recipes;
        }
    }
}
