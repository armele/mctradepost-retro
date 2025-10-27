package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.util.TraceUtils;
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
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;

public class BuildingOutpost extends AbstractBuilding implements ITradeCapable, IRequestSatisfaction
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
    private static final String TAG_REQUEST_TOKEN             = "request_token";
    private static final String TAG_REQUEST_STATE             = "request_state";
    private static final String TAG_REQUEST_TRACKING = "requestTracking";

    protected int stationValidationCooldown = STATION_CONNECTION_COOLDOWN;
    protected BuildingStation connectedStation = null;
    protected OutpostRequestResolver outpostResolver;
    protected IToken<?> outpostResolverToken = null;


    // Map of request tokens to shipment tracking objects.
    public Map<IToken<?>, OutpostShipmentTracking> requestTracking = new ConcurrentHashMap<>();

    protected Map<StationData, TrackConnectionResult> connectionresults = new HashMap<>();
    

    public BuildingOutpost(@NotNull IColony colony, BlockPos pos, String schematicName, int maxLevel)
    {
        // super(colony, pos, schematicName, maxLevel);
        super(colony, pos);
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

    /**
     * Serializes the outpost's state into an NBT tag. The tag contains the following elements:
     * <ul>
     * <li>"ConnectedStation": the position of the building station that is connected to this outpost by tracks, stored as a BlockPos.</li>
     * <li>"OutpostResolverToken": the token used to identify the outpost's resolver, stored as a CompoundTag representing the token.</li>
     * <li>"RequestTracking": a list of block positions that are currently being tracked by the outpost, stored as a ListTag of BlockPos.</li>
     * </ul>
     * If any of these values are missing, the default values are set: BlockPos.ZERO for the connected station, null for the outpost resolver token, and an empty list for the request tracking.
     * 
     * @param provider The holder lookup provider for item and block references.
     * @return the serialized NBT tag.
     */
    @Override
    public CompoundTag serializeNBT(@SuppressWarnings("null") HolderLookup.Provider provider)
    {
        CompoundTag compound = super.serializeNBT(provider);
        BlockPosUtil.write(compound, TAG_CONNECTED_STATION, getConnectedStation() != null ? getConnectedStation().getPosition() : BlockPos.ZERO);

        if (outpostResolverToken != null)
        {
            CompoundTag outpostToken = StandardFactoryController.getInstance().serializeTag(provider, outpostResolverToken);
            compound.put(NBT_OUTPOST_TOKEN, outpostToken);
        }

        if (!requestTracking.isEmpty())
        {
            writeRequestTrackingToNbt(provider, compound);
        }

        return compound;
    }

    /**
     * Deserializes the NBT data for the outpost, restoring its state from the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 outpost.
     */

    @SuppressWarnings("null")
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag compound) 
    {
        super.deserializeNBT(provider, compound);
        BlockPos buildingId = BlockPosUtil.read(compound, TAG_CONNECTED_STATION);
        if (!BlockPos.ZERO.equals(buildingId))
        {
            final IBuilding b = getColony().getBuildingManager().getBuilding(buildingId);
            if (b instanceof BuildingStation station)
            {
                this.connectedStation = station;
            }
            else
            {
                LOGGER.warn("Connected station at {} is not a BuildingStation ({}).", buildingId, b);
                this.connectedStation = null;
            }
        }
        
        if (compound.contains(NBT_OUTPOST_TOKEN)) 
        {
            outpostResolverToken = StandardFactoryController.getInstance().deserializeTag(provider, compound.getCompound(NBT_OUTPOST_TOKEN));
        }

        readRequestTrackingFromNbt(provider, compound);
    }

    /**
     * Writes the request tracking data to the provided CompoundTag.
     *
     * Iterates over the request tracking map and for each entry, serializes the
     * token using StandardFactoryController and stores it, along with the
     * state name of the tracking, in a new CompoundTag. The new CompoundTags are
     * then stored in a ListTag which is added to the provided CompoundTag under
     * the key {@link #TAG_REQUEST_TRACKING}.
     * 
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag to write the request tracking data to.
     */
    public void writeRequestTrackingToNbt(HolderLookup.Provider provider,final CompoundTag compound)
    {
        final ListTag list = new ListTag();

        for (Map.Entry<IToken<?>, OutpostShipmentTracking> e : requestTracking.entrySet())
        {
            if (e.getKey() == null || e.getValue() == null) continue;

            final CompoundTag entry = new CompoundTag();
            // Serialize the token via StandardFactoryController
            final CompoundTag tokenTag = StandardFactoryController.getInstance().serializeTag(provider, e.getKey());
            if (tokenTag != null)
            {
                entry.put(TAG_REQUEST_TOKEN, tokenTag);
                entry.putString(TAG_REQUEST_STATE, e.getValue().getState().name());
                list.add(entry);
            }
        }

        compound.put(TAG_REQUEST_TRACKING, list);
    }


    public void readRequestTrackingFromNbt(final HolderLookup.Provider provider,
        final CompoundTag compound)
    {
        requestTracking.clear();

        if (!compound.contains(TAG_REQUEST_TRACKING, net.minecraft.nbt.Tag.TAG_LIST))
        {
            return;
        }

        final ListTag list = compound.getList(TAG_REQUEST_TRACKING, net.minecraft.nbt.Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);

            // --- Token ---
            if (!entry.contains(TAG_REQUEST_TOKEN, net.minecraft.nbt.Tag.TAG_COMPOUND))
            {
                LOGGER.warn("RequestTracking[{}]: missing or wrong-type token; skipping.", i);
                continue;
            }

            final CompoundTag tokenTag = entry.getCompound(TAG_REQUEST_TOKEN);
            IToken<?> token = null;
            try
            {
                token = (IToken<?>) StandardFactoryController.getInstance().deserializeTag(provider, tokenTag);
            }
            catch (Exception ex)
            {
                LOGGER.warn("RequestTracking[{}]: failed to deserialize token: {}", i, ex.toString());
                continue;
            }
            if (token == null)
            {
                LOGGER.warn("RequestTracking[{}]: deserialized token is null; skipping.", i);
                continue;
            }

            // --- State ---
            OutpostOrderState state = OutpostOrderState.NEEDED; // default/fallback

            if (entry.contains(TAG_REQUEST_STATE, net.minecraft.nbt.Tag.TAG_STRING))
            {
                final String name = entry.getString(TAG_REQUEST_STATE);
                try
                {
                    state = OutpostOrderState.valueOf(name);
                }
                catch (IllegalArgumentException iae)
                {
                    LOGGER.warn("RequestTracking[{}]: unknown state '{}'; defaulting to {}.", i, name, state);
                }
            }
            else
            {
                LOGGER.warn("RequestTracking[{}]: missing state; defaulting to {}.", i, state);
            }

            // --- Tracking object ---
            final OutpostShipmentTracking tracking = new OutpostShipmentTracking(state);

            requestTracking.put(token, tracking);
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

        // At level 0 anyone can build it.
        if (getBuildingLevel() == 0)
        {
            return requestedBuilder;
        }

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

        for (IToken<?> requestId : requestTracking.keySet())
        {
            IRequest<?> request = this.getColony().getRequestManager().getRequestForToken(requestId);
            OutpostShipmentTracking tracking = requestTracking.get(requestId);

            if (tracking == null 
                || request == null
                || tracking.getState() == OutpostOrderState.DELIVERED 
                || request.getState() == RequestState.CANCELLED 
                || request.getState() == RequestState.FAILED
                || request.getState() == RequestState.COMPLETED)
            {
                requestsToRemove.add(request);
            }
        }

        for (IRequest<?> request : requestsToRemove)
        {
            if (request != null)
            {
                requestTracking.remove(request.getId());
            }
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
                TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unable to find connected station - no candidate stations found."));
            }
            else
            {
                final BuildingStation logCopy = candidateStation;
                TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unable to find connected station between {} and {}.", this.getRailStartPosition(), logCopy.getRailStartPosition()));
            }
            connectedStation = null;
        }
        else
        {
            connectedStation = candidateStation;
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Found connected station at {} with rail start position of {}.", connectedStation.getPosition(), connectedStation.getRailStartPosition()));
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
        
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Outpost connection result status: {}", result == null ? "Null" : result.isConnected() ? "Connected" : "Not connected."));

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
        if (!getAllAssignedCitizen().isEmpty())
        {
            ICitizenData exportWorker = getScout();
            exportWorker.getEntity().get().getCitizenExperienceHandler().addExperience(1);
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
        BuildingStationExportModule exports = connectedStation.getModule(MCTPBuildingModules.EXPORTS);

        if (exports == null)
        {
            LOGGER.warn("No export module on connected station - cannot remove order.");
            return;
        }

        exports.removeExport(shipmentReceived);

        if (!getAllAssignedCitizen().isEmpty())
        {
            ICitizenData exportWorker = getScout();
            exportWorker.getEntity().get().getCitizenExperienceHandler().addExperience(1);
        }
    }

    

    /**
     * Retrieves the number of expected shipments for this outpost.
     * This is the number of shipments that are currently in transit from another station to this outpost.
     * @return The number of expected shipments for this outpost.
     */
    public int getRequestTrackingSize()
    {
        return requestTracking.size();
    }

    /**
     * Retrieves the OutpostShipment associated with the given request, or null if no matching shipment is found.
     * @param shipment The request to find the associated OutpostShipment for.
     * @return The OutpostShipment associated with the given request, or null if no matching shipment is found.
     */
    public OutpostShipmentTracking trackingForRequest(IRequest<?> request)
    {        
        OutpostShipmentTracking tracking = requestTracking.get(request.getId());

        if (tracking == null)
        {
            tracking = new OutpostShipmentTracking(OutpostOrderState.NEEDED);    
            requestTracking.put(request.getId(), tracking);
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
    public Set<IRequest<?>> getOutpostRequests()
    {        
        Set<IRequest<?>> outpostRequests = new HashSet<>();

        for (BlockPos child : this.getWorkBuildings())
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


    /**
     * Returns a set of BlockPos that represent the building's work buildings. This includes all child buildings of the outpost, as well as the outpost itself.
     *
     * @return A set of BlockPos that represent the building's work buildings.
     */
    public Set<BlockPos> getWorkBuildings()
    {
        Set<BlockPos> workBuildings = new HashSet<>();

        workBuildings.addAll(getChildren());
        workBuildings.add(getPosition());

        return workBuildings;

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