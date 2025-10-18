package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.huts.WindowHutLiving;
import com.minecolonies.core.colony.buildings.moduleviews.LivingBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;

import net.minecraft.resources.ResourceLocation;

public class OutpostLivingBuildingModuleView extends LivingBuildingModuleView
{
        @NotNull
        @Override
        public BOWindow getWindow()
        {
            return new WindowHutLiving((LivingBuildingView) this.getBuildingView());
        }

        @Override
        public boolean isPageVisible()
        {
            return true;
        }

        @Override
        public String getDesc()
        {
            return "com.mctradepost.core.gui.modules.outpost.residents";
        }

        /**
         * Get the icon of the module.
         * 
         * @return the icon to show.
         */
        @Override
        public ResourceLocation getIconResourceLocation()
        {
            return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/home.png");
        }
        }
