package com.deathfrog.mctradepost.core.colony.jobs.buildings.workerbuildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.minecolonies.api.util.constant.BuildingConstants.CONST_DEFAULT_MAX_BUILDING_LEVEL;

/**
 * Class of the marketplace building.
 */
public class BuildingMarketplace extends AbstractBuilding
{

    public BuildingMarketplace(@NotNull IColony colony, BlockPos pos) {
        super(colony, pos);
    }

    public static final String REQUESTS_TYPE_SELLABLE = "com.mctradepost.coremod.request.sellable";

    /**
     * Description string of the building.
     */
    private static final String MARKETPLACE = "marketplace";

    /**
     * Tag to store the display shelf list.
     */
    private static final String TAG_DISPLAYSHELVES = "displayshelves";

    /**
     * Map of shelf locations and contents.
     */
    private final Map<BlockPos, DisplayCase> displayShelfContents = new HashMap<>();

    /**
     * Key for min remainder at warehouse.
     */
    public static final ISettingKey<IntSetting> MIN = new SettingKey<>(IntSetting.class, ResourceLocation.parse(MCTradePostMod.MODID + ":warehousemin"));

    /**
     * Return a list of display shelves assigned to this hut.
     *
     * @return copy of the list
     */
    public List<BlockPos> getDisplayShelfPositions()
    {
        return ImmutableList.copyOf(displayShelfContents.keySet());
    }

    /**
     * Returns the map of display shelf positions to their contents.
     * The positions are the BlockPos of the block that the item frame is attached to.
     * The contents are the ItemStack that is currently in the item frame.
     * @return a copy of the map.
     */
    public Map<BlockPos, DisplayCase> getDisplayShelves()
    {
        return displayShelfContents;
    } 

    @NotNull
    @Override
    public String getSchematicName()
    {
        return MARKETPLACE;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return CONST_DEFAULT_MAX_BUILDING_LEVEL;
    }

    @Override
    public void registerBlockPosition(@NotNull final BlockState block, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        // MCTradePostMod.LOGGER.info("Registering Block Position: " + block + " at " + pos);
        super.registerBlockPosition(block, pos, world);

        List<ItemFrame> frames = world.getEntitiesOfClass(ItemFrame.class, new AABB(pos));

        if (!frames.isEmpty() && !displayShelfContents.containsKey(pos)) {
            MCTradePostMod.LOGGER.info("Adding display position: {}", pos);
            displayShelfContents.put(pos, new DisplayCase(pos, frames.get(0).getUUID()));
        }
    }

    /**
     * Called when the colony's world is loaded and the display frame that was at the given position is not found.
     * If the display frame exists in the world, it will be updated to reflect the current items in the display shelf contents.
     * If the display frame does not exist, it is marked as broken and a message is sent to all players in the colony.
     * @param pos The position of the display shelf.
     */
    public void lostShelfAtDisplayPos(BlockPos pos) {
        if (displayShelfContents.containsKey(pos)) {
            List<ItemFrame> frames = colony.getWorld().getEntitiesOfClass(ItemFrame.class, new AABB(pos));

            // If a new frame can be found at the expected position, use it.
            if (!frames.isEmpty()) {
                displayShelfContents.put(pos, new DisplayCase(pos, frames.get(0).getUUID(), displayShelfContents.get(pos).getStack(), 0));
            // Otherwise, issue a message about the missing frame.
            } else {
                // TODO: Put some delay logic on the sending of this message so it isn't a constant spam.
                displayShelfContents.put(pos, new DisplayCase(pos, null));
                MCTradePostMod.LOGGER.warn("Missing a display frame at {}", pos);
                MessageUtils.format("entity.shopkeeper.brokenframe").sendTo(getColony()).forAllPlayers();
            }       
        } else {
            MCTradePostMod.LOGGER.warn("Looking for a display shelf at {}", pos);
        }
    }
    
    /**
     * Synchronizes the items in the display frames with the saved items in the building's display shelf contents.
     * Iterates over each display shelf position and sets the corresponding item in the item frame at that position.
     * Ensures that the item frames in the world reflect the current state of the building's item storage.
     */
    private void syncDisplayFramesWithSavedItems()
    {
        Level level = colony.getWorld();
        if (level == null) return;

        for (Map.Entry<BlockPos, DisplayCase> entry : displayShelfContents.entrySet())
        {
            ItemStack stack = entry.getValue().getStack();

            ItemFrame frame = (ItemFrame) ((ServerLevel) level).getEntity(entry.getValue().getFrameId());

            if (frame == null) {
                MCTradePostMod.LOGGER.warn("Missing a display frame at {}", entry.getKey());
            } else {
                frame.setItem(stack);
            }
        }
    }

    /**
     * Initialize the display shelf locations based on what is tagged in the structure.
     * This makes the building look for the correct number of display shelves even if some are missing.
     * That way a "repair" action will fix the problem.
     */
    public void identifyExpectedShelfPositions() {
        // We want any tagged display locations nto be added into the displayShelfContents if not already there.
        final List<BlockPos> shelfLocations = getLocationsFromTag("display_shelf");

        for (BlockPos pos : shelfLocations)
        {
            displayShelfContents.putIfAbsent(pos, new DisplayCase(pos, null));
        }
    }

    /**
     * Deserializes the NBT data for the building, restoring its state from the provided CompoundTag.
     * Clears the current display shelf contents and repopulates it using the data from the NBT.
     * Synchronizes the display frames in the world with the deserialized shelf contents.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the building.
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        // Fill in display shelf locations with the saved items.
        ListTag shelfTagList = compound.getList(TAG_DISPLAYSHELVES, Tag.TAG_COMPOUND);
        for (int i = 0; i < shelfTagList.size(); ++i)
        {
            CompoundTag shelfTag = shelfTagList.getCompound(i);
            DisplayCase contents = DisplayCase.fromNBT(shelfTag, provider);
            displayShelfContents.put(contents.getPos(), contents);
        }

        syncDisplayFramesWithSavedItems();
    }

    /**
     * Serializes the NBT data for the building, including the display shelf contents.
     * The display shelf contents are stored in a list of CompoundTags, where each CompoundTag
     * contains the BlockPos of the display shelf and the ItemStack of the item in that shelf.
     * The CompoundTag is stored in the parent CompoundTag under the key TAG_DISPLAYSHELVES.
     * @param provider The holder lookup provider for item and block references.
     * @return The serialized CompoundTag containing the state of the building.
     */
    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);
        final ListTag shelfTagList = new ListTag();

        for (Map.Entry<BlockPos, DisplayCase> entry : displayShelfContents.entrySet())
        {
            shelfTagList.add(entry.getValue().toNBT(provider));
        }

        compound.put(TAG_DISPLAYSHELVES, shelfTagList);
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
