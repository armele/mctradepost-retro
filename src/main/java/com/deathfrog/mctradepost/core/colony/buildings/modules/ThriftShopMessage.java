package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import com.mojang.logging.LogUtils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;

import org.jetbrains.annotations.NotNull;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_RAREFINDS;

public class ThriftShopMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "thriftshop_message", ThriftShopMessage::new);

    public enum ThriftAction
    {
        PURCHASE, REROLL
    }

    private static final String THRIFTSHOP_PURCHASE = "mctradepost.thriftshop.purchase";

    /**
     * What ingredient are we adding or removing?
     */
    private ThriftAction thriftAction;

    /**
     * What item are we putting into the stew?
     */
    private ItemStack itemStack;

    /**
     * What cost must we pay for this item?
     */
    int cost = 0;

    /**
     * Creates a Transfer Items request
     *
     * @param itemStack to be take from the player for the building
     * @param cost  coins being exchanged for
     * @param building  the building we're executing on.
     */
    public ThriftShopMessage(final IBuildingView building, ThriftAction action, final ItemStack itemStack, final int cost)
    {
        super(TYPE, building);
        this.itemStack = itemStack.copy();
        this.thriftAction = action;
        this.cost = cost;
    }

    public ThriftShopMessage(final IBuildingView building, final ThriftAction action)
    {
        super(TYPE, building);
        this.itemStack = ItemStack.EMPTY;
        this.thriftAction = action;
        this.cost = 0;
    }

    protected ThriftShopMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        itemStack = Utils.deserializeCodecMess(buf);
        thriftAction = ThriftAction.values()[buf.readInt()];
        cost = buf.readInt();
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);

        Utils.serializeCodecMess(buf, itemStack);
        buf.writeInt(thriftAction.ordinal());
        buf.writeInt(cost);
    }

    /**
     * Server-side handler for the ThriftShopMessage.
     * This method will be called on the server when the client sends a ThriftShopMessage.
     * The method will take the appropriate action based on the value of the thriftAction field.
     * If the thriftAction is REMOVE, the method will remove the ingredient from the building's ingredient list.
     * If the thriftAction is ADD, the method will add the ingredient to the building's ingredient list.
     * After modifying the ingredient list, the method will notify all connected stations of the change.
     * 
     * @param ctxIn the payload context for this message
     * @param player the player who sent the message
     * @param colony the colony the message is for
     * @param building the building that the message is for
     * @see AbstractBuildingServerMessage#onExecute(IPayloadContext, ServerPlayer, IColony, IBuilding)
     */    
    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        if (building.hasModule(MCTPBuildingModules.THRIFTSHOP))
        {
            if (thriftAction == ThriftAction.PURCHASE)
            {
                TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Executing Rare Finds purchase for {}.", itemStack.getHoverName()));

                ItemStack purchaseItem = itemStack.copy();
                if (purchaseItem == null || purchaseItem.isEmpty()) return;

                MessageUtils.format(THRIFTSHOP_PURCHASE).sendTo(player);
                building.getModule(MCTPBuildingModules.THRIFTSHOP).purchaseItem(purchaseItem, cost, player);
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Executing Rare Finds reroll request."));
                building.getModule(MCTPBuildingModules.THRIFTSHOP).rerollSelections(player);
            }

        }
    }
}
