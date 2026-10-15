package com.deathfrog.mctradepost.core.blocks.blockentity;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.WaterRouteRegistry;
import com.deathfrog.mctradepost.core.inventory.TradeDockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Menu anchor for the authoritative water-link data stored in {@link WaterRouteRegistry}. */
public class TradeDockBlockEntity extends BlockEntity implements MenuProvider
{
    public TradeDockBlockEntity(BlockPos pos, BlockState state)
    {
        super(MCTradePostTileEntities.TRADE_DOCK.get(), pos, state);
    }

    public List<WaterRouteRegistry.WaterLink> links()
    {
        if (!(level instanceof ServerLevel server)) return List.of();
        WaterRouteRegistry registry = WaterRouteRegistry.get(server);
        return registry.linksFor(worldPosition).stream().map(link -> registry.validate(server, link)).toList();
    }

    @SuppressWarnings("null")
    public void writeMenuData(RegistryFriendlyByteBuf buffer)
    {
        final BlockPos localWorldPos = worldPosition;

        if (localWorldPos == null) return;

        buffer.writeBlockPos(localWorldPos);
        List<WaterRouteRegistry.WaterLink> links = links();
        buffer.writeVarInt(links.size());
        for (WaterRouteRegistry.WaterLink link : links)
        {
            buffer.writeBlockPos(link.other(localWorldPos));
            buffer.writeVarInt(link.distance());
            buffer.writeEnum(link.status());
            buffer.writeVarLong(link.lastValidated());
            buffer.writeBoolean(link.breakStart() != null);
            if (link.breakStart() != null) buffer.writeBlockPos(link.breakStart());
            buffer.writeBoolean(link.breakEnd() != null);
            if (link.breakEnd() != null) buffer.writeBlockPos(link.breakEnd());
        }
    }

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("container.mctradepost.trade_dock");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @Nonnull Inventory inventory, @Nonnull Player player)
    {
        return new TradeDockMenu(id, inventory, worldPosition, links());
    }
}
