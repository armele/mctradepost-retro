package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowPetAssignmentModule;
import com.google.common.collect.ImmutableList;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PetAssignmentModuleView extends AbstractBuildingModuleView
{
    private final List<IBuildingView> herdingBuildings = new ArrayList<>();
    private boolean dirty = true;

    /**
     * Read this view from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer to read this view from.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        herdingBuildings.clear();

        final int size = buf.readInt();

        // MCTradePostMod.LOGGER.info("Getting {} herding buildings", size);

        for (int i = 0; i < size; i++)
        {
            CompoundTag compound = buf.readNbt();

            // MCTradePostMod.LOGGER.info("NBT read while deserializing herding buliding {}: {}", i, compound);

            if (compound == null)
            {
                continue;
            }
            IBuildingView herdBuildingView = BuildingUtil.buildingViewFromNBT(compound);
            if (herdBuildingView != null)
            {   
                // MCTradePostMod.LOGGER.info("View got herding building: {}", herdBuildingView);
                herdingBuildings.add(herdBuildingView);
            }
            else
            {
                // MCTradePostMod.LOGGER.warn("View did not get herding building from NBT: {}", compound);
            }
        }

        dirty = false;
    }

    public boolean isDirty()
    {
        return dirty;
    }

    public void markDirty()
    {
        dirty = true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowPetAssignmentModule(buildingView, this);
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
   @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/pets.png");
    }

    /**
     * Gets the description of the module to display in the GUI.
     * 
     * @return The description of the module.
     */
    @Override
    public String getDesc()
    {
        return "com.minecolonies.coremod.gui.petstore.assignment";
    }

    /**
     * Retrieves an immutable list of herding buildings associated with this module.
     * 
     * @return An immutable list of IBuildingView representing the herding buildings.
     */
    public ImmutableList<IBuildingView> getHerdingBuildings()
    {

        return ImmutableList.copyOf(herdingBuildings);
    }
}
