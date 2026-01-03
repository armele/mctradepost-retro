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
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.utils.BuilderBucket;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;

public class BuildingOutpost extends AbstractBuildingStructureBuilder implements ITradeCapable, IRequestSatisfaction
{
    public enum OutpostOrderState
    {
        NEEDED,                 // The outpost has a need for something
        NEEDS_ITEM_FOR_SHIPPING,// Connected station needs something before it can ship
        ITEM_READY_TO_SHIP,     // Connected station has something to ship
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

    protected int stationValidationCooldown = 0;
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
    
    /**
     * Returns the claim radius for this outpost at the given level.
     * This outpost currently always claims a single block in all directions, 
     * (regardless of level) so the claim radius is always 1.
     * 
     * @param newLevel the new level that the outpost is being upgraded to
     * @return the claim radius for the outpost at the given level
     */
    @Override
    public int getClaimRadius(int newLevel) 
    {
        return 1;
    }


    /**
     * Determines if citizens can be assigned to this outpost.
     * Noteworthy is that this allows level 0 assignment.
     * 
     * @return true if citizens can be assigned to this outpost, false otherwise
     */
    @Override
    public boolean canAssignCitizens()
    {
        return true;
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
    @SuppressWarnings("null")
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider)
    {
        CompoundTag compound = super.serializeNBT(provider);
        BlockPosUtil.write(compound, TAG_CONNECTED_STATION, getConnectedStation() != null ? getConnectedStation().getPosition() : BlockPos.ZERO);

        if (outpostResolverToken != null)
        {
            CompoundTag outpostToken = StandardFactoryController.getInstance().serializeTag(provider, outpostResolverToken);
            if (!outpostToken.isEmpty())
            {
                compound.put(NBT_OUTPOST_TOKEN, outpostToken);
            }
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

    @Override
    public void serializeToView(@NotNull RegistryFriendlyByteBuf buf, boolean fullSync) 
    {
            super.serializeToView(buf, fullSync);
            buf.writeInt(this.getOutpostLevel());
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
                entry.putString(TAG_REQUEST_STATE, e.getValue().getState().name() + "");
                list.add(entry);
            }
        }

        compound.put(TAG_REQUEST_TRACKING, list);
    }


    /**
     * Reads the request tracking data from the provided CompoundTag.
     * 
     * <p>Clears the request tracking map and then iterates over the list of
     * request tracking entries stored in the CompoundTag under the key
     * {@link #TAG_REQUEST_TRACKING}. For each entry, deserializes the token
     * using StandardFactoryController and then stores it, along with the
     * state name of the tracking, in a new CompoundTag. The new CompoundTags are
     * then stored in a ListTag which is added to the provided CompoundTag under
     * the key {@link #TAG_REQUEST_TRACKING}.
     * 
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag to read the request tracking data from.
     */
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

        if (!requestedBuilder.equals(this.getPosition()))
        {
            String buildingDisplayName = this.getBuildingDisplayName() + "";

            if (player != null)
            {
                MessageUtils.format("com.mctradepost.outpost.builder.switched", Component.translatable(buildingDisplayName)).sendTo(player);
            }
            else
            {
                MessageUtils.format("com.mctradepost.outpost.builder.switched", Component.translatable(buildingDisplayName)).sendTo(getColony());
            }
        }

        return this.getPosition();
    }

    /**
     * Requests that the outpost be upgraded to the next level.
     * If the outpost is currently at level 0 and the parent building is at level 1 or higher, the outpost will be built.
     * If the outpost is not at the maximum level and the parent building is at a lower level, the outpost will be upgraded.
     * If the outpost is at the maximum level, a message will be sent to the player indicating that no upgrade is available.
     * If the outpost requires a research effect to be upgraded and the effect is not yet researched, a message will be sent to the player indicating that the research effect is required.
     * If the outpost requires a research effect to be upgraded and the effect has already been researched but the outpost is not yet at the required level, a message will be sent to the player indicating that the outpost requires an upgrade.
     * 
     * NOTE that this uses the Outpost "fake level" workaround.
     * 
     * @param player the player requesting the upgrade
     * @param builder the position of the builder requesting the upgrade
     */
    @Override
    public void requestUpgrade(Player player, BlockPos builder)
    {
        BlockPos useBuilder = outpostBuilderPosition(player, builder);

        final ResourceLocation hutResearch = colony.getResearchManager().getResearchEffectIdFrom(this.getBuildingType().getBuildingBlock());

        if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(hutResearch) &&
              colony.getResearchManager().getResearchEffects().getEffectStrength(hutResearch) < 1)
        {
            MessageUtils.format(TranslationConstants.WARNING_BUILDING_REQUIRES_RESEARCH_UNLOCK).sendTo(player);
            return;
        }
        if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(hutResearch) &&
              (colony.getResearchManager().getResearchEffects().getEffectStrength(hutResearch) <= getOutpostLevel()))
        {
            MessageUtils.format(TranslationConstants.WARNING_BUILDING_REQUIRES_RESEARCH_UPGRADE).sendTo(player);
            return;
        }

        final IBuilding parentBuilding = colony.getBuildingManager().getBuilding(getParent());

        if (getOutpostLevel() == 0 && (parentBuilding == null || parentBuilding.getBuildingLevel() > 0))
        {
            requestWorkOrder(WorkOrderType.BUILD, useBuilder);
        }
        else if (getOutpostLevel() < getMaxBuildingLevel() && (parentBuilding == null || getOutpostLevel() < parentBuilding.getBuildingLevel() || parentBuilding.getBuildingLevel() >= parentBuilding.getMaxBuildingLevel()))
        {
            requestWorkOrder(WorkOrderType.UPGRADE, useBuilder);
        }
        else
        {
            MessageUtils.format(TranslationConstants.WARNING_NO_UPGRADE).sendTo(player);
        }
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
     * Returns the current building level of this outpost.
     * If the building level is 0, returns 1, as the outpost is always considered to be at least level 1
     * for the purposes of other colony logic.
     * 
     * Otherwise, returns the building level as determined by the superclass.
     * @return the current building level of this outpost
     */
    @Override
    public int getBuildingLevel() 
    {
        if (super.getBuildingLevel() == 0)
        {
            return 1;
        }
        
        return super.getBuildingLevel();
    }


    /**
     * Returns the current building level of this outpost,
     * reflecting a "0" level outpost if currently unbuilt.
     * 
     * Note that this differs from getBuildingLevel() only if the outpost is at level 0.
     * Kludgey workaround since we want to do things at level 0 that Minecolonies code base
     * assumes cannot happen until level 1.
     * 
     * @return the current building level of this outpost (without the outpost bump)
     */
    public int getOutpostLevel()
    {
        return super.getBuildingLevel();
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

        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Validating Outpost connected to station in colony {}.", this.getColony().getName()));

        Collection<IBuilding> buildings = colony.getBuildingManager().getBuildings().values();
        BuildingStation candidateStation = null;
        boolean isCurrentlyDisconnected = this.isDisconnected();
        boolean connected = false;

        for (IBuilding building : buildings)
        {
            if (building instanceof BuildingStation station && building.getColony().getID() == this.getColony().getID())
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

        if (shipmentReceived.getRequestToken() != null)
        {
            IRequest<?> request = null;
            
            try 
            {
                IRequestManager requestManager = this.getColony().getRequestManager();
                request = requestManager.getRequestForToken(shipmentReceived.getRequestToken());
                
                requestManager.updateRequestState(request.getId(), RequestState.COMPLETED);
                OutpostShipmentTracking tracking = trackingForRequest(request);
                tracking.setState(OutpostOrderState.RECEIVED);

            }
            catch (Exception e)
            {
                LOGGER.warn("Request for token {} no longer valid.", shipmentReceived.getRequestToken(), e);
            }

        }

        exports.removeExport(shipmentReceived);

        if (!getAllAssignedCitizen().isEmpty())
        {
            ICitizenData exportWorker = getScout();
            exportWorker.getEntity().get().getCitizenExperienceHandler().addExperience(1);
        }

        markDirty();
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

        if (!supers.isEmpty())
        {
            builder.addAll(supers);
        }

        if (outpostResolver != null)
        {
            builder.add(outpostResolver);
        }

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

        IBuilding outpostBuilding = toOutpostBuilding(checkBuilding);

        if (outpostBuilding != null)
        {
            qualifies = true;
        }

        return qualifies;
    }

    /**
     * Checks if the given building is an outpost building, either directly or indirectly through its parent.
     * A building is considered an outpost building if it is an instance of BuildingOutpost, or if its parent is.
     * @param checkBuilding The building to check.
     * @return True if the building is an outpost building, false otherwise.
     */
    public static IBuilding toOutpostBuilding(@Nonnull IBuilding checkBuilding)
    {
        IBuilding returnBuilding = null;

        if (checkBuilding instanceof BuildingOutpost)
        {
            returnBuilding = checkBuilding;
        }
        else
        {
            BlockPos parent = checkBuilding.getParent();

            if (parent != null && checkBuilding.getColony() != null)
            {
                IBuilding parentBuilding = checkBuilding.getColony().getBuildingManager().getBuilding(parent);
                
                if (parentBuilding instanceof BuildingOutpost)
                {
                    returnBuilding = parentBuilding;
                }
            }
        }
        
        return returnBuilding;
    }

    /**
     * Retrieves all open requests from all buildings in this outpost.
     * This will include all requests from this outpost itself, as well as all requests from all its children.
     * @return A list of all open requests in this outpost.
     */
    public Set<IRequest<?>> getOutstandingOutpostRequests(boolean includeReceived)
    {
        final Set<IRequest<?>> outpostRequests = new HashSet<>();

        for (BlockPos child : this.getWorkBuildings())
        {
            final IBuilding outpostBuilding = this.getColony().getBuildingManager().getBuilding(child);
            if (outpostBuilding == null) continue;

            // Citizen-scoped open requests
            final Set<ICitizenData> citizens = outpostBuilding.getAllAssignedCitizen();

            if (citizens != null && !citizens.isEmpty())
            {
                for (ICitizenData citizen : citizens)
                {
                    final Collection<IRequest<?>> openRequests = outpostBuilding.getOpenRequests(citizen.getId());
                    if (openRequests == null || openRequests.isEmpty()) continue;

                    for (IRequest<?> r : openRequests)
                    {
                        final RequestState s = r.getState();
                        if (s != RequestState.CANCELLED && (s != RequestState.RECEIVED || includeReceived))
                        {
                            outpostRequests.add(r);
                        }
                    }
                }
            }

            // Building-scoped open requests (often already filtered by MineColonies)
            final Collection<IRequest<?>> buildingRequests = outpostBuilding.getOpenRequests(-1);
            if (buildingRequests != null && !buildingRequests.isEmpty())
            {
                for (IRequest<?> r : buildingRequests)
                {
                    final RequestState s = r.getState();
                    if (s != RequestState.CANCELLED && (s != RequestState.RESOLVED || includeReceived))
                    {
                        outpostRequests.add(r);
                    }
                }
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

    /**
     * Gets the scout of the outpost, if any. Returns null if no scout is assigned.
     * 
     * @return the scout of the outpost, or null if none is assigned.
     */
    public ICitizenData getScout()
    {
        List<ICitizenData> employees = this.getModule(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.scout.get()).getAssignedCitizen();
        
        if (employees.isEmpty()) {
            return null;
        }
    
        return employees.get(0);
    }

    /**
     * Check if the resources are in the bucket, and 
     * move them to the worker inventory for use. If the resources are not in the bucket, 
     * request them from the stationmaster.
     * 
     * @param requiredResources the resources required for the task
     * @param worker the worker to whom to deliver the resources
     */
    @Override
    public void checkOrRequestBucket(@Nullable final BuilderBucket requiredResources, final ICitizenData worker)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("BuildingOutpost.checkOrRequestBucket in {}.", this.getColony().getName()));
        super.checkOrRequestBucket(requiredResources, worker);
    }

    /**
     * Check if the resources are in the bucket, and 
     * move them to the worker inventory for use.
     *
     * @param stack the stack to check.
     * @return true if so.
     */
    public boolean hasResourceInBucket(final ItemStack stack)
    {
        final int hashCode = stack.getComponentsPatch().hashCode();
        final String key = stack.getDescriptionId() + "-" + hashCode;
        boolean hasit = getRequiredResources() != null && getRequiredResources().getResourceMap().containsKey(key);

        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("BuildingOutpost.hasResourceInBucket in {} looking for {}. Found? {}", this.getColony().getName(), stack.getHoverName(), hasit));

        if (getScout() != null && hasit)
        { 
            ItemStorage toTransfer = new ItemStorage(stack.copy());

            InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(this, toTransfer, getScout().getInventory());
        }

        return hasit;
    }


}