package com.deathfrog.mctradepost.core.blocks.huts;

import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.mojang.logging.LogUtils;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.minecolonies.api.util.Log;

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
        return stack.getOrDefault(MCTPModDataComponents.LINKED_BLOCK_POS.get(), BlockPos.ZERO);
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
        boolean allowPlacement = false;

        // Make sure the chunk is present (defensive)
        if (!world.isAreaLoaded(pos, 1)) return false;

        if (player.isCreative())
        {
            Log.getLogger().info("Creative mode placement allowed.");
            allowPlacement = true;
        }


        // Require the Outpost Marker at this exact position unless we're in creative mode.
        if (world.getBlockState(pos).is(MCTradePostMod.BLOCK_OUTPOST_MARKER.get())) 
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
            player.displayClientMessage(
                Component.translatable("com.mctradepost.outpost.buildfailure"),
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
}
