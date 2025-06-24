package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowRecyclingItemListModule;
import com.deathfrog.mctradepost.item.SouvenirItem;
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
    protected Map<Item, Integer> itemValues = new HashMap<>();

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
            ItemStorage souvenirVersion = new ItemStorage(Utils.deserializeCodecMess(buf));

            Item item = SouvenirItem.getOriginal(souvenirVersion.getItemStack());
            itemValues.put(item, SouvenirItem.getSouvenirValue(souvenirVersion.getItemStack()));
            
            this.addItem(new ItemStorage(item));
        }
    }

    public int getValueForItem(Item item)
    {
        return itemValues.getOrDefault(item, 0);
    }

    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowRecyclingItemListModule("mctradepost:gui/layouthuts/layoutmarketplacelistmodule.xml",
            this.buildingView,
            this);
    }
}
