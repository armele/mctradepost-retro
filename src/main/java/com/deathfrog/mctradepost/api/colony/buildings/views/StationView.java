package com.deathfrog.mctradepost.api.colony.buildings.views;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData.TrackConnectionStatus;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult.FailureKind;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult.RouteFailureDiagnostic;
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
    protected Map<DimPos, StationData> stations = new HashMap<>();
    protected Set<StationData> connectionAttempts = new HashSet<>();
    protected Map<StationData, Boolean> connectionstatus = new HashMap<>();
    protected Map<StationData, List<TrackRoute.SegmentType>> connectionModes = new HashMap<>();
    protected Map<StationData, List<ResourceLocation>> connectionDimensions = new HashMap<>();
    protected Map<StationData, RouteFailureDiagnostic> connectionFailures = new HashMap<>();

    public StationView(final IColonyView colony, final BlockPos location) 
    {
        super(colony, location);
    }

    /**
     * Retrieves the map of stations for this building, as deserialized from the server and stored client-side.
     * 
     * @return map of dimension-qualified positions to station data
     */
    public Map<DimPos, StationData> getStations()
    {
        return stations;
    }

    /**
     * Tests whether the local station is configured to attempt a route to a destination.
     *
     * @param station destination station
     * @return true when route discovery is enabled
     */
    public boolean shouldAttemptConnection(StationData station)
    {
        return connectionAttempts.contains(station);
    }

    /**
     * Updates the client-side connection-attempt state while the server processes the request.
     *
     * @param station destination station
     * @param enabled whether discovery should be enabled
     */
    public void setConnectionAttempt(StationData station, boolean enabled)
    {
        if (enabled)
        {
            connectionAttempts.add(station);
        }
        else
        {
            connectionAttempts.remove(station);
        }
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

    /** @return distinct transport modes in outbound order for the cached connection */
    public List<TrackRoute.SegmentType> stationConnectionModes(StationData station)
    {
        return connectionModes.getOrDefault(station, List.of());
    }

    /**
     * Gets the distinct non-origin dimensions traversed by the cached connection.
     *
     * @param station destination station whose route is being queried
     * @return immutable dimension identifiers in first-use order
     */
    public List<ResourceLocation> stationConnectionDimensions(StationData station)
    {
        return connectionDimensions.getOrDefault(station, List.of());
    }

    /**
     * Gets the best failed-search frontier retained for a disconnected station.
     *
     * @param station destination station whose route is being queried
     * @return failure diagnostic, or {@code null} when no useful frontier was retained
     */
    public RouteFailureDiagnostic stationConnectionFailure(StationData station)
    {
        return connectionFailures.get(station);
    }

    /**
     * Deserializes the station list and cached connection details, including transport modes and non-origin route dimensions.
     * 
     * @param buf The buffer containing the serialized state to deserialize.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf) 
    {
        super.deserialize(buf);
        stations.clear();
        connectionAttempts.clear();
        connectionstatus.clear();
        connectionModes.clear();
        connectionDimensions.clear();
        connectionFailures.clear();
        final int stationsize = buf.readInt();
        for (int i = 0; i < stationsize; i++)
        {
            final CompoundTag compound = buf.readNbt();
            if (compound != null)
            {
                StationData data = StationData.fromNBT(compound);
                boolean attemptConnection = buf.readBoolean();
                if (data != null) 
                {
                    // BuildingStation.LOGGER.info("Adding station to view: {}", data);
                    this.stations.put(data.getDimensionalPosition(), data);
                    if (attemptConnection)
                    {
                        connectionAttempts.add(data);
                    }
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
            final int modeCount = buf.readVarInt();
            final java.util.ArrayList<TrackRoute.SegmentType> modes = new java.util.ArrayList<>(modeCount);
            for (int modeIndex = 0; modeIndex < modeCount; modeIndex++)
            {
                modes.add(buf.readEnum(TrackRoute.SegmentType.class));
            }
            final int dimensionCount = buf.readVarInt();
            final java.util.ArrayList<ResourceLocation> dimensions = new java.util.ArrayList<>(dimensionCount);
            for (int dimensionIndex = 0; dimensionIndex < dimensionCount; dimensionIndex++)
            {
                dimensions.add(buf.readResourceLocation());
            }
            RouteFailureDiagnostic failure = null;
            if (buf.readBoolean())
            {
                DimPos position = DimPos.STREAM_CODEC.decode(buf);
                TrackRoute.SegmentType mode = buf.readEnum(TrackRoute.SegmentType.class);
                FailureKind reason = buf.readEnum(FailureKind.class);
                failure = new RouteFailureDiagnostic(position, mode, reason);
            }

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
            this.connectionModes.put(deserializedKey, List.copyOf(modes));
            this.connectionDimensions.put(deserializedKey, List.copyOf(dimensions));
            if (failure != null)
            {
                this.connectionFailures.put(deserializedKey, failure);
            }
        }
    }
}
