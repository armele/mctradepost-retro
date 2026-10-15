package com.deathfrog.mctradepost.core.inventory;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.WaterRouteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Read-only snapshot of the linkages involving one Trade Dock. */
public class TradeDockMenu extends AbstractContainerMenu
{
    public record LinkView(BlockPos destination,
        int distance,
        WaterRouteRegistry.Status status,
        long lastValidated,
        BlockPos breakStart,
        BlockPos breakEnd)
    {
        
    }

    private final @Nonnull BlockPos dock;
    private final List<LinkView> links;

    @SuppressWarnings("null")
    public TradeDockMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data)
    {
        super(MCTradePostMod.TRADE_DOCK_MENU.get(), id);
        dock = data.readBlockPos();
        int count = Math.min(256, data.readVarInt());
        List<LinkView> decoded = new ArrayList<>(count);

        for (int i = 0; i < count; i++)
        {
            BlockPos destination = data.readBlockPos();
            int distance = data.readVarInt();
            WaterRouteRegistry.Status status = data.readEnum(WaterRouteRegistry.Status.class);
            long lastValidated = data.readVarLong();
            BlockPos breakStart = data.readBoolean() ? data.readBlockPos() : null;
            BlockPos breakEnd = data.readBoolean() ? data.readBlockPos() : null;
            decoded.add(new LinkView(destination, distance, status, lastValidated, breakStart, breakEnd));
        }
        links = List.copyOf(decoded);
    }

    @SuppressWarnings("null")
    public TradeDockMenu(int id, Inventory inventory, BlockPos dock, List<WaterRouteRegistry.WaterLink> serverLinks)
    {
        super(MCTradePostMod.TRADE_DOCK_MENU.get(), id);
        this.dock = dock.immutable();
        this.links = serverLinks.stream()
            .map(link -> new LinkView(link
                .other(dock), link.distance(), link.status(), link.lastValidated(), link.breakStart(), link.breakEnd()))
            .toList();
    }

    public BlockPos dock()
    {
        return dock;
    }

    public List<LinkView> links()
    {
        return links;
    }

    @SuppressWarnings("null")
    @Override
    public boolean stillValid(@Nonnull Player player)
    {
        return player.level().getBlockState(dock).is(MCTradePostMod.TRADE_DOCK.get()) &&
            player.distanceToSqr(dock.getX() + .5D, dock.getY() + .5D, dock.getZ() + .5D) <= 64D;
    }

    @SuppressWarnings("null")
    @Override
    public @Nonnull ItemStack quickMoveStack(@Nonnull Player player, int index)
    {
        return ItemStack.EMPTY;
    }
}
