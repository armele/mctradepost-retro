package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.common.base.Function;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class OutpostExportModuleView extends ItemListModuleView
{
    protected Set<ItemStorage> possibleItems = new HashSet<ItemStorage>();

    public OutpostExportModuleView(String id, String desc)
    {
        super(id, desc, false, null);
    }

    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        super.deserialize(buf);

        this.possibleItems.clear();
        int size = buf.readInt();

        for (int j = 0; j < size; ++j)
        {
            this.possibleItems.add(new ItemStorage(Utils.deserializeCodecMess(buf)));
        }
    }

    @Override
    public Function<IBuildingView, Set<ItemStorage>> getAllItems()
    {
        return (IBuildingView v) -> Collections.unmodifiableSet(possibleItems);
    }

    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/export.png");
    }
}
