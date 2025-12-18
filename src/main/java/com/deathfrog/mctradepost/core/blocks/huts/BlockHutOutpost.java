package com.deathfrog.mctradepost.core.blocks.huts;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.blocks.BlockOutpostMarker;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.mojang.logging.LogUtils;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.MessageUtils;

public class BlockHutOutpost extends MCTPBaseBlockHut
{
    public static final String HUT_NAME = "blockhutoutpost";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlockHutOutpost() {
        super();
    }

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
            stack.set(LINKED_BLOCK_POS(), pos);
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
        return stack.getOrDefault(LINKED_BLOCK_POS(), NullnessBridge.assumeNonnull(BlockPos.ZERO));
    }


    /**
     * Checks if the given item stack has a linked block position.
     *
     * @param stack The item stack to check.
     * @return True if the item stack has a linked block position, false otherwise.
     */
    public static boolean hasLinkedBlockPos(ItemStack stack)
    {
        return !stack.isEmpty() && stack.has(LINKED_BLOCK_POS());
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
            stack.remove(LINKED_BLOCK_POS());
        }
    }


    /**
     * A special outpost placer that requires the presence of an outpost marker at the same position
     * unless the player is in creative mode.
     *
     * @param player The player placing the outpost (may be null if not applicable)
     * @param world The world the outpost is being placed in
     * @param pos The position the outpost is being placed at
     * @param blueprint The blueprint of the outpost being placed
     * @param rotationMirror The rotation mirror of the outpost being placed
     * @param fancyPlacement Whether fancy placement is enabled
     * @param pack The name of the blueprint pack
     * @param path The path to the blueprint
     * @return True if the outpost was successfully placed, false otherwise
     */
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
        boolean allowPlacement = false;

        if (!MCTPConfig.outpostEnabled.get())
        {
            if (player != null) 
            {
                player.displayClientMessage(NullnessBridge.assumeNonnull(
                    Component.translatable("com.mctradepost.outpost.disallowed")),
                    true
                );
            }
            return false;
        }

        if (pos == null) return false;

        // Make sure the chunk is present (defensive)
        if (!world.isAreaLoaded(pos, 1)) return false;

        if (player.isCreative())
        {
            Log.getLogger().info("Creative mode placement allowed.");
            allowPlacement = true;
        }


        // Require the Outpost Marker at this exact position unless we're in creative mode.
        if (world.getBlockState(pos).is(outpostMarker())) 
        {
            Log.getLogger().info("Outpost marker found.");
            allowPlacement = true;
        }

        if (allowPlacement) 
        {
            Log.getLogger().info("Placing outpost.");
            return super.setup(player, world, pos, blueprint, rotationMirror, fancyPlacement, pack, path);
        }

        if (player != null) 
        {
            player.displayClientMessage(NullnessBridge.assumeNonnull(
                Component.translatable("com.mctradepost.outpost.buildfailure")),
                true
            );
        }
        
        Log.getLogger().info("Outpost placement not allowed here.");

        return false;
    }

    /**
     * Gets the requirements for placing an outpost hut at the given position.
     * @param level the ClientLevel to get the requirements for
     * @param pos the BlockPos to get the requirements for
     * @param player the LocalPlayer to get the requirements for
     * @return a list of MutableComponent representing the requirements
     */
    @Override
    public List<MutableComponent> getRequirements(final ClientLevel level, final BlockPos pos, final LocalPlayer player)
    {
        List<MutableComponent> requirements = super.getRequirements(level, pos, player);
        requirements.add(Component.translatable("com.mctradepost.outpost.requirements"));
        return requirements;
    }


    /**
     * Determines if the outpost hut can be placed at the given position.
     * The outpost hut must be within 16 blocks of the outpost marker and
     * the outpost marker must be within 800 blocks of the colony center.
     * @param pos the BlockPos to check
     * @param player the Player to check
     * @return true if the outpost hut can be placed, false otherwise
     */
    @Override
    public boolean canPlaceAt(final BlockPos pos, final Player player)
    {

        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), pos);
        if (colony == null || pos == null) return false;

        int maxDistance = MCTPConfig.maxDistance.get();

        if (colony.getCenter().distSqr(pos) > (maxDistance * maxDistance))
        {
            MessageUtils.format("The outpost hut block must be within " + maxDistance + " blocks of your colony center.").sendTo(player);
            return false;
        } 


        // Creative mode always allowed (within distance)
        if (player.isCreative())
        {
            return true;
        }

        // Get the level reference
        Level level = player.level();

        // Define search radius
        final int radius = 16;

        // Search for an Outpost Marker block within radius 
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++)
        {
            for (int y = -radius; y <= radius; y++)
            {
                for (int z = -radius; z <= radius; z++)
                {
                    checkPos.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (level.getBlockState(checkPos).is(outpostMarker()))
                    {
                        // Found a marker nearby — allow placement
                        return true;
                    }
                }
            }
        }

        MessageUtils.format("The outpost hut block must be within 16 blocks of your outpost marker.").sendTo(player);
        return false;
    }


    /**
     * Gets the outpost marker block.
     *
     * @return the outpost marker block, never null
     */
    @Nonnull static BlockOutpostMarker outpostMarker()
    {   
        BlockOutpostMarker outpostMarker = MCTradePostMod.BLOCK_OUTPOST_MARKER.get();
        return NullnessBridge.assumeNonnull(outpostMarker);
    }

    /**
     * A data component type which stores a BlockPos indicating the location of a linked outpost marker block.
     *
     * @return the data component type for the linked outpost marker block position
     */
    @Nonnull static DataComponentType<BlockPos> LINKED_BLOCK_POS()
    {
        return NullnessBridge.assumeNonnull(MCTPModDataComponents.LINKED_BLOCK_POS.get());
    }
}
