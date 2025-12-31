package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowStewmolierIngredientModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class StewmelierIngredientModuleView extends AbstractBuildingModuleView
{

    private final List<ItemStorage> ingredientList = new ArrayList<>();

    /**
     * Read this view from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer to read this view from.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        ingredientList.clear();
        final int size = buf.readInt();
        for (int i = 0; i < size; i++)
        {
            ItemStack itemStack = Utils.deserializeCodecMess(buf);
            int protectedQuantity = buf.readInt();
            ingredientList.add(new ItemStorage(itemStack, protectedQuantity));
        }
    }

    /**
     * Gets the description of the module to display in the GUI.
     * 
     * @return The description of the module.
     */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.minecolonies.coremod.gui.stewmelier.ingredients");
    }

    /**
     * Gets the window for this module.
     * 
     * @return The window for this module.
     */
    @Override
    public BOWindow getWindow()
    {
        return new WindowStewmolierIngredientModule(buildingView, this);
    }
    
    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/stew.png");
    }

    /**
     * Get the ingredient list.
     * 
     * @return the ingredient list.
     */
    public List<ItemStorage> getIngredients()
    {
        return ingredientList;
    }

}
