package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowPetTrainingItemsModule;
import com.ldtteam.blockui.views.BOWindow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

public class PetTrainingItemsModuleView extends AbstractBuildingModuleView
{

    /**
     * Read this view from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer to read this view from.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {

    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowPetTrainingItemsModule(this);
    }

    /**
     * Whether this tab displays trainable pets rather than herd animals.
     *
     * @return true for trainable pets
     */
    public boolean displaysPets()
    {
        return true;
    }

    /**
     * Whether husbandry research is required before this tab can display its contents.
     *
     * @return true when husbandry research is required
     */
    public boolean requiresHusbandryResearch()
    {
        return false;
    }

    /**
     * Gets the explanatory tooltip shown for this tab.
     *
     * @return the tooltip text
     */
    public Component getItemsTooltip()
    {
        return Component.translatable("com.minecolonies.coremod.gui.petstore.trainingtips.pets.hover");
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
   @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/collar.png");
    }

    /**
     * Gets the description of the module to display in the GUI.
     * 
     * @return The description of the module.
     */
    @Override
    public @Nullable Component  getDesc()
    {
        return Component.translatable("com.minecolonies.coremod.gui.petstore.trainingitems");
    }

}
