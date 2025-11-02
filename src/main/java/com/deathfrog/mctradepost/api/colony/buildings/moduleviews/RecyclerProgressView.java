package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowRecyclerProgressModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling.RecyclingProcessor;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Building statistic module.
 */
public class RecyclerProgressView extends AbstractBuildingModuleView
{


    private Set<RecyclingProcessor> recyclingProcessors = new LinkedHashSet<RecyclingProcessor>();
    private int maxProcessors = -1;

    public RecyclerProgressView() {
        super();
        // MCTradePostMod.LOGGER.info("Constructing RecyclerProgressView.");
    }


    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
   @Override
    public String getIcon()
    {
        return "crafting";
    }

    @Override
    public Component getDesc()
    {
        return Component.translatable("com.mctradepost.core.gui.modules.recyclerprogress");
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowRecyclerProgressModule(this, maxProcessors);
    }

    /**
     * Deserializes the state of the RecyclerProgressView from the given buffer.
     * Clears the current set of recycling processors and repopulates it
     * with data read from the buffer. The buffer is expected to contain a
     * serialized NBT tag with a list of recycling processors, each represented
     * as a CompoundTag. Each processor's state is deserialized and added to
     * the recyclingProcessors set.
     *
     * @param buf The buffer containing the serialized state to deserialize.
     */
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf) {
        // MCTradePostMod.LOGGER.info("Deserializing RecyclerProgressView.");
        
        recyclingProcessors.clear();

        CompoundTag tag = buf.readNbt();

        if (tag != null && tag.contains(BuildingRecycling.SERIALIZE_RECYCLINGPROCESSORS_TAG, Tag.TAG_LIST)) {
            ListTag processorListTag = tag.getList(BuildingRecycling.SERIALIZE_RECYCLINGPROCESSORS_TAG, Tag.TAG_COMPOUND);
            for (Tag element : processorListTag) {
                CompoundTag processorTag = (CompoundTag) element;
                RecyclingProcessor processor = new RecyclingProcessor();
                processor.deserialize(buf.registryAccess(), processorTag);
                recyclingProcessors.add(processor);
            }
        }

        CompoundTag max = buf.readNbt();

        if (max != null && max.contains("maxProcessors", Tag.TAG_INT)) {
            int maxProcessors = max.getInt("maxProcessors");
            this.maxProcessors = maxProcessors;
        }
    }

    /**
     * Retrieves a set of RecyclingProcessor objects representing the current
     * state of all recycling processors associated with this building.
     * @return a set of RecyclingProcessor objects representing the current
     *         state of all recycling processors associated with this building.
     */
    public Set<RecyclingProcessor> getRecyclingProcessors() {
        return recyclingProcessors;
    }
}