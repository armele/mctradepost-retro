package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;

import net.minecraft.network.RegistryFriendlyByteBuf;

public class BuildingStationConnectionModule extends AbstractBuildingModule implements IPersistentModule
{
    public BuildingStationConnectionModule()
    {
        super();
    }


    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {
        // No-op for now
    }
}
