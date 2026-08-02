package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowThriftShopOffersModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketOffer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.util.Utils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ThriftShopOffersModuleView extends AbstractBuildingModuleView
{
    List<MarketOffer> offers = new ArrayList<>();
    protected long lastRoll = 0L;
    int rerollCost = -1;
    int subscriptionCapacity = 0;
    private final List<ItemStack> subscriptions = new ArrayList<>();

    /**
     * Read this view from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer to read this view from.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        lastRoll = buf.readLong();
        rerollCost = buf.readInt();
        subscriptionCapacity = buf.readVarInt();
        subscriptions.clear();
        int subscriptionCount = buf.readVarInt();
        for (int i = 0; i < subscriptionCount; i++)
        {
            subscriptions.add(Utils.deserializeCodecMess(buf));
        }

        offers.clear();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++)
        {
            ItemStack stack = Utils.deserializeCodecMess(buf);

            if (stack == null) continue;

            int price = buf.readVarInt();
            int tierOrd = buf.readVarInt();

            MarketTier tier = MarketTier.TIER1_COMMON;
            MarketTier[] values = MarketTier.values();
            if (tierOrd >= 0 && tierOrd < values.length) tier = values[tierOrd];

            offers.add(new MarketOffer(stack, tier, price));
        }

        // Keep active subscriptions at the top, including offers loaded from saves
        // created before subscription rows were inserted first on the server.
        offers.sort((left, right) -> Boolean.compare(isSubscribed(right.stack()), isSubscribed(left.stack())));
    }

    /**
     * Gets the description of the module to display in the GUI.
     * 
     * @return The description of the module.
     */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.minecolonies.coremod.gui.thriftshop.offers");
    }

    /**
     * Gets the window for this module.
     * 
     * @return The window for this module.
     */
    @Override
    public BOWindow getWindow()
    {
        return new WindowThriftShopOffersModule(buildingView, this);
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/thrift.png");
    }

    /**
     * Gets the list of market offers that are currently available for the thrift shop.
     * 
     * @return The list of market offers that are currently available for the thrift shop.
     */
    public List<MarketOffer> getOffers()
    {
        return offers;
    }

    public long getLastRollDay()
    {
        return lastRoll;
    }

    public int getRerollCost()
    {
        return rerollCost;
    }

    /**
     * Gets the subscription capacity synchronized from the server.
     *
     * @return zero when subscription research is locked, otherwise the level-based capacity
     */
    public int getSubscriptionCapacity()
    {
        return subscriptionCapacity;
    }

    /**
     * Checks whether an item has a synchronized active subscription.
     *
     * @param stack item to inspect
     * @return whether an equivalent subscription exists
     */
    @SuppressWarnings("null")
    public boolean isSubscribed(@Nonnull ItemStack stack)
    {
        return subscriptions.stream().anyMatch(subscription -> ItemStack.isSameItemSameComponents(subscription, stack));
    }
}
