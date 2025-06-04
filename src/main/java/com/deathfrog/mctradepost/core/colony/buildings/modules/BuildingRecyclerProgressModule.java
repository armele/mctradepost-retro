package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;

import net.minecraft.network.RegistryFriendlyByteBuf;

public class BuildingRecyclerProgressModule extends AbstractBuildingModule implements IPersistentModule
{
    public BuildingRecyclerProgressModule()
    {
        super();
    }

    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {
        MCTradePostMod.LOGGER.info("Serialzing BuildingRecyclerProgressModule to view.");

        super.serializeToView(buf, fullSync);
        BuildingRecycling recyclingCenter = (BuildingRecycling) building;
        
        buf.writeNbt(recyclingCenter.serializeRecyclingProcessors(buf.registryAccess()));
    }
}
