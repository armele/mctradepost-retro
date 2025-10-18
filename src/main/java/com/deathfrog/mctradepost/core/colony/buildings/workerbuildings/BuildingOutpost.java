package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.ITradeCapable;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.DefaultBuildingInstance;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;

public class BuildingOutpost extends DefaultBuildingInstance implements ITradeCapable
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int STATION_VALIDATION_COOLDOWN = 100;
    public static final int STATION_CONNECTION_COOLDOWN = 20;

    protected static final String TAG_CONNECTED_STATION = "connected_station";

    protected int stationValidationCooldown = STATION_CONNECTION_COOLDOWN;
    protected BuildingStation connectedStation = null;
    
    /**
     * Map of track connection results for other trade capable buildings (by station data).
     */
    private Map<StationData, TrackConnectionResult> connectionresults = new HashMap<>();
    

    public BuildingOutpost(@NotNull IColony colony, BlockPos pos, String schematicName, int maxLevel)
    {
        super(colony, pos, schematicName, maxLevel);
    }


    @Override
    public String getSchematicName()
    {
        return ModBuildings.OUTPOST_ID;
    }

    @Override
    public void onRestart(ICitizenData citizen)
    {
        super.onRestart(citizen);

        establishConnectedStation();
    }

    @Override
    public int getClaimRadius(int newLevel) 
    {
        return 1;
    }

    /**
     * Retrieves the BuildingStation object that is connected to this outpost by tracks.
     * This will be null if no connected station is found.
     * 
     * @return the connected station, or null if no station is connected
     */
    public BuildingStation getConnectedStation()
    {
        return connectedStation;
    }

    /**
     * Returns true if the outpost is not connected to any other station, false otherwise.
     * 
     * @return true if the outpost is not connected, false otherwise
     */
    public boolean isDisconnected()
    {
        return connectedStation == null;
    }

    /**
     * Checks if the outpost has any missing child buildings, i.e. if outpostFarm or outpostBuilder is null.
     * @return true if any child buildings are missing, false otherwise.
     */
    public boolean isMissingChildBuildings()
    {
        boolean missing = false;

        BuildingFarmer outpostFarm = getOutpostFarmer();
        BuildingBuilder outpostBuilder = getOutpostBuilder();

        if (outpostFarm == null || outpostBuilder == null)
        {
            missing = true;
        }

        return missing;
    }

    @Override
    public CompoundTag serializeNBT(@SuppressWarnings("null") HolderLookup.Provider provider)
    {
        CompoundTag compound = super.serializeNBT(provider);
        BlockPosUtil.write(compound, TAG_CONNECTED_STATION, getConnectedStation() != null ? getConnectedStation().getPosition() : BlockPos.ZERO);
        return compound;
    }

    @SuppressWarnings("null")
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag compound) 
    {
        super.deserializeNBT(provider, compound);
        BlockPos buildingId = BlockPosUtil.read(compound, TAG_CONNECTED_STATION);
        if (!BlockPos.ZERO.equals(buildingId))
        {
            this.connectedStation = (BuildingStation) getColony().getBuildingManager().getBuilding(buildingId);
        }
    }

    /**
     * If a scout is not assigned to this outpost, will send a message to the player (if given) or the colony (if no player is given) stating that a scout is missing.
     * If the requested position is not equal to the outpost's current position, will send a message to the player (if given) or the colony (if no player is given) 
     * stating that the outpost's builder has been switched.
     * 
     * Will return the outpost's current position as the requested builder location.
     * 
     * @param player the player that is attempting to build the outpost (may be null)
     * @param requestedBuilder the position that the outpost is to be built at
     * @return the position that the outpost was built at, or BlockPos.ZERO if no outpost was built
     */
    protected BlockPos outpostBuilderPosition(Player player, BlockPos requestedBuilder)
    {

        BuildingBuilder outpostBuilder = getOutpostBuilder();

        if (outpostBuilder == null)
        {
            if (player != null)
            {
                MessageUtils.format("com.mctradepost.outpost.builder.missing").sendTo(player);
            }
            else
            {
                MessageUtils.format("com.mctradepost.outpost.builder.missing").sendTo(getColony());
            }
            return BlockPos.ZERO;
        }

        if (!requestedBuilder.equals(outpostBuilder.getPosition()))
        {
            if (player != null)
            {
                MessageUtils.format("com.mctradepost.outpost.builder.switched", outpostBuilder.getBuildingDisplayName()).sendTo(player);
            }
            else
            {
                MessageUtils.format("com.mctradepost.outpost.builder.switched", outpostBuilder.getBuildingDisplayName()).sendTo(getColony());
            }
        }

        return outpostBuilder.getPosition();
    }

    @Override
    public void requestUpgrade(Player player, BlockPos builder)
    {
        super.requestUpgrade(player, outpostBuilderPosition(player, builder));
    }

    @Override
    protected void requestWorkOrder(WorkOrderType type, BlockPos builder) 
    {
        super.requestWorkOrder(type, outpostBuilderPosition(null, builder));
    }

    @Override
    public boolean canBeBuiltByBuilder(int newLevel) 
    {
        return getBuildingLevel() + 1 == newLevel;
    }

    /**
     * Called every tick that the colony updates.
     * 
     * @param colony the colony that this building is a part of
     */
    public void onColonyTick(IColony colony)
    {
        super.onColonyTick(colony);

        if (stationValidationCooldown-- <= 0)
        {
            establishConnectedStation();

            if (this.isDisconnected())
            {
                stationValidationCooldown = STATION_CONNECTION_COOLDOWN;
            }
            else
            {
                stationValidationCooldown = STATION_VALIDATION_COOLDOWN;   
            }
        }

        LOGGER.info("Connection cooldown: {}", stationValidationCooldown);
    }

    /**
     * Iterates through all buildings in the colony and checks if any of them are
     * BuildingStations that are connected to this outpost by tracks.
     * 
     * If a connected station is found, it is stored in the connectedStation field.
     */
    protected void establishConnectedStation()
    {
        Collection<IBuilding> buildings = colony.getBuildingManager().getBuildings().values();
        BuildingStation candidateStation = null;
        boolean isCurrentlyDisconnected = this.isDisconnected();
        boolean connected = false;

        for (IBuilding building : buildings)
        {
            if (building instanceof BuildingStation station)
            {
                candidateStation = station;

                TrackConnectionResult result = TrackPathConnection.arePointsConnectedByTracks((ServerLevel) colony.getWorld(), this.getRailStartPosition(), station.getRailStartPosition(), isCurrentlyDisconnected);
                connectionresults.put(new StationData(station), result);
                
                if (result.isConnected())
                {
                    connected = true;
                    break;
                }
            }
        }

        if (!connected)
        {
            if (candidateStation == null)
            {
                LOGGER.info("Unable to find connected station - no candidate stations found.");
            }
            else
            {
                LOGGER.info("Unable to find connected station between {} and {}.", this.getRailStartPosition(), candidateStation.getRailStartPosition());
            }
            connectedStation = null;
        }
        else
        {
            connectedStation = candidateStation;
            LOGGER.info("Found connected station at {} with rail start position of {}.", connectedStation.getPosition(), connectedStation.getRailStartPosition());
        }
    }

    /**
     * Retrieves the BuildingBuilder object that is a child of this outpost.
     * This is the builder that constructs new buildings for the outpost.
     * 
     * @return the BuildingBuilder object that is a child of this outpost, or null if none is found.
     */
    public BuildingBuilder getOutpostBuilder()
    {
        BuildingBuilder outpostBuilder = null;

        if (getChildren().isEmpty())
        {
            LOGGER.error("The outpost building requires a Farm and Builder child buildings.");
            return null;
        }

        for (BlockPos childSpot : this.getChildren())
        {
            IBuilding building = this.getColony().getBuildingManager().getBuilding(childSpot);

            if (building instanceof BuildingBuilder)
            {
                outpostBuilder = (BuildingBuilder) building;
            } 
        }

        return outpostBuilder;
    }

    /**
     * Retrieves the BuildingFarmer object that is a child of this outpost.
     * This is the farm that provides resources to the outpost.
     * 
     * @return the BuildingFarmer object that is a child of this outpost, or null if none is found.
     */
    public BuildingFarmer getOutpostFarmer()
    {
        BuildingFarmer outpostFarm = null;

        if (getChildren().isEmpty())
        {
            LOGGER.error("The outpost building requires a Farm and Builder child buildings.");
            return null;
        }

        for (BlockPos childSpot : this.getChildren())
        {
            IBuilding building = this.getColony().getBuildingManager().getBuilding(childSpot);

            if (building instanceof BuildingFarmer)
            {
                outpostFarm = (BuildingFarmer) building;
                return outpostFarm;
            }
        }

        return outpostFarm;
    }

    @Override
    public void onUpgradeComplete(int buildingLevel) 
    {
        super.onUpgradeComplete(buildingLevel);
        
        establishConnectedStation();
    }

    /**
     * Retrieves the BlockPos of the starting point of the rail network for this outpost.
     * If there is no starting point specified, the position of the building itself is returned.
     * If there are multiple starting points specified in the building's NBT, the first one is used.
     * A warning will be logged if there are multiple starting points found.
     * @return The BlockPos of the starting point of the rail network for this train outpost.
     */
    public BlockPos getRailStartPosition()
    {
        List<BlockPos> locations = getLocationsFromTag(BuildingStation.STATION_START);
        if (locations.isEmpty())
        {
            return this.getPosition();
        }
        else if (locations.size() > 1)
        {
            LOGGER.warn("More than one station start location found, using the first one.");
        }
        return locations.get(0);
    }


    @Override
    public TrackConnectionResult getTrackConnectionResult(StationData stationData)
    {
        TrackConnectionResult result = connectionresults.get(stationData);
        
        LOGGER.info("Outpost connection result status: {}", result == null ? "Null" : result.isConnected() ? "Connected" : "Not connected.");

        return result;
    }


    @Override
    public void markTradesDirty()
    {
        markDirty();
    }

    /**
     * Called when a shipment has been delivered to another station.
     * 
     * @param shipmentSent The shipment that was delivered.
     */
    @Override
    public void onShipmentDelivered(ExportData shipmentSent)
    {
        // TODO: Experience for Scout worker.
    }

    /**
     * Called when a shipment has been received from another station.
     * 
     * @param shipmentReceived The shipment that was received.
     */
    @Override
    public void onShipmentReceived(ExportData shipmentReceived)
    {
        // TODO: Distribute to requesting citizen/hut.
    }

}