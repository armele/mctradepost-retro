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
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData.TrackConnectionStatus;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
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
    protected Map<StationData, Boolean> connectionstatus = new HashMap<>();

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
     * Retrieves the track connection status of the given station data object.
     * If the status is unknown, returns TrackConnectionStatus.UNKNOWN.
     * If the status is known to be connected, returns TrackConnectionStatus.CONNECTED.
     * If the status is known to be disconnected, returns TrackConnectionStatus.DISCONNECTED.
     * 
     * @param station The station data object for which the track connection status is being retrieved.
     * @return The track connection status of the given station data object, or TrackConnectionStatus.UNKNOWN if unknown.
     */
    public TrackConnectionStatus stationConnectionStatus(StationData station)
    {
        Boolean connected = connectionstatus.get(station);

        if (connected != null) 
        {
            return connected ? TrackConnectionStatus.CONNECTED : TrackConnectionStatus.DISCONNECTED;
        }
        else
        {
            return TrackConnectionStatus.UNKNOWN;
        }
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

        final int connSize = buf.readInt();
        for (int i = 0; i < connSize; i++) {
            final CompoundTag keyTag = buf.readNbt();
            final boolean value = buf.readBoolean();

            if (keyTag == null) 
            {
                BuildingStation.LOGGER.warn("Invalid connectionresults entry {}; keyTag={}, value={}", i, keyTag, value);
                continue;
            }

            final StationData deserializedKey = StationData.fromNBT(keyTag);
            if (deserializedKey == null) 
            {
                BuildingStation.LOGGER.warn("Failed to deserialize StationData key in connectionresults; skipping {}", i);
                continue;
            }

            this.connectionstatus.put(deserializedKey, Boolean.valueOf(value));
        }
    }
}
