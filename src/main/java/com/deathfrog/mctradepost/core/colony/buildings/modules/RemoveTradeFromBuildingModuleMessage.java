package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class RemoveTradeFromBuildingModuleMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "remove_trade_from_building_module", RemoveTradeFromBuildingModuleMessage::new);

    /**
     * How many item need to be transfer from the player inventory to the building chest.
     */
    private ItemStack itemStack;

    /**
     * How many item need to be transfer from the player inventory to the building chest.
     */
    private int quantity;

    /**
     * Creates a Transfer Items request
     *
     * @param itemStack to be take from the player for the building
     * @param quantity  of item needed to be transfered
     * @param building  the building we're executing on.
     */
    public RemoveTradeFromBuildingModuleMessage(final IBuildingView building, final ItemStack itemStack, final int quantity)
    {
        super(TYPE, building);
        this.itemStack = itemStack;
        this.quantity = quantity;
    }

    protected RemoveTradeFromBuildingModuleMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        itemStack = Utils.deserializeCodecMess(buf);
        quantity = buf.readInt();
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        Utils.serializeCodecMess(buf, itemStack);
        buf.writeInt(quantity);
    }

    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        if (building.hasModule(MCTPBuildingModules.IMPORTS))
        {
            building.getModule(MCTPBuildingModules.IMPORTS).removeTrade(itemStack);
        }
    }
}
