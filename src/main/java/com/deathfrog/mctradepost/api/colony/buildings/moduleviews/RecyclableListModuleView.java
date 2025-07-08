package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.views.RecyclingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.minecolonies.api.util.Utils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class RecyclableListModuleView extends ItemListModuleView
{
    // protected Function<IBuildingView, Set<ItemStorage>> whItems;
    private final List<ItemStorage> pendingItems = new ArrayList<>();

    public RecyclableListModuleView(String id, String desc, boolean inverted)
    {
        super(id, desc, inverted, building -> ((RecyclingView) building).getRecyclableItems());
    }

    /**
     * Gets the icon to display for this module in the GUI.
     * 
     * @return The icon to display.
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/recycling.png");
    }

    /**
     * Gets the description of the module to display in the GUI.
     * 
     * @return The description of the module.
     */
    @Override
    public String getDesc()
    {
        return "com.deathfrog.mctradepost.gui.workerhuts.recyclingengineer.recyclables";
    }

    /**
     * Deserializes the state of the RecyclableListModuleView from the given buffer. Clears the current set of items and repopulates it
     * with data read from the buffer. The buffer is expected to contain a serialized int with the number of items, and then that many
     * ItemStorage objects, each deserialized using a utility method.
     * 
     * @param buf The buffer containing the serialized state to deserialize.
     */
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        super.deserialize(buf);

        this.pendingItems.clear();
        int size = buf.readInt();

        for (int j = 0; j < size; ++j)
        {
            this.pendingItems.add(new ItemStorage(Utils.deserializeCodecMess(buf)));
        }
    }
}
