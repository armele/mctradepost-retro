package com.deathfrog.mctradepost.core.blocks.huts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.tileentities.MCTPTileEntityColonyBuilding;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

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

    /**
     * Event-Handler for placement of this block.
     * <p>
     * Override for custom logic.
     *
     * @param worldIn the word we are in.
     * @param pos     the position where the block was placed.
     * @param state   the state the placed block is in.
     * @param placer  the player placing the block.
     * @param stack   the itemstack from where the block was placed.
     * @param rotMir  the mirror used.
     * @param style   the style of the building
     */

    public void onBlockPlacedByBuildTool(
      @NotNull final Level worldIn,
      @NotNull final BlockPos pos,
      final BlockState state,
      final LivingEntity placer,
      final ItemStack stack,
      final RotationMirror rotMir,
      final String style,
      final String blueprintPath)
    {
        final BlockEntity tileEntity = worldIn.getBlockEntity(pos);

        if (tileEntity != null) {
            MCTradePostMod.LOGGER.info("Placing hut of type {}", tileEntity.getClass().getName());
        } else {
            MCTradePostMod.LOGGER.info("Attempting to place a null tile entity.");
        }

        if (tileEntity instanceof final MCTPTileEntityColonyBuilding hut)
        {
            MCTradePostMod.LOGGER.info("Rotation mirror: {}; pack name: {}; blueprint path: {}", rotMir, style, blueprintPath);
            hut.setRotationMirror(rotMir);
            hut.setPackName(style);
            hut.setBlueprintPath(blueprintPath);
        } else {
            MCTradePostMod.LOGGER.info("Wrong instance to place a tile entity of type {}", tileEntity.getClass().getName());
        }

        setPlacedBy(worldIn, pos, state, placer, stack);
    }    
}
