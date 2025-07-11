package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class WithdrawMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "withdraw_message", WithdrawMessage::new);

    protected WithdrawMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
    }

    public WithdrawMessage(final IBuildingView building) 
    {
        super(TYPE, building);
    }

    @Override
    protected void onExecute(IPayloadContext payload, ServerPlayer player, IColony colony, IBuilding building)
    {
        final int mintingLevel = MCTPConfig.mintingLevel.get();

        if (building instanceof BuildingMarketplace marketplace && marketplace.getBuildingLevel() >= mintingLevel) {
            ItemStack coins = marketplace.mintCoins(player, 1);
            if (!coins.isEmpty()) {
                player.addItem(coins);
                MessageUtils.format("mctradepost.marketplace.minted").sendTo(player);
            }  
        }
        else 
        {
            MessageUtils.format("mctradepost.marketplace.invalid").sendTo(player);
        }
    }
    
}
