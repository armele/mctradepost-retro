package com.deathfrog.mctradepost.api.colony.buildings.views;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;

/*
 * ✅ Best Practice
* If a field should:
* Be visible on the client UI → put it in serializeNBT() in the building and deserializeNBT() both in the building and deserialize here in the view.
* 
*/

@OnlyIn(Dist.CLIENT)
public class StationView extends AbstractBuildingView 
{
    protected Map<BlockPos, StationData> stations = new HashMap<>();

    public StationView(final IColonyView colony, final BlockPos location) 
    {
        super(colony, location);
    }

    /**
     * Retrieves the map of stations for this building, as deserialized from the server and stored client-side.
     * 
     * @return A map of BlockPos to StationData.
     */
    public Map<BlockPos, StationData> getStations() 
    {
        return stations;
    }

    /**
     * Deserializes the state of the StationView from the given buffer. Clears the current set of stations and repopulates it
     * with data read from the buffer. The buffer is expected to contain a serialized int with the number of stations, and then
     * that many StationData objects, each deserialized using a utility method.
     * 
     * @param buf The buffer containing the serialized state to deserialize.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf) 
    {
        super.deserialize(buf);
        final int stationsize = buf.readInt();
        for (int i = 0; i < stationsize; i++)
        {
            final CompoundTag compound = buf.readNbt();
            if (compound != null)
            {
                StationData data = StationData.fromNBT(compound);
                if (data != null) 
                {
                    // BuildingStation.LOGGER.info("Adding station to view: {}", data);
                    this.stations.put(data.getBuildingPosition(), data);
                }
                else
                {
                    BuildingStation.LOGGER.warn("Failed to deserialize station from tag: {}", compound);
                }
            }
        }
    }
}
