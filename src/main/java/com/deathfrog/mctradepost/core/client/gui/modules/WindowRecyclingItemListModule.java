package com.deathfrog.mctradepost.core.client.gui.modules;

import com.minecolonies.api.colony.buildings.modules.IItemListModuleView;
import com.minecolonies.core.client.gui.modules.building.ItemListModuleWindow;

import net.minecraft.resources.ResourceLocation;

public class WindowRecyclingItemListModule extends ItemListModuleWindow
{

    public WindowRecyclingItemListModule(IItemListModuleView moduleView, ResourceLocation resource)
    {
        super(moduleView, resource);
    }

   protected void updateResourceList() {
        super.updateResourceList();
   }


    
}
