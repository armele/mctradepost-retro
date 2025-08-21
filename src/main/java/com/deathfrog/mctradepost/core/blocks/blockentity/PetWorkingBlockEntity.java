package com.deathfrog.mctradepost.core.blocks.blockentity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PetWorkingBlockEntity extends RandomizableContainerBlockEntity
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private Component customName;

    public static final int SLOT_COUNT = 27;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

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

    public void setCustomName(Component name)
    {
        this.customName = name;
    }

    @Nullable
    public Component getCustomName()
    {
        return this.customName;
    }

    public boolean hasCustomName()
    {
        return this.customName != null;
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        if (this.hasCustomName())
        {
            tag.putString("CustomName", Component.Serializer.toJson(this.customName, registries));
        }
    }

    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        if (tag.contains("CustomName"))
        {
            this.customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
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
        if (this.hasCustomName())
        {
            LOGGER.info("Custom name: " + this.getCustomName());
            return this.getCustomName();
        }

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
    public void onLoad() {
        super.onLoad();

        if (this.getLevel() == null) {
            return;
        }

        if (!this.getLevel().isClientSide) {
            IColony colony = IColonyManager.getInstance().getClosestColony(this.getLevel(), this.getBlockPos());
            if (colony != null) {
                PetRegistryUtil.registerWorkLocation(colony, this.getBlockPos());
            }

            adjustName();
        }
    }

    public void adjustName()
    {
        if (!this.hasCustomName())
        {
            // Try to derive the name from the MineColonies building at this position
            final BlockPos pos = getBlockPos();
            final ServerLevel sLevel = (ServerLevel) level;

            IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(sLevel, pos);

            if (colony != null)
            {
                IBuilding selectedBuilding = null;

                for (IBuilding candidate : colony.getBuildingManager().getBuildings().values())
                {
                    Tuple<BlockPos, BlockPos> corners = candidate.getCorners();
                    
                    BlockPos min = new BlockPos(Math.min(corners.getA().getX(), corners.getB().getX()),
                        Math.min(corners.getA().getY(), corners.getB().getY()),
                        Math.min(corners.getA().getZ(), corners.getB().getZ()));
                    
                    BlockPos max = new BlockPos(Math.max(corners.getA().getX(), corners.getB().getX()),
                        Math.max(corners.getA().getY(), corners.getB().getY()),
                        Math.max(corners.getA().getZ(), corners.getB().getZ()));

                    if (pos.getX() >= min.getX() && 
                        pos.getX() <= max.getX() &&
                        pos.getY() >= min.getY() &&
                        pos.getY() <= max.getY() &&
                        pos.getZ() >= min.getZ() &&
                        pos.getZ() <= max.getZ())
                    {
                        selectedBuilding = candidate;
                        break;
                    }
                }

                if (selectedBuilding != null)
                {
                    // Use building display name + suffix (customize as you like)
                    Component name = Component.literal("Herd: " +selectedBuilding.getBuildingDisplayName());
                    this.setCustomName(name);
                    this.setChanged();
                    level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
                }
            }
        }
    }

}
