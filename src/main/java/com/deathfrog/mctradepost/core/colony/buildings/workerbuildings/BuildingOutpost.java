package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.requestsystem.IRequestSatisfaction;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.OutpostRequestResolver;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.ITradeCapable;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.DefaultBuildingInstance;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;

public class BuildingOutpost extends DefaultBuildingInstance implements ITradeCapable, IRequestSatisfaction
{
    public enum OutpostOrderState
    {
        NEEDED,                 // The outpost has a need for something
        NEEDS_ITEM_FOR_SHIPPING,// Connected station needs something before it can ship
        SHIPMENT_INITIATED,     // Connected station has started the shipment
        RECEIVED,               // Outpost has received the order from the connected station
        READY_FOR_DELIVERY,     // Scout is ready to deliver the order (it is in their inventory)
        DELIVERED               // Scout has delivered the order to the necessary place in the outpost
    };

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int STATION_VALIDATION_COOLDOWN = 100;
    public static final int STATION_CONNECTION_COOLDOWN = 20;

    protected static final String TAG_CONNECTED_STATION = "connected_station";
    protected static final String NBT_OUTPOST_TOKEN = "mctp_outpost_token";

    protected int stationValidationCooldown = STATION_CONNECTION_COOLDOWN;
    protected BuildingStation connectedStation = null;
    protected OutpostRequestResolver outpostResolver;
    protected IToken<?> outpostResolverToken = null;


    // Map of request tokens to shipment tracking objects.
    public Map<IToken<?>, OutpostShipmentTracking> expectedShipments = new ConcurrentHashMap<>();

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

        if (outpostResolverToken != null)
        {
            StandardFactoryController.getInstance().serializeTag(provider, outpostResolverToken);
        }

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
        
        if (compound.contains(NBT_OUTPOST_TOKEN)) 
        {
            outpostResolverToken = StandardFactoryController.getInstance().deserializeTag(provider, compound.getCompound(NBT_OUTPOST_TOKEN));
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

        List<IRequest<?>> requestsToRemove = new ArrayList<>();

        for (IToken<?> requestId : expectedShipments.keySet())
        {
            IRequest<?> request = this.getColony().getRequestManager().getRequestForToken(requestId);
            OutpostShipmentTracking tracking = expectedShipments.get(requestId);

            if (tracking == null)
            {
                continue;
            }
            else
            {
                if (tracking.getState() == OutpostOrderState.DELIVERED 
                    || request.getState() == RequestState.CANCELLED 
                    || request.getState() == RequestState.FAILED
                    || request.getState() == RequestState.COMPLETED)
                {
                    requestsToRemove.add(request);
                }
            }
        }

        for (IRequest<?> request : requestsToRemove)
        {
            expectedShipments.remove(request.getId());
        }
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
        OutpostShipmentTracking receivedShipment = expectedShipments.get(shipmentSent);

        if (receivedShipment != null)
        {
            receivedShipment.state = OutpostOrderState.DELIVERED;
        }
    }

    /**
     * Called when a shipment has been received from another station.
     * 
     * @param shipmentReceived The shipment that was received.
     */
    @Override
    public void onShipmentReceived(ExportData shipmentReceived)
    {
        OutpostShipmentTracking matchingShipment = expectedShipments.get(shipmentReceived);
        
        if (matchingShipment != null)
        {
            matchingShipment.state = OutpostOrderState.RECEIVED;
        }

        BuildingStationExportModule exports = connectedStation.getModule(MCTPBuildingModules.EXPORTS);

        if (exports == null)
        {
            LOGGER.warn("No export module on connected station - cannot remove order.");
            return;
        }

        exports.removeExport(shipmentReceived);
    }

    

    /**
     * Retrieves the number of expected shipments for this outpost.
     * This is the number of shipments that are currently in transit from another station to this outpost.
     * @return The number of expected shipments for this outpost.
     */
    public int getExpectedShipmentSize()
    {
        return expectedShipments.size();
    }

    /**
     * Adds a shipment to the expected shipments for this outpost. The shipment is associated with the given outpost destination.
     * When the shipment is received, the state of the shipment is updated to OutpostOrderState.RECEIVED.
     * 
     * @param shipment The shipment to add to the expected shipments.
     * @param outpostDestination The outpost destination that the shipment is associated with.
     */
    public OutpostShipmentTracking addExpectedShipment(IRequest<?> request, ExportData shipment, BlockPos outpostDestination)
    {
        OutpostShipmentTracking tracking = expectedShipments.get(request.getId());

        if (tracking == null)
        {
            tracking = new OutpostShipmentTracking(outpostDestination, shipment, OutpostOrderState.NEEDED);    
            expectedShipments.put(request.getId(), tracking);
        } 
        else
        {
            tracking.setAssociatedExportData(shipment);
        }

        return tracking;
    }

    /**
     * Checks if the outpost has an expected shipment associated with the given export data.
     * 
     * @param exportData The export data to check for an associated expected shipment.
     * @return True if the outpost has an expected shipment associated with the given export data, false otherwise.
     */
    public boolean hasExpectedShipment(ExportData exportData) 
    { 

        for (OutpostShipmentTracking shipment : expectedShipments.values())
        {
            if (shipment.exportData != null && shipment.exportData.equals(exportData))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Retrieves the OutpostShipment associated with the given request, or null if no matching shipment is found.
     * @param shipment The request to find the associated OutpostShipment for.
     * @return The OutpostShipment associated with the given request, or null if no matching shipment is found.
     */
    public OutpostShipmentTracking trackingForRequest(IRequest<?> request)
    {        
        OutpostShipmentTracking tracking = expectedShipments.get(request);

        if (tracking == null)
        {
            tracking = this.addExpectedShipment(request, null, request.getRequester().getLocation().getInDimensionLocation());
        }
    
        return tracking;
    }


    /**
     * Creates a collection of request resolvers for this outpost.
     * This collection contains all request resolvers from the superclass, as well as an additional resolver for outpost requests.
     * The outpost request resolver is responsible for resolving requests for the outpost.
     * @return A collection of request resolvers for this outpost.
     */
    @Override
    public ImmutableCollection<IRequestResolver<?>> createResolvers()
    {
        final ImmutableCollection<IRequestResolver<?>> supers = super.createResolvers();
        final ImmutableList.Builder<IRequestResolver<?>> builder = ImmutableList.builder();

        if (outpostResolverToken != null)
        {
            outpostResolver = new OutpostRequestResolver(getRequester().getLocation(), outpostResolverToken);
        }
        else
        {
            outpostResolver = new OutpostRequestResolver(getRequester().getLocation(), colony.getRequestManager().getFactoryController().getNewInstance(TypeConstants.ITOKEN));
            outpostResolverToken = outpostResolver.getId();
        }

        builder.addAll(supers);
        builder.add(outpostResolver);

        return builder.build();
    }


    /**
     * Retrieves the request resolver responsible for resolving requests for this outpost.
     * @return The request resolver responsible for resolving requests for this outpost.
     */
    public IRequestResolver<?> getOutpostResolver()
    {
        if (outpostResolver == null)
        {
            createResolvers();
        }

        return outpostResolver;
    }

    /**
     * Checks if the given building is an outpost building, either directly or indirectly through its parent.
     * A building is considered an outpost building if it is an instance of BuildingOutpost, or if its parent is.
     * @param checkBuilding The building to check.
     * @return True if the building is an outpost building, false otherwise.
     */
    public static boolean isOutpostBuilding(@Nonnull IBuilding checkBuilding)
    {
        boolean qualifies = false;

        if (checkBuilding instanceof BuildingOutpost)
        {
            qualifies = true;
        }
        else
        {
            BlockPos parent = checkBuilding.getParent();

            if (parent != null && checkBuilding.getColony() != null)
            {
                IBuilding parentBuilding = checkBuilding.getColony().getBuildingManager().getBuilding(parent);
                
                if (parentBuilding instanceof BuildingOutpost)
                {
                    qualifies = true;
                }
            }
        } 

        return qualifies;
    }

    /**
     * Retrieves all open requests from all buildings in this outpost.
     * This will include all requests from this outpost itself, as well as all requests from all its children.
     * @return A list of all open requests in this outpost.
     */
    public List<IRequest<?>> getOutpostRequests()
    {        
        List<IRequest<?>> outpostRequests = new ArrayList<>();

        for (BlockPos child : this.getChildren())
        {
            IBuilding childBuilding = this.getColony().getBuildingManager().getBuilding(child);

            // LOGGER.info("Checking requests in building {}", childBuilding.getBuildingDisplayName());

            for (ICitizenData citizen : childBuilding.getAllAssignedCitizen())
            {
                // LOGGER.info("Checking requests for citizen {}", citizen.getName());


                final Collection<IRequest<?>> openRequests = childBuilding.getOpenRequests(citizen.getId());

                if (openRequests != null)
                {
                    outpostRequests.addAll(openRequests);
                }
            }

            // Gather requests not associated with the specific citizen, too.
            final Collection<IRequest<?>> openRequests = childBuilding.getOpenRequests(-1);

            if (openRequests != null)
            {
                outpostRequests.addAll(openRequests);
            }
        }

        return outpostRequests;
    }

    public ICitizenData getScout()
    {
        List<ICitizenData> employees = this.getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.scout.get()).getAssignedCitizen();
        
        if (employees.isEmpty()) {
            return null;
        }
    
        return employees.get(0);
    }

}