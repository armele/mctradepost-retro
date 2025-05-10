package com.deathfrog.mctradepost.core.blocks.huts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public abstract class MCTPBaseBlockHut extends AbstractBlockHut<MCTPBaseBlockHut> {

    public MCTPBaseBlockHut registerMCTPHutBlock(final Registry<Block> registry)
    {
        Registry.register(registry, ResourceLocation.parse(MCTradePostMod.MODID + ":" + getHutName()), this);
        return this;
    }

    /**
     * Get the registry name frm the blck hut.
     * @return the key.
     */
    public ResourceLocation getRegistryName()
    {
        return ResourceLocation.parse(MCTradePostMod.MODID + ":" + getHutName());
    }    

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull final BlockPos blockPos, @NotNull final BlockState blockState)
    {
        final TileEntityColonyBuilding building = (TileEntityColonyBuilding) MCTradePostTileEntities.BUILDING.get().create(blockPos, blockState);
        if (building != null) {
            building.registryName = this.getBuildingEntry().getRegistryName();
        }
        return building;
    }
}
