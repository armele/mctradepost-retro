package com.deathfrog.mctradepost.api.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import java.util.Objects;
import java.util.Optional;
import com.minecolonies.api.util.IItemHandlerCapProvider;

// --- Utilities to obtain a handler or provider for a block position ---
public final class ItemHandlerHelpers
{
    /** Try capability; if missing, wrap the Container (handles double chests). */
    public static Optional<IItemHandler> getHandler(Level level, BlockPos pos, @org.jetbrains.annotations.Nullable Direction side)
    {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return Optional.empty();

        // 1) NeoForge capability lookup (preferred)
        IItemHandler cap = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, level.getBlockState(pos), be, side);
        if (cap != null) return Optional.of(cap);

        // 2) Wrap a vanilla Container if present
        Container container = extractContainer(level, pos, be);
        if (container == null) return Optional.empty();

        if (side != null && container instanceof WorldlyContainer wc)
        {
            return Optional.of(new SidedInvWrapper(wc, side));
        }
        return Optional.of(new InvWrapper(container));
    }

    /** Same as getHandler, but returns your provider interface for helpers that expect it. */
    public static Optional<IItemHandlerCapProvider> getProvider(Level level,
        BlockPos pos,
        @org.jetbrains.annotations.Nullable Direction side)
    {
        return getHandler(level, pos, side).map(SimpleHandlerProvider::of);
    }

    /** Gets a Container, joining double chests correctly. */
    private static Container extractContainer(Level level, BlockPos pos, BlockEntity be)
    {
        if (be instanceof ChestBlockEntity cbe)
        {
            // Join double chests (if any)
            Container joined =
                ChestBlock.getContainer((ChestBlock) level.getBlockState(pos).getBlock(), level.getBlockState(pos), level, pos, false);
            if (joined != null) return joined;
            return cbe; // fallback: single chest container
        }
        if (be instanceof Container c) return c;
        return null;
    }
}