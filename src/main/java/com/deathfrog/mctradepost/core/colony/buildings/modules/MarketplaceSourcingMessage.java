package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketplaceSourcingModule;
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

/** Sends a retained-search or subscription-management action to the owning Marketplace. */
public class MarketplaceSourcingMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID,
        "marketplace_sourcing_message", MarketplaceSourcingMessage::new);

    /** Supported sourcing actions. */
    public enum Action { ADD_SEARCH, REMOVE_SEARCH, INVEST, CANCEL_INVESTMENT, CANCEL_SUBSCRIPTION }

    private final Action action;
    private final ItemStack stack;
    private final int investmentLevel;

    /**
     * Creates a sourcing action.
     *
     * @param building target Marketplace view
     * @param action requested action
     * @param stack affected item
     * @param investmentLevel requested investment level, or zero when unused
     */
    public MarketplaceSourcingMessage(IBuildingView building, Action action, ItemStack stack, int investmentLevel)
    {
        super(TYPE, building);
        this.action = action;
        this.stack = stack.copy();
        this.investmentLevel = investmentLevel;
    }

    /** Deserializes a sourcing action from the network. */
    protected MarketplaceSourcingMessage(RegistryFriendlyByteBuf buf, PlayMessageType<?> type)
    {
        super(buf, type);
        action = Action.values()[buf.readVarInt()];
        stack = Utils.deserializeCodecMess(buf);
        investmentLevel = buf.readVarInt();
    }

    /** {@inheritDoc} */
    @Override
    protected void toBytes(@NotNull RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeVarInt(action.ordinal());
        Utils.serializeCodecMess(buf, stack);
        buf.writeVarInt(investmentLevel);
    }

    /** {@inheritDoc} */
    @Override
    protected void onExecute(IPayloadContext context, ServerPlayer player, IColony colony, IBuilding building)
    {

        ItemStack localStack = stack;

        if (localStack == null) return;

        if (!building.hasModule(MCTPBuildingModules.MARKETPLACE_SOURCING)) return;
        MarketplaceSourcingModule module = building.getModule(MCTPBuildingModules.MARKETPLACE_SOURCING);
        long day = colony.getWorld().getDayTime() / MarketDailyRoller.TICKS_PER_DAY;
        switch (action)
        {
            case ADD_SEARCH -> module.addSearch(localStack);
            case REMOVE_SEARCH -> module.removeSearch(localStack);
            case INVEST -> module.invest(localStack, investmentLevel, player, day);
            case CANCEL_INVESTMENT -> module.cancelInvestment(localStack);
            case CANCEL_SUBSCRIPTION -> module.cancelSubscription(localStack);
        }
    }
}
