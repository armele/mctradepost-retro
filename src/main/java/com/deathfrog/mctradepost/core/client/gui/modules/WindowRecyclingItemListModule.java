package com.deathfrog.mctradepost.core.client.gui.modules;

import com.minecolonies.api.colony.buildings.modules.IItemListModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.modules.ItemListModuleWindow;

public class WindowRecyclingItemListModule extends ItemListModuleWindow
{

    public WindowRecyclingItemListModule(String res, IBuildingView building, IItemListModuleView moduleView)
    {
        super(res, building, moduleView);
    }

   protected void updateResourceList() {
        super.updateResourceList();
   }


    
}
