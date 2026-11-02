package com.deathfrog.mctradepost.api.colony.buildings.views;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;

/*
 * ✅ Best Practice If a field should: Be visible on the client UI → put it in serializeNBT() in the building and deserializeNBT() both
 * in the building and deserialize here in the view.
 */

@OnlyIn(Dist.CLIENT)
public class MarketplaceView extends AbstractBuildingView
{
    private boolean displayShelfContents = true;
    private final List<ItemStack> shelfItems = new ArrayList<>();

    public MarketplaceView(final IColonyView colony, final BlockPos location)
    {
        super(colony, location);
    }

    public boolean shouldDisplayShelfContents()
    {
        return displayShelfContents;
    }

    public List<ItemStack> getShelfItems()
    {
        return shelfItems;
    }
}
