package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Read this view from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer to read this view from.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        lastRoll = buf.readLong();

        offers.clear();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++)
        {
            ItemStack stack = Utils.deserializeCodecMess(buf);
            int price = buf.readVarInt();
            int tierOrd = buf.readVarInt();

            MarketTier tier = MarketTier.TIER1_COMMON;
            MarketTier[] values = MarketTier.values();
            if (tierOrd >= 0 && tierOrd < values.length) tier = values[tierOrd];

            offers.add(new MarketOffer(stack, tier, price));
        }
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
}
