package com.deathfrog.mctradepost.core.blocks.blockentity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.logging.LogUtils;

import net.minecraft.util.Tuple;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PetWorkingBlockEntity extends RandomizableContainerBlockEntity
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String TAG_PWB_CUSTOM_NAME = "PWBCustomName";

    // Stores a player-set name (in an anvil, for example)
    private Component customName;

    // Stores the derived name, recomputed as needed.
    private Component derivedName; 

    private boolean needsBuildingName = true;

    // Next game time at which we’re allowed to re-check.
    private long nextNameCheckGameTime = 0L;

    // Adaptive or fixed interval between checks:
    private int currentCheckIntervalTicks = 20 * 10; // start with 10s
    private static final int MAX_CHECK_INTERVAL_TICKS = 20 * 60 * 10; // cap at 10 min
    public static final int SLOT_COUNT = 27;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, NullnessBridge.assumeNonnull(ItemStack.EMPTY));

    public PetWorkingBlockEntity(BlockPos pos, BlockState state)
    {
        super(MCTradePostTileEntities.PET_WORK_LOCATION.get(), pos, state);
    }

    @Override
    protected NonNullList<ItemStack> getItems()
    {
        return items;
    }

    @Override
    protected void setItems(@Nonnull NonNullList<ItemStack> items)
    {
        this.items = items;
    }

    @Override
    public int getContainerSize()
    {
        return SLOT_COUNT;
    }


    /**
     * Sets the custom name of the PetWorkingBlockEntity, which is used when generating the block's display name.
     * Note that this name is only used for display purposes, and does not affect the block's actual name or
     * item ID.
     * @param name the custom name to set
     */
    public void setCustomName(Component name)
    {
        // LOGGER.info("Setting the custom name to {} at {}: {}", name, this.getBlockPos().toShortString(), Thread.currentThread().getStackTrace());

        this.customName = name;
    }

    @Nullable
    public Component getCustomName()
    {
        // LOGGER.info("Getting the custom name {} at {}: {}", this.customName, this.getBlockPos().toShortString(), Thread.currentThread().getStackTrace());
        return this.customName;
    }

    public boolean hasCustomName()
    {
        // LOGGER.info("Checking if there is a custom name {} at {}.", this.customName, this.getBlockPos().toShortString());
        return this.customName != null;
    }

    /**
     * Saves additional data to the given CompoundTag, including the custom name of the TE and its inventory.
     * If the TE has a custom name, it is stored in the tag under the key TAG_PWB_CUSTOM_NAME.
     * If the TE has a loot table, it is saved in the tag under the key "LootTable"; otherwise, the inventory is
     * saved in the tag under the key "Items".
     * @param tag the CompoundTag to save the data to.
     * @param registries the HolderLookup.Provider containing the registries of items and blocks.
     */
    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);

        // Save inventory unless a loot table is in use
        if (!this.trySaveLootTable(tag) && this.items != null)
        {
            ContainerHelper.saveAllItems(tag, this.items, registries);
        }

        if (this.customName != null)
        {
            String customString = Component.Serializer.toJson(this.customName, registries);
            if (customString != null)
            {
                tag.putString(TAG_PWB_CUSTOM_NAME, customString);
            }
        }
    }

    /**
     * Loads additional data from the given CompoundTag, including the custom name of the TE and its inventory.
     * If the TE has a loot table, it is loaded in the tag under the key "LootTable"; otherwise, the inventory is
     * loaded in the tag under the key "Items". The custom name is loaded from the tag under the key TAG_PWB_CUSTOM_NAME.
     * @param tag the CompoundTag to load the data from.
     * @param registries the HolderLookup.Provider containing the registries of items and blocks.
     */
    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);

        // Ensure list has the correct size before loading
        this.items = NonNullList.withSize(getContainerSize(), NullnessBridge.assumeNonnull(ItemStack.EMPTY));

        // Load inventory unless a loot table is in use
        if (!this.tryLoadLootTable(tag) && this.items != null)
        {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }

        if (tag.contains(TAG_PWB_CUSTOM_NAME))
        {
            String customString = tag.getString(TAG_PWB_CUSTOM_NAME);
            Component raw = Component.Serializer.fromJson(NullnessBridge.assumeNonnull(customString), registries);
            setCustomName(raw);
        }

        if (this.customName != null)
        {
            // LOGGER.info("in loadAdditional - Custom name: {} at {}", this.customName, this.getBlockPos().toShortString());
            needsBuildingName = false;
        }
        else
        {
            // LOGGER.info("No custom name at {}: ", this.getBlockPos().toShortString());
            needsBuildingName = true;
        }

    }

    @Override
    protected AbstractContainerMenu createMenu(int windowId, @Nonnull Inventory inv)
    {
        return ChestMenu.threeRows(windowId, inv, this);
    }

    @Override
    public Component getDefaultName()
    {
        // First prefer custom names
        if (this.hasCustomName())
        {
            // LOGGER.info("In getDefaultName - Custom name {} at {} ", this.getCustomName(), this.getBlockPos().toShortString());
            return this.getCustomName();
        }

        // Then try derived names
        if (this.derivedName != null)
        {
            return this.derivedName;
        }

        // Fall back to true defaults.
        Block block = this.getBlockState().getBlock();
        String key = block.getDescriptionId();

        Component translated = Component.translatable(key + ".shortname");
        // LOGGER.info("Default name key {} translates to {}. ", key, translated);

        Component name = Component.literal(translated.getString() + " @ " + this.getBlockPos().toShortString());

        return name;
    }

    /**
     * Called when the block entity is loaded to ensure builder-placed animal working blocks are recognized
     * and registered as working locations.
     */
    @Override
    public void onLoad() 
    {
        super.onLoad();

        Level localLevel = level;
        if (localLevel == null || localLevel.isClientSide) return;

        // Still register the work location:
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(localLevel, worldPosition);
        if (colony != null) 
        {
            PetRegistryUtil.registerWorkLocation(colony, worldPosition);
        }
        else 
        {
            LOGGER.warn("No colony found for Pet Working Block at {}", this.getBlockPos().toShortString());
        }

        if (this.hasCustomName()) 
        {
            needsBuildingName = false;
        } 
        else 
        {
            needsBuildingName = true;
            // First check soon after load to catch already-finished builds:
            nextNameCheckGameTime = localLevel.getGameTime() + 20; // 1s after load
        }
    }

    /**
     * Called every tick on the server for every PetWorkingBlockEntity. This is responsible for resolving the name of the block entity
     * from the building at its position if it does not have a custom name.
     * @param level the level the block entity is in
     * @param pos the position of the block entity
     * @param state the block state of the block entity
     * @param be the block entity to resolve the name of
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PetWorkingBlockEntity be) 
    {
        if (level.isClientSide) return;
        if (!be.needsBuildingName || be.hasCustomName() || be.derivedName != null) return;

        long now = level.getGameTime();

        // Initialize if needed
        if (be.nextNameCheckGameTime == 0L) 
        {
            be.nextNameCheckGameTime = now + be.currentCheckIntervalTicks;
            return;
        }

        // Not time yet
        if (now < be.nextNameCheckGameTime) 
        {
            return;
        }
        
        boolean success = be.tryResolveNameFromBuilding();

        if (success) 
        {
            // LOGGER.info("Got derived name {} at {} " + be.getCustomName(), be.getBlockPos().toShortString());
            be.needsBuildingName = false;
            be.setChanged();
        } 
        else 
        {
            // LOGGER.info("No derived name yet {} at {} " + be.getCustomName(), be.getBlockPos().toShortString());

            // Backoff: space attempts further apart over time.
            be.currentCheckIntervalTicks = Math.min(be.currentCheckIntervalTicks * 2, MAX_CHECK_INTERVAL_TICKS);
            be.nextNameCheckGameTime = now + be.currentCheckIntervalTicks;
        }
    }



    /**
     * Attempts to derive the name of this block entity from the MineColonies building at its position.
     * If the block entity does not have a custom name, it checks all buildings in the colony at its position
     * and uses the first one it finds. If a building is found, it sets the name of this block entity to a
     * string of the form "Herd: <building display name>" and marks the block entity as changed.
     * If no building is found, the name of this block entity is not changed.
     */
    private boolean tryResolveNameFromBuilding()
    {
        if (this.derivedName != null)
        {
            return true;
        }

        Level level = getLevel();
        if (level == null || level.isClientSide)
        {
            return false;
        }

        BlockPos localWorldPosition = worldPosition;

        if (localWorldPosition == null)
        {
            return false;
        }

        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, localWorldPosition);
        if (colony == null)
        {
            // LOGGER.info("Couldn't get the colony from the manager for position {} ", worldPosition.toShortString());
            return false;
        }

        // LOGGER.info("Testing {} buildings at {} ", colony.getBuildingManager().getBuildings().size(), worldPosition.toShortString());
        IBuilding selected = null;

        for (IBuilding candidate : colony.getServerBuildingManager().getBuildings().values())
        {
            Tuple<BlockPos, BlockPos> corners = candidate.getCorners();
            if (corners == null) continue;

            BlockPos a = corners.getA();
            BlockPos b = corners.getB();

            int minX = Math.min(a.getX(), b.getX());
            int minY = Math.min(a.getY(), b.getY());
            int minZ = Math.min(a.getZ(), b.getZ());
            int maxX = Math.max(a.getX(), b.getX());
            int maxY = Math.max(a.getY(), b.getY());
            int maxZ = Math.max(a.getZ(), b.getZ());

            if (localWorldPosition.getX() >= minX && localWorldPosition.getX() <= maxX &&
                localWorldPosition.getY() >= minY &&
                localWorldPosition.getY() <= maxY &&
                localWorldPosition.getZ() >= minZ &&
                localWorldPosition.getZ() <= maxZ)
            {
                selected = candidate;
                break;
            }
        }

        if (selected == null)
        {
            // LOGGER.info("No building found for position {} ", worldPosition.toShortString());
            return false;
        }

        String dispName = selected.getBuildingDisplayName();
        Component name = Component.literal("Herd: " + Component.translatable(dispName + "").getString());

        // LOGGER.info("Setting derived name {} at position {} ", name, worldPosition.toShortString());

        this.derivedName = name;
        this.setChanged();
        
        BlockState state = getBlockState();

        if (state == null)
        {
            return false;
        }

        level.sendBlockUpdated(localWorldPosition, state, state, 3);

        return true;
    }

}
