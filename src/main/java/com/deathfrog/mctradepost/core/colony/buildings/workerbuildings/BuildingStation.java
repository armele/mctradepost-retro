package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.Queue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationImportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;
import com.deathfrog.mctradepost.core.colony.requestsystem.IRequestSatisfaction;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.TrainDeliveryResolver;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.ITradeCapable;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.deathfrog.mctradepost.item.CoinItem;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.eventhooks.citizenEvents.VisitorSpawnedEvent;
import com.minecolonies.core.datalistener.CustomVisitorListener;
import com.minecolonies.core.datalistener.RecruitmentItemsListener;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import static com.minecolonies.api.util.constant.Constants.MAX_STORY;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STATION;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST_REQUESTS;

public class BuildingStation extends AbstractBuilding implements ITradeCapable, IRequestSatisfaction
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static @Nonnull String EXPORTS_SHIPPED = "exports_shipped";
    public static @Nonnull String IMPORTS_RECEIVED = "imports_received";
    public static @Nonnull String NBT_TDR_TOKEN = "tdr_token";

    protected TrainDeliveryResolver trainDeliveryResolver;
    protected IToken<?> trainDeliveryResolverToken = null;

    /**
     * List of additional citizens (visitors)
     */
    private final List<Integer> externalCitizens = new ArrayList<>();

    /**
     * Serialization tag for the stations.
     */
    private final String TAG_STATIONS = "stations";

    /**
     * Structurize position tag for the start of the rail network in this station.
     */
    public static final String STATION_START = "station_start";

    /**
     * Map of station data for other stations (by station block position).
     */
    private Map<BlockPos, StationData> stations = new ConcurrentHashMap<>();

    /**
     * Map of track connection results for other trade capable buildings (by station data).
     */
    private Map<StationData, TrackConnectionResult> connectionresults = new HashMap<>();

    /**
     * Queue of payment requests to other stations
     */
    private Queue<Integer> paymentRequests = new ArrayDeque<>();

    /**
     * Delay for spawning more visitors when a spot opens up.
     */
    private int noVisitorTime = 10000;

    public final static String EXPORT_ITEMS = "com.deathfrog.mctradepost.gui.workerhuts.station.exports";
    public final static String FUNDING_ITEMS = "com.deathfrog.mctradepost.gui.workerhuts.station.funding";

    public BuildingStation(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    /**
     * Retrieves the schematic name for this building.
     *
     * @return The schematic name as defined in ModBuildings.
     */

    @Override
    public String getSchematicName()
    {
        return ModBuildings.STATION_ID;
    }

    /**
     * Retrieves the map of stations for this building. The map is keyed by BlockPos, which is the position of the station in the
     * building. The values are the StationData objects associated with the station at that position.
     * 
     * @return a reference to the map of stations.
     */
    public Map<BlockPos, StationData> getStations()
    {
        return stations;
    }

    /**
     * Clears the list of connected stations for this building. This should be called when the building is being destroyed, or when all
     * stations are being cleared from the building for some other reason.
     */
    public void clearConnectedStations()
    {
        stations.clear();
    }

    /**
     * Clears the list of exports for this station. This will remove all export data from the station, and mark the module as dirty so
     * that it will be saved to disk. This is useful when the user changes the trade settings, so that the client can receive the
     * updated trade data.
     */
    public void clearExports()
    {
        BuildingStationExportModule exports = this.getModule(MCTPBuildingModules.EXPORTS);

        if (exports != null)
        {
            exports.clearExports();
        }
    }

    /**
     * Adds a station to the list of stations for this building. The station is mapped to its position in the building's station map.
     *
     * @param station The StationData to be added.
     */
    public void addStation(StationData sdata)
    {
        if (sdata == null || sdata.getBuildingPosition() == null || sdata.getBuildingPosition().equals(BlockPos.ZERO))
        {
            LOGGER.warn("Attempted to add null station to building.");
            return;
        }
        stations.put(sdata.getBuildingPosition(), sdata);
        // LOGGER.info("Adding station to building: {}. There are now {} stations with data recorded.", sdata, stations.size());
    }

    /**
     * Checks if the given station is contained in the list of stations for this building.
     *
     * @param station The StationData to check.
     * @return true if the station is in the list, false if not.
     */
    public boolean hasStation(StationData station)
    {
        if (station == null)
        {
            return false;
        }

        return stations.containsKey(station.getBuildingPosition());
    }

    /**
     * Checks if there is a station at the given BlockPos in the list of stations for this building.
     *
     * @param pos The BlockPos to check.
     * @return true if a station is at the given position, false if not.
     */
    public boolean hasStationAt(@Nonnull BlockPos pos)
    {
        return stations.containsKey(pos);
    }

    /**
     * Retrieves the station data at the specified block position.
     *
     * @param pos The block position to retrieve the station data from.
     * @return The StationData object at the given position, or null if no station exists at that position.
     */
    public StationData getStationAt(@Nonnull BlockPos pos)
    {
        return stations.get(pos);
    }

    /**
     * Marks the trades as dirty, so that they will be re-sent to the client on the next update. This cascades to both the import and
     * export modules. This is useful when the user changes the trade settings, so that the client can receive the updated trade data.
     */
    public void markTradesDirty()
    {
        markDirty();
        BuildingStationImportModule imports = getModule(MCTPBuildingModules.IMPORTS);

        if (imports != null)
        {
            imports.markDirty();
        }

        BuildingStationExportModule exports = getModule(MCTPBuildingModules.EXPORTS);

        if (exports != null)
        {
            exports.markDirty();
        }
    }

    /**
     * Validates the stations that are connected to this station. Goes through all the stations in the list and checks if they are
     * still valid. Checks for the following conditions: - The server is not null. - The level is not null. - The colony is not null. -
     * The building is not null and is an instance of BuildingStation. If any of these conditions are not met, the station is removed
     * from the list.
     */
    protected void validateStations()
    {
        for (StationData remoteStation : this.stations.values())
        {
            MinecraftServer server = getColony().getWorld().getServer();
            ResourceKey<Level> dimension = remoteStation.getDimension();

            if (server == null || dimension == null)
            {
                stations.remove(remoteStation.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no server): {} - removing it.", remoteStation);
                markTradesDirty();
                continue;
            }

            Level level = server.getLevel(dimension);

            if (level == null)
            {
                stations.remove(remoteStation.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no level): {} - removing it.", remoteStation);
                markTradesDirty();
                continue;
            }

            IColony stationColony = IColonyManager.getInstance().getIColony(level, remoteStation.getBuildingPosition());

            if (stationColony == null)
            {
                stations.remove(remoteStation.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no colony): {} - removing it.", remoteStation);
                markTradesDirty();
                continue;
            }

            IBuilding building = stationColony.getBuildingManager().getBuilding(remoteStation.getBuildingPosition());

            if (building == null || !(building instanceof ITradeCapable))
            {
                stations.remove(remoteStation.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no building): {} - removing it.", remoteStation);
                markTradesDirty();
                continue;
            }

            // Restore track connection information if not present (for example, after restoring from a save)
            if (getTrackConnectionResult(remoteStation) == null)
            {
                TrackConnectionResult trackConnectionResult =
                    TrackPathConnection.arePointsConnectedByTracks((ServerLevel) this.getColony().getWorld(),
                        this.getRailStartPosition(),
                        remoteStation.getRailStartPosition(),
                        false);
                putTrackConnectionResult(remoteStation, trackConnectionResult);
                markTradesDirty();
            }
        }
    }

    /**
     * Handles the spawning of tourists in the station, based on the current research level for tourists.
     * <p>If the station is open for business and the number of external citizens is less than the current tourism level multiplied by
     * the building level, a visitor is spawned and the recruitment interaction is triggered with the first name of the visitor.
     * <p>The time until the next visitor is determined by the current number of citizens in the colony, the maximum number of citizens
     * in the colony and the current building level.
     */
    private void handleTourists()
    {
        double tourismLevel =
            this.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.TOURISTS);

        if ((this.getBuildingLevel() > 0) && (externalCitizens.size() < tourismLevel * this.getBuildingLevel()) &&
            isOpenForBusiness() &&
            noVisitorTime <= 0)
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info("Checking for station visitors, with a tourism level of {}.", tourismLevel));

            final IVisitorData visitorData = spawnVisitor();

            if (noVisitorTime > 0)
            {
                noVisitorTime -= 500;
            }

            if (visitorData != null && !CustomVisitorListener.chanceCustomVisitors(visitorData))
            {
                visitorData
                    .triggerInteraction(new RecruitmentInteraction(
                        Component.translatable("com.minecolonies.coremod.gui.chat.recruitstory" +
                            (this.getColony().getWorld().random.nextInt(MAX_STORY) + 1), visitorData.getName().split(" ")[0]),
                        ChatPriority.IMPORTANT));
            }

            noVisitorTime = colony.getWorld().getRandom().nextInt(3000) +
                (6000 / this.getBuildingLevel()) * colony.getCitizenManager().getCurrentCitizenCount() /
                    colony.getCitizenManager().getMaxCitizens();
        }
    }

    /**
     * Called every tick that the colony updates.
     * 
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        handleTourists();
        validateStations();

        for (IBuildingModule module : this.modules)
        {
            if (module instanceof ITickingModule)
            {
                ((ITickingModule) module).onColonyTick(colony);
            }
        }
    }

    /**
     * Spawns a visitor citizen that can be recruited.
     */
    @Nullable
    public IVisitorData spawnVisitor()
    {
        final int recruitLevel = this.getColony().getWorld().random.nextInt(10 * this.getBuildingLevel()) + 15;
        final RecruitmentItemsListener.RecruitCost cost = RecruitmentItemsListener.getRandomRecruitCost(recruitLevel);
        if (cost == null)
        {
            return null;
        }

        final IVisitorData newCitizen = (IVisitorData) this.getColony().getVisitorManager().createAndRegisterCivilianData();
        newCitizen.setBedPos(this.getPosition());
        newCitizen.setHomeBuilding(this);
        newCitizen.getCitizenSkillHandler().init(recruitLevel);
        newCitizen.setRecruitCosts(cost.boots());

        BlockPos spawnPos = BlockPosUtil.findSpawnPosAround(this.getColony().getWorld(), this.getPosition());
        if (spawnPos == null)
        {
            spawnPos = this.getPosition();
        }

        this.getColony().getVisitorManager().spawnOrCreateCivilian(newCitizen, this.getColony().getWorld(), spawnPos, true);
        if (newCitizen.getEntity().isPresent())
        {
            newCitizen.getEntity().get().setItemSlot(EquipmentSlot.HEAD, getHats(recruitLevel));
        }
        this.getColony().getEventDescriptionManager().addEventDescription(new VisitorSpawnedEvent(spawnPos, newCitizen.getName()));

        externalCitizens.add(newCitizen.getId());
        return newCitizen;
    }

    /**
     * Get the hat for the given recruit level.
     *
     * @param recruitLevel the input recruit level.
     * @return the itemstack for the boots.
     */
    private ItemStack getHats(final int recruitLevel)
    {
        ItemStack hat = ItemStack.EMPTY;
        if (recruitLevel > 1)
        {
            // Leather
            hat = new ItemStack(NullnessBridge.assumeNonnull(Items.LEATHER_HELMET));
        }
        if (recruitLevel > 2)
        {
            // Gold
            hat = new ItemStack(NullnessBridge.assumeNonnull(Items.GOLDEN_HELMET));
        }
        if (recruitLevel > 3)
        {
            // Iron
            hat = new ItemStack(NullnessBridge.assumeNonnull(Items.IRON_HELMET));
        }
        if (recruitLevel > 4)
        {
            // Diamond
            hat = new ItemStack(NullnessBridge.assumeNonnull(Items.DIAMOND_HELMET));
        }
        return hat;
    }

    /**
     * Gets the stationmaster (worker) assigned to this building.
     *
     * @return the stationmaster, or null if none is found.
     */
    public ICitizenData getStationmaster()
    {
        List<ICitizenData> employees =
            this.getModule(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.stationmaster.get())
                .getAssignedCitizen();

        if (employees.isEmpty())
        {
            return null;
        }

        return employees.get(0);
    }

    /**
     * Returns true if the station is open for business, i.e. if it has a stationmaster assigned and they are working.
     * 
     * @return true if the station is open for business, false otherwise.
     */
    public boolean isOpenForBusiness()
    {
        ICitizenData stationmaster = getStationmaster();

        if (stationmaster == null)
        {
            return false;
        }

        final Optional<AbstractEntityCitizen> optionalEntityCitizen = stationmaster.getEntity();

        if (!optionalEntityCitizen.isPresent())
        {
            return false;
        }

        AbstractEntityCitizen stationmasterEntity = optionalEntityCitizen.get();

        IState workState = ((EntityCitizen) stationmasterEntity).getCitizenAI().getState();

        return CitizenAIState.WORKING.equals(workState);
    }

    /**
     * Serializes the state of the building to NBT, including the list of stations that this building is connected to.
     * The list of stations is stored in a ListTag with the key {@link #TAG_STATIONS}. Each station is represented by a CompoundTag
     * containing the state of the station, as returned by {@link StationData#toNBT(HolderLookup.Provider)}.
     * Additionally, the token used to identify the outpost resolver used by the train delivery AI is stored in a CompoundTag with the key
     * {@link #NBT_TDR_TOKEN}.
     * 
     * @param provider The holder lookup provider for item and block references.
     * @return The compound tag containing the serialized state of the building.
     */    
    @Override
    public CompoundTag serializeNBT(@SuppressWarnings("null") final HolderLookup.Provider provider)
    {
        CompoundTag compound = super.serializeNBT(provider);

        final ListTag stationTagList = new ListTag();

        for (Map.Entry<BlockPos, StationData> entry : stations.entrySet())
        {
            if (entry.getValue() != null && !BlockPos.ZERO.equals(entry.getValue().getBuildingPosition()) &&
                !BlockPos.ZERO.equals(entry.getValue().getRailStartPosition()))
            {
                stationTagList.add(entry.getValue().toNBT());
            }
        }

        compound.put(TAG_STATIONS, stationTagList);

        if (trainDeliveryResolverToken != null)
        {
            CompoundTag outpostToken = StandardFactoryController.getInstance().serializeTag(provider, trainDeliveryResolverToken);

            if (outpostToken != null)
            {
                compound.put(NullnessBridge.assumeNonnull(NBT_TDR_TOKEN), outpostToken);
            }
        }

        return compound;
    }

    /**
     * Deserializes the state of the building from NBT, including the list of stations.
     * 
     * @param provider The holder lookup provider for item and block references.
     * @param compound The compound tag containing the serialized state of the building.
     */
    @SuppressWarnings("null")
    @Override
    public void deserializeNBT(Provider provider, CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        ListTag stationTagList = compound.getList(TAG_STATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < stationTagList.size(); ++i)
        {
            CompoundTag stationTag = stationTagList.getCompound(i);
            StationData contents = StationData.fromNBT(stationTag);
            if (contents != null)
            {
                // MCTradePostMod.LOGGER.warn("Deserialized station data {} from tag: {}", contents, stationTag);
                addStation(contents);
            }
            else
            {
                MCTradePostMod.LOGGER.warn("Failed to deserialize station data from tag: {}", stationTag);
            }
        }

        if (compound.contains(NBT_TDR_TOKEN))
        {
            trainDeliveryResolverToken =
                StandardFactoryController.getInstance().deserializeTag(provider, compound.getCompound(NBT_TDR_TOKEN));
        }
    }

    /**
     * Serializes the current state of the building, including the list of stations, to the given buffer. The state of the stations is
     * stored under the key TAG_STATIONS in the serialized CompoundTag.
     *
     * @param buf      The buffer to serialize the state of the building into.
     * @param fullSync Whether or not to serialize the full state of the building, or just the delta.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {
        super.serializeToView(buf, fullSync);
        buf.writeInt(stations.size());
        for (final Entry<BlockPos, StationData> station : stations.entrySet())
        {
            buf.writeNbt(station.getValue().toNBT());
        }

        buf.writeInt(connectionresults.size());
        for (final Map.Entry<StationData, TrackConnectionResult> e : connectionresults.entrySet())
        {
            buf.writeNbt(e.getKey().toNBT());       // key
            buf.writeBoolean(e.getValue().connected);     // value
        }
    }

    /**
     * Records the track connection result for a given station.
     *
     * @param stationData The station data for which the track connection result is being recorded.
     * @param result      The result of the track connection, which includes whether the connection is successful, the closest point on
     *                    the track, and the path of the track connection.
     */
    public void putTrackConnectionResult(StationData stationData, TrackConnectionResult result)
    {
        connectionresults.put(stationData, result);
    }

    /**
     * Retrieves the track connection result for a given station data object.
     *
     * @param stationData The station data for which the track connection result is being retrieved.
     * @return The track connection result, which includes whether the connection is successful, the closest point on the track, and
     *         the path of the track connection.
     */
    public TrackConnectionResult getTrackConnectionResult(StationData stationData)
    {
        return connectionresults.get(stationData);
    }

    /**
     * Retrieves the BlockPos of the starting point of the rail network for this train station. If there is no starting point
     * specified, the position of the building itself is returned. If there are multiple starting points specified in the building's
     * NBT, the first one is used. A warning will be logged if there are multiple starting points found.
     * 
     * @return The BlockPos of the starting point of the rail network for this train station.
     */
    public BlockPos getRailStartPosition()
    {
        List<BlockPos> locations = getLocationsFromTag(STATION_START);
        if (locations.isEmpty())
        {
            return this.getPosition();
        }
        else if (locations.size() > 1)
        {
            MCTradePostMod.LOGGER.warn("More than one station start location found, using the first one.");
        }
        return locations.get(0);
    }

    /**
     * Adds a payment request to the queue. The request will be processed on the next call to {@link #removePaymentRequest()}.
     * 
     * @param amount the amount of the payment request.
     */
    public void addPaymentRequest(Integer amount)
    {
        paymentRequests.add(amount);
    }

    /**
     * Retrieves and removes the next payment request from the queue.
     * 
     * @return the amount of the next payment request, or null if there are no more requests.
     */
    public Integer removePaymentRequest()
    {
        return paymentRequests.poll();
    }

    /**
     * Completes a shipment of goods from this station to another station specified by the export data. This method will add the goods
     * to the remote station's inventory or drop them on the ground if the inventory is full. It will also add the payment to the local
     * station's inventory or drop it on the ground if the inventory is full. Additionally, it will give experience to the worker at
     * the remote station.
     * 
     * @param exportData the export data containing the station to ship to, the item to ship, and the cost of shipping.
     */
    public void completeExport(ExportData exportData)
    {
        TraceUtils.dynamicTrace(TRACE_STATION,
            () -> LOGGER.info("Shipment completed of {} for {} at {}",
                exportData.getTradeItem().getItem(),
                exportData.getCost(),
                exportData.getDestinationStationData().getStation().getPosition()));

        @Nonnull ItemStack finalPayment = NullnessBridge.assumeNonnull(ItemStack.EMPTY);

        if (exportData.getCost() > 0)
        {
            CoinItem coinItem = MCTradePostMod.MCTP_COIN_ITEM.get();
            finalPayment = new ItemStack(NullnessBridge.assumeNonnull(coinItem), exportData.getCost());
        }

        int shipmentCountdown = exportData.getShipmentCountdown();
        if (shipmentCountdown > 0)
        {
            shipmentCountdown -= 1;
            exportData.setShipmentCountdown(shipmentCountdown);
        }

        if (!exportData.isReverse())
        {
            StatsUtil.trackStatByName(this,
                EXPORTS_SHIPPED,
                exportData.getTradeItem().getItemStack().getHoverName(),
                exportData.getQuantity());
            StatsUtil.trackStatByName(exportData.getDestinationStationData().getStation(),
                IMPORTS_RECEIVED,
                exportData.getTradeItem().getItemStack().getHoverName(),
                exportData.getQuantity());
        }
        else
        {
            StatsUtil.trackStatByName(exportData.getSourceStation(),
                EXPORTS_SHIPPED,
                exportData.getTradeItem().getItemStack().getHoverName(),
                exportData.getQuantity());
            StatsUtil.trackStatByName(this,
                IMPORTS_RECEIVED,
                exportData.getTradeItem().getItemStack().getHoverName(),
                exportData.getQuantity());
        }

        ITradeCapable remoteStation = exportData.getDestinationStationData().getStation();

        if (remoteStation == null)
        {
            // If the remote station has been destroyed, refund the shipped items to the shipping station.
            // No funds will be recived (but the destination station is not refunded).
            MCTPInventoryUtils.insertOrDropByQuantity(this, exportData.getTradeItem());
            exportData.setShipDistance(-1);
            GhostCartEntity cart = exportData.getCart();
            if (cart != null)
            {
                cart.discard();
                exportData.setCart(null);
            }
            return;
        }

        MCTPInventoryUtils.insertOrDropByQuantity(remoteStation, exportData.getTradeItem());

        // Adds to the local building inventory and calls for a pickup to the warehouse or drops on the ground if inventory is full.
        if (InventoryUtils.addItemStackToItemHandler(this.getItemHandlerCap(), finalPayment))
        {
            remoteStation.onShipmentReceived(exportData);
        }
        else
        {
            ServerLevel level = (ServerLevel) this.getColony().getWorld();
            BlockPos buildingPos = this.getPosition();

            if (level != null && buildingPos != null)
            {
                MCTPInventoryUtils.dropItemsInWorld(level, buildingPos, finalPayment);
            }
        }

        this.onShipmentDelivered(exportData);

        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Resetting export."));

        exportData.setShipDistance(-1);
        GhostCartEntity cart = exportData.getCart();
        if (cart != null)
        {
            cart.discard();
            exportData.setCart(null);
        }
    }

    /**
     * Called when a shipment is delivered to this station. If the shipment has an associated request token, the request is resolved.
     * Additionally, all assigned citizens at this station receive 1 experience point.
     * 
     * @param shipmentSent the export data containing the shipment that was delivered.
     */
    @Override
    public void onShipmentDelivered(ExportData shipmentSent)
    {
        if (shipmentSent.getRequestToken() != null)
        {
            IRequest<?> associatedRequest = null;

            try
            {
                IRequestManager requestManager = this.getColony().getRequestManager();
                associatedRequest = requestManager.getRequestForToken(shipmentSent.getRequestToken());

                if (associatedRequest != null)
                {
                    resolveRequest(associatedRequest, true);
                }
            }
            catch (Exception e)
            {
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                    () -> LOGGER.info("Request for token {} no longer valid (possibly cancelled while shipping).",
                        shipmentSent.getRequestToken(),
                        e));
            }
        }

        for (ICitizenData citizen : getAllAssignedCitizen())
        {
            if (!citizen.getEntity().isEmpty())
            {
                citizen.getEntity().get().getCitizenExperienceHandler().addExperience(1);
            }
        }
    }

    /**
     * Called when a shipment has been received at this building. This method is used to call for a pickup for the received items. If
     * the building is an outpost, it will call for a pickup for the full amount of the shipment. If the building is not an outpost, it
     * will call for a pickup for each stack of the shipment, up to the max stack size of the item.
     * 
     * @param itemShipped the shipment that was received
     */
    @Override
    public void onShipmentReceived(ExportData shipmentReceived)
    {
        boolean calledForPickup = false;
        int pickupAmount = shipmentReceived.getQuantity();
        while (pickupAmount > 0)
        {
            int thisPickup = 0;
            if (pickupAmount > shipmentReceived.getTradeItem().getItemStack().getMaxStackSize())
            {
                thisPickup = shipmentReceived.getTradeItem().getItemStack().getMaxStackSize();
            }
            else
            {
                thisPickup = pickupAmount;
            }
            pickupAmount -= thisPickup;

            Item item = shipmentReceived.getTradeItem().getItemStack().getItem();
            
            IToken<?> token = item == null ? null : BuildingUtil.bringThisToTheWarehouse(this, new ItemStack(item, thisPickup));

            if (token != null)
            {
                calledForPickup = true;
            }
        }

        if (calledForPickup)
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info("Calling for pickup for {} at {}", this.getBuildingDisplayName(), this.getPosition()));
        }

        if (!this.getAllAssignedCitizen().isEmpty())
        {
            ICitizenData worker = this.getStationmaster();

            if (worker != null && !worker.getEntity().isEmpty())
            {
                worker.getEntity().get().getCitizenExperienceHandler().addExperience(shipmentReceived.getCost());
            }
        }
    }

    /**
     * Returns a list of connected outpost buildings. A connected outpost is defined as one which is connected to this station by a
     * track, and is part of the same colony as this station.
     * 
     * @return a list of connected outpost buildings
     */
    public List<BuildingOutpost> findConnectedOutposts()
    {
        List<BuildingOutpost> outposts = new ArrayList<>();
        for (StationData stationData : connectionresults.keySet())
        {
            TrackConnectionResult connectionResult = connectionresults.get(stationData);

            if (connectionResult.isConnected() && stationData.getStation() instanceof BuildingOutpost outpost &&
                this.getColony().getID() == outpost.getColony().getID())
            {
                outposts.add(outpost);
            }
        }
        return outposts;
    }

    /**
     * Analyzes all requests from connected outposts and attempts to resolve them. If a request is in the CREATED or ASSIGNED state, it
     * checks if the outpost has a qualifying item to ship. If so, it initiates a shipment and sets the request state to RESOLVED. If
     * no qualifying item is found, it echoes the request to the stationmaster and sets the request state to ASSIGNED. If the request
     * is already in progress, it does nothing. This method is called as needed by the worker's AI.
     */
    public void handleOutpostRequests()
    {
        final WarehouseRequestQueueModule module = this.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
        if (module == null)
        {
            LOGGER.error("No request queue module found on station: {}", this.getBuildingDisplayName());
            return;
        }

        ICitizenData stationmaster = getStationmaster();

        if (stationmaster == null)
        {
            // Cannot do work (no station master)
            return;
        }

        /**
         * List of requests that have been removed from the queue.
         */
        final List<IToken<?>> handledRequestList = new ArrayList<>();

        final IStandardRequestManager requestManager = (IStandardRequestManager) this.getColony().getRequestManager();

        final List<IToken<?>> openTaskList = module.getMutableRequestList();
        final List<IToken<?>> reqsToRemove = new ArrayList<>();

        TraceUtils.dynamicTrace(TRACE_STATION,
            () -> LOGGER.info("Analyzing {} tasks for station: {} in colony {}.",
                openTaskList.size(),
                this.getBuildingDisplayName(),
                this.getColony().getID()));

        int car = 1;

        // Cycle through open task list, see if we have something that can
        // be shipped, and ship it if so. Remove it from the task list once shipped.
        for (final IToken<?> requestToken : openTaskList)
        {
            final IRequest<?> request = requestManager.getRequestForToken(requestToken);

            if (request == null || !(request.getRequest() instanceof Delivery delivery))
            {
                reqsToRemove.add(requestToken);
                continue;
            }

            try
            {
                final IRequestResolver<?> currentlyAssignedResolver = requestManager.getResolverForRequest(request.getId());

                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                    () -> LOGGER.info("Request {} is currently assigned to resolver: {}",
                        request.getShortDisplayString(),
                        currentlyAssignedResolver));
            }
            catch (IllegalArgumentException e)
            {
                // Repair request if it is not registered with the request manager.
                // requestManager.getRequestHandler().registerRequest(request);
                // requestManager.assignRequest(request.getId());
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                    () -> LOGGER.info("Request {} is not registered with the request manager: ", request.getShortDisplayString(), e));
                continue;
            }

            IBuilding destinationBuilding =
                this.getColony().getBuildingManager().getBuilding(delivery.getTarget().getInDimensionLocation());

            if (destinationBuilding == null || !(destinationBuilding instanceof BuildingOutpost outpost))
            {
                // Our destination outpost is no longer valid.
                reqsToRemove.add(requestToken);
                continue;
            }

            OutpostShipmentTracking tracking = outpost.trackingForRequest(request);
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                () -> LOGGER.info("Analyzing request in state {} with Outpost tracking {} - details: {}",
                    request.getState(),
                    tracking,
                    request.getShortDisplayString()));

            if ((tracking.getState() == OutpostOrderState.NEEDED || tracking.getState() == OutpostOrderState.NEEDS_ITEM_FOR_SHIPPING ||
                tracking.getState() == OutpostOrderState.ITEM_READY_TO_SHIP) && !handledRequestList.contains(request.getId()) &&
                !reqsToRemove.contains(request.getId()))
            {
                boolean satisfied = trySatisfyRequest(request, outpost, car++);

                if (satisfied)
                {
                    tracking.setState(OutpostOrderState.SHIPMENT_INITIATED);
                    reqsToRemove.add(requestToken);
                    continue;
                }
            }
        }

        module.getMutableRequestList().removeAll(reqsToRemove);
        module.markDirty();
        handledRequestList.addAll(reqsToRemove);
    }

    /**
     * Handles the completion of a request in the task queue. If the request was successful, marks it as resolved; otherwise, marks it
     * as failed. If the request was a delivery, attempts to resolve any dependent requests (i.e. other deliveries to the same outpost)
     * and updates the state of those requests accordingly. Adapted from JobDeliveryman
     * 
     * @param request    the request to finish
     * @param successful whether the request was successful or not
     */
    public void resolveRequest(IRequest<?> request, final boolean successful)
    {
        if (request == null)
        {
            return;
        }

        if (request.getRequest() instanceof Delivery)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                () -> LOGGER.info("Outstanding Request {} - {} (state {}) about to be marked as resolved.",
                    request.getId(),
                    request.getShortDisplayString(),
                    request.getState()));

            if (request.getState() == RequestState.IN_PROGRESS)
            {
                getColony().getRequestManager()
                    .updateRequestState(request.getId(), successful ? RequestState.RESOLVED : RequestState.FAILED);

                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                    () -> LOGGER.info("Outstanding Request {} - {} (state {}) post-update.",
                        request.getId(),
                        request.getShortDisplayString(),
                        request.getState()));

                if (request.getParent() != null)
                {
                    IRequest<?> parent = getColony().getRequestManager().getRequestForToken(request.getParent());
                    TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                        () -> LOGGER.info("Parent Request {} - {} (state {}) post-update.",
                            parent.getId(),
                            parent.getShortDisplayString(),
                            parent.getState()));
                }
            }
        }
    }

    /**
     * Adds a delivery request to the station's work queue if it is not already present, and if the request is a delivery from this
     * station to the given outpost.
     * 
     * @param request The request to check for adding to the work queue.
     * @param outpost The outpost to check for delivery requests to.
     * @return True if the request was added to the work queue, false otherwise.
     */
    protected boolean addTaskForDeliveryIfMissing(@Nonnull IRequest<?> request, BuildingOutpost outpost)
    {
        boolean addedDelivery = false;
        final WarehouseRequestQueueModule module = this.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
        if (module == null)
        {
            LOGGER.error("No request queue module found on station: {}", this.getBuildingDisplayName());
            return false;
        }

        if (request.getRequest() instanceof Delivery delivery)
        {
            BlockPos deliveryTargetPos = delivery.getTarget().getInDimensionLocation();
            IBuilding deliveryTargetBuilding = getColony().getBuildingManager().getBuilding(deliveryTargetPos);

            if (deliveryTargetBuilding == null)
            {
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                    () -> LOGGER.info("Delivery Request {} - {} from {} to {} is missing a target building at that location.",
                        request.getId(),
                        request.getShortDisplayString(),
                        delivery.getStart().getInDimensionLocation().toShortString(),
                        delivery.getTarget().getInDimensionLocation().toShortString()));
                return false;
            }

            IBuilding outpostDeliveryTarget = BuildingOutpost.toOutpostBuilding(deliveryTargetBuilding);

            // If this is a delivery to the outpost in question from this station, add it to our work queue.
            if (outpostDeliveryTarget != null && outpostDeliveryTarget.equals(outpost) &&
                delivery.getStart().getInDimensionLocation().equals(this.getPosition()) &&
                !module.getMutableRequestList().contains(request.getId()))
            {
                module.addRequest(request.getId());
                addedDelivery = true;
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                    () -> LOGGER.info("Delivery Request {} (state {}) - {} added to station queue.",
                        request.getId(),
                        request.getState(),
                        request.getShortDisplayString()));
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                    () -> LOGGER.info("Delivery Request {} - {} from {} to {} is not a station {} to outpost {} request.",
                        request.getId(),
                        request.getShortDisplayString(),
                        delivery.getStart().getInDimensionLocation().toShortString(),
                        delivery.getTarget().getInDimensionLocation().toShortString(),
                        this.getPosition().toShortString(),
                        outpost.getPosition().toShortString()));
            }
        }
        else
        {
            for (IToken<?> childRequestToken : request.getChildren())
            {
                IRequest<?> childRequest = this.getColony().getRequestManager().getRequestForToken(childRequestToken);

                if (childRequest == null) continue;

                addedDelivery = addedDelivery ||
                    addTaskForDeliveryIfMissing(childRequest, outpost) ||
                    addedDelivery;
            }
        }

        return addedDelivery;
    }

    /**
     * Check if we can satisfy the given request from an outpost. If so, initiate a shipment and mark the request as resolved. If not,
     * check if we need an item to be delivered in order to satisfy the request. If so, request that item and mark the request as
     * needing an item for shipping.
     * 
     * @param request The request to check for satisfaction.
     * @param outpost The outpost to check for satisfaction.
     * @param car     The car to use for the shipment.
     * @return A list of request tokens to remove from the outpost's request list.
     */
    protected boolean trySatisfyRequest(@Nonnull IRequest<?> request, @Nonnull BuildingOutpost outpost, int car)
    {
        boolean satisfied = false;

        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
            () -> LOGGER.info("Checking if satisfiable: {} (state {}) - {}",
                request.getId(),
                request.getState(),
                request.getShortDisplayString()));
        // Check if the building has a qualifying item and ship it if so. Determine state change of request status.
        ItemStorage satisfier = inventorySatisfiesRequest(request, true);

        if (satisfier != null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Station has something to ship: {}", satisfier));
            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Station has something to ship: {}", satisfier));
            initiateShipment(satisfier, request, outpost, car);
            satisfied = true;
        }

        return satisfied;
    }

    /**
     * Initiates a shipment of goods from this station to an outpost. The cost of shipping is hardcoded to 0 (no charge to the outpost
     * for shipping). The shipment data is added to the outpost's expected shipments.
     * 
     * @param thingsToDeliver    the items to ship
     * @param associatedRequest  the request associated with the shipment
     * @param outpostDestination the outpost to ship to
     * @param car                When called multiple times in short succession use different car numbers to space them out on the
     *                           track.
     */
    public void initiateShipment(ItemStorage thingsToDeliver,
        IRequest<?> associatedRequest,
        BuildingOutpost outpostDestination,
        int car)
    {
        BuildingStationExportModule exports = this.getModule(MCTPBuildingModules.EXPORTS);

        if (exports == null)
        {
            LOGGER.error("No export module on connected station - cannot place order.");
            return;
        }

        if (thingsToDeliver == null || thingsToDeliver.isEmpty())
        {
            LOGGER.error("Shipments should not be initiated with nothing to deliver. Associated request: {} (State {}) - {}",
                associatedRequest.getId(),
                associatedRequest.getState(),
                associatedRequest.getShortDisplayString());
            return;
        }

        int cost = 0;

        StationData destinationStation = new StationData(outpostDestination);
        ExportData export = exports.addExport(destinationStation, thingsToDeliver, cost);
        export.setRequestToken(associatedRequest.getId());
        export.setShipmentCountdown(car);

        OutpostShipmentTracking shipment = outpostDestination.trackingForRequest(associatedRequest);
        shipment.setState(OutpostOrderState.SHIPMENT_INITIATED);
        exports.markDirty();
        this.markDirty();

        TraceUtils.dynamicTrace(TRACE_STATION,
            () -> LOGGER.info("Set up export order for {} to {} for request {} - {}.",
                thingsToDeliver,
                outpostDestination.getBuildingDisplayName(),
                associatedRequest == null ? "null" : associatedRequest.getId(),
                associatedRequest == null ? "null" : associatedRequest.getShortDisplayString()));
    }

    /**
     * Initiates a return shipment of goods to this station. It is the caller's responsibility to eliminate the items from the source
     * inventory. The cost of shipping is hardcoded to 0 (no charge to the outpost for shipping).
     * 
     * @param thingsToDeliver the items to ship
     * @param carNumber       the carNumber is used to space out cars when many are being sent in a short time frame.
     */
    public void initiateReturn(StationData returningFrom, ItemStorage thingsToDeliver, int carNumber)
    {
        BuildingStationExportModule exports = this.getModule(MCTPBuildingModules.EXPORTS);

        if (exports == null)
        {
            LOGGER.error("No export module - cannot place return order.");
            return;
        }

        if (thingsToDeliver == null || thingsToDeliver.isEmpty())
        {
            LOGGER.error("Shipments should not be initiated with nothing to deliver. Item being returned is null.");
            return;
        }

        int trackDistance = 0;

        ExportData export = exports.addReturn(returningFrom.getStation(), thingsToDeliver);
        export.setShipmentCountdown(1);
        export.setShipDistance(carNumber);

        TrackConnectionResult tcr = getTrackConnectionResult(returningFrom);
        if (tcr != null)
        {
            trackDistance = tcr.path.size();
        }
        else
        {
            // Fallback: This should only be needed if the connection got broken between the two stations between checks.
            // If the connection got broken between the two stations, just use the block positions of the rail start locations.
            trackDistance = (int) BlockPosUtil.dist(returningFrom.getRailStartPosition(), this.getRailStartPosition());
        }

        export.setTrackDistance(trackDistance);

        TraceUtils.dynamicTrace(TRACE_OUTPOST,
            () -> LOGGER.info("Set up return for {} to {}.", thingsToDeliver, this.getBuildingDisplayName()));
    }

    /**
     * Creates a collection of request resolvers for this outpost. This collection contains all request resolvers from the superclass,
     * as well as an additional resolver for outpost requests. The outpost request resolver is responsible for resolving requests for
     * the outpost.
     * 
     * @return A collection of request resolvers for this outpost.
     */
    @Override
    public ImmutableCollection<IRequestResolver<?>> createResolvers()
    {
        final ImmutableCollection<IRequestResolver<?>> supers = super.createResolvers();
        final ImmutableList.Builder<IRequestResolver<?>> builder = ImmutableList.builder();
        ILocation location = NullnessBridge.assumeNonnull(getRequester().getLocation());
        
        if (trainDeliveryResolverToken != null)
        {
            trainDeliveryResolver = new TrainDeliveryResolver(location, trainDeliveryResolverToken);
        }
        else
        {
            IToken <?> token = colony.getRequestManager().getFactoryController().getNewInstance(TypeConstants.ITOKEN);

            if (token != null)
            {
                trainDeliveryResolver = new TrainDeliveryResolver(location, token);
                trainDeliveryResolverToken = trainDeliveryResolver.getId();
            }
        }

        if (!supers.isEmpty())
        {
            builder.addAll(supers);
        }

        if (trainDeliveryResolver != null)
        {
            builder.add(trainDeliveryResolver);
        }

        return builder.build();
    }
}
