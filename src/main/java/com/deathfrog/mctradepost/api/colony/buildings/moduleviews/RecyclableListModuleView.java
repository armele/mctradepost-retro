package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.Set;
import java.util.function.Function;

import com.deathfrog.mctradepost.api.colony.buildings.views.RecyclingView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;

public class RecyclableListModuleView extends ItemListModuleView {
    protected Function<IBuildingView, Set<ItemStorage>> whItems;

    public RecyclableListModuleView(String id, String desc, boolean inverted)
    {   
        super(id, desc, inverted, building -> ((RecyclingView)building).getRecyclableItems());

    }

   @Override
    public String getIcon()
    {
        return "info";
    }

    @Override
    public String getDesc()
    {
        return "com.deathfrog.mctradepost.gui.workerhuts.recyclingengineer.recyclables";
    }

}
