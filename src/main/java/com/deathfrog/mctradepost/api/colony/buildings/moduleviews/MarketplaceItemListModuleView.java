package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowMarketplaceItemListModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.minecolonies.api.util.Utils;
import com.mojang.logging.LogUtils;

public class MarketplaceItemListModuleView extends ItemListModuleViewExt
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public Set<ItemStorage> itemOptionSet = new HashSet<>();

    public MarketplaceItemListModuleView(String id, Component desc)
    {
        super(id, desc, false, new AtomicReference<>());
    }

    protected Set<ItemStorage> computeAllItems(IBuildingView view)
    {
        return itemOptionSet;
    }

    /**
     * Deserializes the state of this module from the given buffer.
     * The state is represented as a list of item stacks, where each item stack is serialized using the {@link Utils#deserializeCodecMess(RegistryFriendlyByteBuf, ItemStack)} method.
     * The list size is written as an integer, followed by the serialized item stacks.
     * After deserializing the list of items, the list of items to be kept when the inventory is cleared is also deserialized in the same format.
     * @param buf The buffer containing the serialized state to deserialize.
     */
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        super.deserialize(buf);

        // Read in the list of possible items.
        itemOptionSet.clear();
        int size = buf.readInt();

        for (int j = 0; j < size; ++j)
        {
            ItemStorage item = new ItemStorage(Utils.deserializeCodecMess(buf));
            
            itemOptionSet.add(item);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowMarketplaceItemListModule(this, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "gui/layouthuts/layoutmarketplacelistmodule.xml"));
    }
}
