package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.client.gui.modules.WindowMarketplaceItemListModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ItemValueRegistry;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.minecolonies.api.util.Utils;

public class MarketplaceItemListModuleView extends ItemListModuleView
{

    public MarketplaceItemListModuleView(String id, String desc, boolean inverted, Function<IBuildingView, Set<ItemStorage>> allItems)
    {
        super(id, desc, inverted, allItems);
    }

    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        this.clearItems();
        int size = buf.readInt();

        for (int j = 0; j < size; ++j)
        {
            ItemStorage item = new ItemStorage(Utils.deserializeCodecMess(buf));
            
            this.addItem(item);
        }
    }

    /**
     * Returns the value of a given item, or 0 if the item is not in the list.
     * 
     * @param item The item to get the value for.
     * @return The value of the given item.
     */
    public int getValueForItem(Item item)
    {
        return ItemValueRegistry.getValue(item);
    }

    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowMarketplaceItemListModule("mctradepost:gui/layouthuts/layoutmarketplacelistmodule.xml",
            this.buildingView,
            this);
    }
}
