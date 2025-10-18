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
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationImportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.ITradeCapable;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.eventhooks.citizenEvents.VisitorSpawnedEvent;
import com.minecolonies.core.datalistener.CustomVisitorListener;
import com.minecolonies.core.datalistener.RecruitmentItemsListener;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import static com.minecolonies.api.util.constant.Constants.MAX_STORY;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STATION;

public class BuildingStation extends AbstractBuilding implements ITradeCapable
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static String EXPORTS_SHIPPED = "exports_shipped";
    public static String IMPORTS_RECEIVED = "imports_received";

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
     * Retrieves the map of stations for this building.
     * The map is keyed by BlockPos, which is the position of the station in the building.
     * The values are the StationData objects associated with the station at that position.
     * @return a reference to the map of stations.
     */
    public Map<BlockPos, StationData> getStations() 
    {
        return stations;
    }

    /**
     * Clears the list of connected stations for this building.
     * This should be called when the building is being destroyed, or when all stations
     * are being cleared from the building for some other reason.
     */
    public void clearConnectedStations() 
    {
        stations.clear();
    }

    /**
     * Adds a station to the list of stations for this building.
     * The station is mapped to its position in the building's station map.
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
     * Marks the trades as dirty, so that they will be re-sent to the client on the next update.
     * This cascades to both the import and export modules.
     * This is useful when the user changes the trade settings, so that the client can receive the
     * updated trade data.
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
     * Validates the stations that are connected to this station.
     * 
     * Goes through all the stations in the list and checks if they are still valid.
     * Checks for the following conditions:
     * - The server is not null.
     * - The level is not null.
     * - The colony is not null.
     * - The building is not null and is an instance of BuildingStation.
     * If any of these conditions are not met, the station is removed from the list.
     */
    protected void validateStations()
    {
        for (StationData remoteStation : this.stations.values() )
        {
            MinecraftServer server = getColony().getWorld().getServer();

            if (server == null) {
                stations.remove(remoteStation.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no server): {} - removing it.", remoteStation);
                markTradesDirty();
                continue;
            }

            Level level = server.getLevel(remoteStation.getDimension());

            if (level == null) {
                stations.remove(remoteStation.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no level): {} - removing it.", remoteStation);
                markTradesDirty();
                continue;
            }

            IColony stationColony = IColonyManager.getInstance().getIColony(level, remoteStation.getBuildingPosition());

            if (stationColony == null) {
                stations.remove(remoteStation.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no colony): {} - removing it.", remoteStation);
                markTradesDirty();
                continue;
            }

            IBuilding building = stationColony.getBuildingManager().getBuilding(remoteStation.getBuildingPosition());

            if (building == null || !(building instanceof ITradeCapable)) {
                stations.remove(remoteStation.getBuildingPosition());
                BuildingStation.LOGGER.warn("Failed to validate station (no building): {} - removing it.", remoteStation);
                markTradesDirty();
                continue;
            }

            // Restore track connection information if not present (for example, after restoring from a save)
            if (getTrackConnectionResult(remoteStation) == null) {
                TrackConnectionResult trackConnectionResult = TrackPathConnection.arePointsConnectedByTracks((ServerLevel) this.getColony().getWorld(), 
                    this.getRailStartPosition(), remoteStation.getRailStartPosition(), false);
                putTrackConnectionResult(remoteStation, trackConnectionResult);
                markTradesDirty();
            }
        }
    }

    /**
     * Handles the spawning of tourists in the station, based on the current research level for tourists.
     * <p>
     * If the station is open for business and the number of external citizens is less than the current tourism level
     * multiplied by the building level, a visitor is spawned and the recruitment interaction is triggered with the
     * first name of the visitor.
     * <p>
     * The time until the next visitor is determined by the current number of citizens in the colony, the maximum
     * number of citizens in the colony and the current building level.
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
            hat = new ItemStack(Items.LEATHER_HELMET);
        }
        if (recruitLevel > 2)
        {
            // Gold
            hat = new ItemStack(Items.GOLDEN_HELMET);
        }
        if (recruitLevel > 3)
        {
            // Iron
            hat = new ItemStack(Items.IRON_HELMET);
        }
        if (recruitLevel > 4)
        {
            // Diamond
            hat = new ItemStack(Items.DIAMOND_HELMET);
        }
        return hat;
    }

    /**
     * Returns true if the station is open for business, i.e. if it has a
     * stationmaster assigned and they are working.
     * @return true if the station is open for business, false otherwise.
     */
    public boolean isOpenForBusiness() 
    {
        List<ICitizenData> employees = this.getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.stationmaster.get()).getAssignedCitizen();
        
        if (employees.isEmpty()) {
            return false;
        }
    
        final Optional<AbstractEntityCitizen> optionalEntityCitizen = employees.get(0).getEntity();

        if (!optionalEntityCitizen.isPresent()) {
            return false;
        }
        
        AbstractEntityCitizen stationmaster = optionalEntityCitizen.get();

        IState workState = ((EntityCitizen) stationmaster).getCitizenAI().getState();

        return CitizenAIState.WORKING.equals(workState);
    }

    @Override
    public CompoundTag serializeNBT(final HolderLookup.Provider provider)
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

        return compound;
    }

    /**
     * Deserializes the state of the building from NBT, including the list of stations.
     * @param provider The holder lookup provider for item and block references.
     * @param compound The compound tag containing the serialized state of the building.
     */
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

    }

    /**
     * Serializes the current state of the building, including the list of stations, to the given buffer.
     * The state of the stations is stored under the key TAG_STATIONS in the serialized CompoundTag.
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
     * @param result The result of the track connection, which includes whether the connection is successful,
     *               the closest point on the track, and the path of the track connection.
     */
    public void putTrackConnectionResult(StationData stationData, TrackConnectionResult result)
    {
        connectionresults.put(stationData, result);
    }

    /**
     * Retrieves the track connection result for a given station data object.
     *
     * @param stationData The station data for which the track connection result is being retrieved.
     * @return The track connection result, which includes whether the connection is successful,
     *         the closest point on the track, and the path of the track connection.
     */
    public TrackConnectionResult getTrackConnectionResult(StationData stationData)
    {
        return connectionresults.get(stationData);
    }

    /**
     * Retrieves the BlockPos of the starting point of the rail network for this train station.
     * If there is no starting point specified, the position of the building itself is returned.
     * If there are multiple starting points specified in the building's NBT, the first one is used.
     * A warning will be logged if there are multiple starting points found.
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
     * Adds a payment request to the queue. The request will be processed on the
     * next call to {@link #removePaymentRequest()}.
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
     * Completes a shipment of goods from this station to another station specified by the export data.
     * This method will add the goods to the remote station's inventory or drop them on the ground if the inventory is full.
     * It will also add the payment to the local station's inventory or drop it on the ground if the inventory is full.
     * Additionally, it will give experience to the worker at the remote station.
     * @param exportData the export data containing the station to ship to, the item to ship, and the cost of shipping.
     */
    public void completeExport(ExportData exportData)
    {
        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Shipment completed of {} for {} at {}", exportData.getTradeItem().getItem(), exportData.getCost(), exportData.getDestinationStationData().getStation().getPosition()));

        ItemStack finalPayment = ItemStack.EMPTY;
        
        if (exportData.getCost() > 0) 
        {
            finalPayment = new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get(), exportData.getCost());
        }

        StatsUtil.trackStatByName(this, EXPORTS_SHIPPED, exportData.getTradeItem().getItemStack().getHoverName(), exportData.getQuantity());
        StatsUtil.trackStatByName(exportData.getDestinationStationData().getStation(), IMPORTS_RECEIVED, exportData.getTradeItem().getItemStack().getHoverName(), exportData.getQuantity());

        ITradeCapable remoteStation = exportData.getDestinationStationData().getStation();
 
        MCTPInventoryUtils.InsertOrDropByQuantity(remoteStation, exportData.getTradeItem(), exportData.getQuantity());

        // Adds to the local building inventory and calls for a pickup to the warehouse or drops on the ground if inventory is full.
        if (InventoryUtils.addItemStackToItemHandler(this.getItemHandlerCap(), finalPayment))
        {
            remoteStation.onShipmentReceived(exportData);
        }
        else
        {
            MCTPInventoryUtils.dropItemsInWorld((ServerLevel) this.getColony().getWorld(), this.getPosition(), finalPayment);
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

    @Override
    public void onShipmentDelivered(ExportData shipmentSent)
    {
        if (!getAllAssignedCitizen().isEmpty())
        {
            ICitizenData exportWorker = getAllAssignedCitizen().toArray(ICitizenData[]::new)[0];
            exportWorker.getEntity().get().getCitizenExperienceHandler().addExperience(shipmentSent.getCost());
        }
    }

    /**
     * Called when a shipment has been received at this building. This method is used to call for a pickup for the received items.
     * If the building is an outpost, it will call for a pickup for the full amount of the shipment. If the building is not an outpost, it will call for a pickup for each stack of the shipment, up to the max stack size of the item.
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
            IToken<?> token = BuildingUtil.bringThisToTheWarehouse(this, new ItemStack(shipmentReceived.getTradeItem().getItemStack().getItem(), thisPickup));

            if (token != null)
            {
                calledForPickup = true;
            }
        }

        if (calledForPickup)
        {
            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Calling for pickup for {} at {}", this.getBuildingDisplayName(), this.getPosition()));
        }

        if (!this.getAllAssignedCitizen().isEmpty())
        {
            ICitizenData worker = this.getAllAssignedCitizen().toArray(ICitizenData[]::new)[0];
            
            if (worker != null && !worker.getEntity().isEmpty())
            {
                worker.getEntity().get().getCitizenExperienceHandler().addExperience(shipmentReceived.getCost());
            }
        }
    }

}
