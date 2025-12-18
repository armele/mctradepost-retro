package com.deathfrog.mctradepost.core.blocks.huts;

import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.tileentities.MCTPTileEntityColonyBuilding;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
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

public abstract class MCTPBaseBlockHut extends AbstractBlockHut<MCTPBaseBlockHut>
{
    public MCTPBaseBlockHut registerMCTPHutBlock(final @Nonnull Registry<Block> registry)
    {
        Registry.register(registry, getRegistryName(), this);
        return this;
    }

    /**
     * Get the registry name frm the blck hut.
     * 
     * @return the key.
     */
    public @Nonnull ResourceLocation getRegistryName()
    {
        String name = this.getHutName();

        if (name == null)
        {
            throw new IllegalStateException("Block hut has no name");
        }

        ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, name);
        return NullnessBridge.assumeNonnull(resLoc);
    }

    /**
     * Creates a new block entity for the given block position and state. This method will create a new instance of a
     * TileEntityColonyBuilding and set its registry name to the name of the building entry associated with this block hut.
     *
     * @param blockPos   the block position
     * @param blockState the block state
     * @return the new block entity, or null if the building entry is not found
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull final BlockPos blockPos, @NotNull final BlockState blockState)
    {
        BlockPos localBlockPos = NullnessBridge.assumeNonnull(blockPos);
        BlockState localBlockState = NullnessBridge.assumeNonnull(blockState);

        final TileEntityColonyBuilding building =
            (TileEntityColonyBuilding) MCTradePostTileEntities.BUILDING.get().create(localBlockPos, localBlockState);
            
        if (building != null)
        {
            building.registryName = this.getBuildingEntry().getRegistryName();
        }
        return building;
    }

    /**
     * Event-Handler for placement of this block.
     * <p>Override for custom logic.
     *
     * @param worldIn the word we are in.
     * @param pos     the position where the block was placed.
     * @param state   the state the placed block is in.
     * @param placer  the player placing the block.
     * @param stack   the itemstack from where the block was placed.
     * @param rotMir  the mirror used.
     * @param style   the style of the building
     */

    /**
     * Called when a block of this type is placed by a build tool. This method is responsible for setting the rotation mirror, pack
     * name, and blueprint path of the newly created MCTPTileEntityColonyBuilding.
     * 
     * @param worldIn       the world we are in.
     * @param pos           the position where the block was placed.
     * @param state         the state the placed block is in.
     * @param placer        the player placing the block.
     * @param stack         the item stack from where the block was placed.
     * @param rotMir        the rotation mirror of the placed block.
     * @param style         the pack name of the placed block.
     * @param blueprintPath the path to the blueprint of the placed block.
     */
    @Override
    public void onBlockPlacedByBuildTool(@NotNull final Level worldIn,
        @NotNull final BlockPos pos,
        final BlockState state,
        final LivingEntity placer,
        final ItemStack stack,
        final RotationMirror rotMir,
        final String style,
        final String blueprintPath)
    {
        BlockPos localPos = NullnessBridge.assumeNonnull(pos);
        final BlockEntity tileEntity = worldIn.getBlockEntity(localPos);

        if (tileEntity == null)
        {
            MCTradePostMod.LOGGER.error("Attempting to place a null tile entity.");
            return;
        }

        if (tileEntity instanceof final MCTPTileEntityColonyBuilding hut)
        {
            MCTradePostMod.LOGGER.info("Rotation mirror: {}; pack name: {}; blueprint path: {}", rotMir, style, blueprintPath);
            hut.setRotationMirror(rotMir);
            hut.setPackName(style);
            hut.setBlueprintPath(blueprintPath);
        }
        else
        {
            MCTradePostMod.LOGGER.info("Wrong instance to place a tile entity of type {}", tileEntity.getClass().getName());
        }

        setPlacedBy(worldIn, localPos, state, placer, stack);
    }
}
