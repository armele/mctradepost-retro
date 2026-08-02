package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowMarketplaceSourcingModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketplaceSourcingModule.RetainedSearch;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketplaceSourcingModule.Subscription;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.util.Utils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Client-side snapshot of Marketplace retained searches and subscriptions. */
public class MarketplaceSourcingModuleView extends AbstractBuildingModuleView
{
    private final List<RetainedSearch> searches = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private int searchCapacity;
    private int subscriptionCapacity;
    private long currentDay;

    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        searchCapacity = buf.readVarInt();
        subscriptionCapacity = buf.readVarInt();
        currentDay = buf.readLong();
        searches.clear();
        int searchCount = buf.readVarInt();
        for (int i = 0; i < searchCount; i++)
        {
            ItemStack stack = Utils.deserializeCodecMess(buf);
            MarketTier tier = readTier(buf.readVarInt());
            searches.add(new RetainedSearch(stack, tier, buf.readVarInt(), buf.readLong()));
        }
        subscriptions.clear();
        int subscriptionCount = buf.readVarInt();
        for (int i = 0; i < subscriptionCount; i++)
        {
            subscriptions.add(new Subscription(Utils.deserializeCodecMess(buf), readTier(buf.readVarInt()), buf.readLong()));
        }
    }

    /** @return retained-search entries received from the server. */
    public List<RetainedSearch> getSearches()
    {
        return searches;
    }

    /** @return subscription entries received from the server. */
    public List<Subscription> getSubscriptions()
    {
        return subscriptions;
    }

    /** @return currently unlocked retained-search capacity. */
    public int getSearchCapacity()
    {
        return searchCapacity;
    }

    /** @return currently unlocked subscription capacity. */
    public int getSubscriptionCapacity()
    {
        return subscriptionCapacity;
    }

    /** @return server day used to calculate remaining investment time. */
    public long getCurrentDay()
    {
        return currentDay;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("mctradepost.retained_search.title");
    }

    /** {@inheritDoc} */
    @Override
    public BOWindow getWindow()
    {
        return new WindowMarketplaceSourcingModule(buildingView, this);
    }

    /** {@inheritDoc} */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/search.png");
    }

    /** Converts a synchronized ordinal to a safe market tier. */
    private MarketTier readTier(int ordinal)
    {
        MarketTier[] values = MarketTier.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MarketTier.TIER1_COMMON;
    }
}
