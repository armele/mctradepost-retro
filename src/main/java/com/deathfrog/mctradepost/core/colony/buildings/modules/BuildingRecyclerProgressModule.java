package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class BuildingRecyclerProgressModule extends AbstractBuildingModule implements IPersistentModule
{
    public BuildingRecyclerProgressModule()
    {
        super();
    }

    /**
     * Serializes the current state of the BuildingRecyclerProgressModule to a {@link RegistryFriendlyByteBuf}.
     * Called when the module needs to be synced with the client.
     * 
     * @param buf      The {@link RegistryFriendlyByteBuf} to serialize the state of the module into.
     * @param fullSync Whether or not to serialize the full state of the module, or just the delta.
     */
    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {
        // MCTradePostMod.LOGGER.info("Serialzing BuildingRecyclerProgressModule to view.");

        super.serializeToView(buf, fullSync);
        BuildingRecycling recyclingCenter = (BuildingRecycling) building;
        
        CompoundTag tag = new CompoundTag();
        tag.putInt("maxProcessors", recyclingCenter.getMachineCapacity());

        buf.writeNbt(recyclingCenter.serializeRecyclingProcessors(buf.registryAccess()));
        buf.writeNbt(tag);
    }
}
