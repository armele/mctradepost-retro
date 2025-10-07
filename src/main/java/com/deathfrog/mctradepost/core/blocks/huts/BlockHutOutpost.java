package com.deathfrog.mctradepost.core.blocks.huts;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.util.SoundUtils;
import com.mojang.logging.LogUtils;
import com.minecolonies.api.configuration.ServerConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.minecolonies.api.util.Log;

public class BlockHutOutpost extends MCTPBaseBlockHut
{
    public static final String HUT_NAME = "blockhutoutpost";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getHutName()
    {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.outpost;
    }


    /**
     * Sets the linked block position of the given item stack to the given block position.
     *
     * @param stack The item stack to set the linked block position for.
     * @param pos The block position to set as the linked block position.
     */
    public static void setLinkedBlockPos(ItemStack stack, BlockPos pos)
    {
        if (!stack.isEmpty() && pos != null)
        {
            stack.set(MCTPModDataComponents.LINKED_BLOCK_POS.get(), pos);
        }
    }


    /**
     * Retrieves the linked block position from the given item stack.
     *
     * @param stack The item stack to retrieve the linked block position from.
     * @return The linked block position from the item stack, or null if the stack does not contain a linked block position.
     */
    @Nullable
    public static BlockPos getLinkedBlockPos(ItemStack stack)
    {
        return stack.getOrDefault(MCTPModDataComponents.LINKED_BLOCK_POS.get(), null);
    }


    /**
     * Checks if the given item stack has a linked block position.
     *
     * @param stack The item stack to check.
     * @return True if the item stack has a linked block position, false otherwise.
     */
    public static boolean hasLinkedBlockPos(ItemStack stack)
    {
        return !stack.isEmpty() && stack.has(MCTPModDataComponents.LINKED_BLOCK_POS.get());
    }

    /**
     * Clears the linked block position from the given item stack.
     * If the item stack is empty, this method does nothing.
     * @param stack The item stack to clear the linked block position from.
     */
    public static void clearLinkedBlockPos(ItemStack stack)
    {
        if (!stack.isEmpty())
        {
            stack.remove(MCTPModDataComponents.LINKED_BLOCK_POS.get());
        }
    }

    @Override
    public boolean setup(ServerPlayer player,
        Level world,
        BlockPos pos,
        Blueprint blueprint,
        RotationMirror rotationMirror,
        boolean fancyPlacement,
        String pack,
        String path)
    {
        Log.getLogger().info("Special outpost placer! ");
        // TODO: Eliminate if not used.

        BlockState anchor = blueprint.getBlockState(blueprint.getPrimaryBlockOffset());
        if (anchor.getBlock() instanceof AbstractBlockHut && (fancyPlacement || !player.isCreative()))
        {
            if (!(Boolean) ((ServerConfiguration) IMinecoloniesAPI.getInstance().getConfig().getServer()).blueprintBuildMode.get() &&
                !this.canPaste(anchor.getBlock(), player, pos))
            {
                return false;
            }
            else
            {
                world.destroyBlock(pos, true);
                world.setBlockAndUpdate(pos, anchor);
                ((BlockHutOutpost) anchor.getBlock())
                    .onBlockPlacedByBuildTool(world, pos, anchor, player, (ItemStack) null, rotationMirror, pack, path);
                if ((Boolean) ((ServerConfiguration) IMinecoloniesAPI.getInstance().getConfig().getServer()).blueprintBuildMode.get())
                {
                    return true;
                }
                else
                {
                    IColony colony = colonyByTrack(world, player, pos);

                    if (colony == null)
                    {
                        Log.getLogger().error("No colony found along track at {}", pos);
                        return false;
                    }

                    IBuilding building = colony.getBuildingManager().getBuilding(pos);
                    if (building == null)
                    {
                        if (anchor.getBlock() != MCTradePostMod.blockHutOutpost.get())
                        {
                            SoundUtils.playErrorSound(player, player.blockPosition());
                            Log.getLogger().error("BuildTool: building is null!", new Exception());
                            return false;
                        }
                    }
                    else
                    {
                        SoundUtils.playSuccessSound(player, player.blockPosition());
                        if (building.getTileEntity() != null)
                        {
                            building.getTileEntity().setColony(colony);
                        }

                        String adjusted = path.replace(".blueprint", "");
                        String num = adjusted.substring(path.replace(".blueprint", "").length() - 2, adjusted.length() - 1);
                        building.setStructurePack(pack);
                        building.setBlueprintPath(path);

                        try
                        {
                            building.setBuildingLevel(Integer.parseInt(num));
                        }
                        catch (NumberFormatException var14)
                        {
                            building.setBuildingLevel(1);
                        }

                        building.setRotationMirror(rotationMirror);
                        building.onUpgradeComplete(building.getBuildingLevel());
                    }

                    return true;
                }
            }
        }
        else
        {
            return true;
        }
    }

    /**
     * Determines if a player can paste a given block at a given position.
     *
     * @param anchor the block to be pasted
     * @param player the player attempting the paste
     * @param pos    the position at which the block is being pasted
     * @return true if the player can paste the block, false otherwise
     */
    protected boolean canPaste(final Block anchor, final Player player, final BlockPos pos)
    {
        return true;
    }

    protected IColony colonyByTrack(final Level world, final Player player, final BlockPos pos)
    {
        // TODO: Update this.
        return IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
    }
}
