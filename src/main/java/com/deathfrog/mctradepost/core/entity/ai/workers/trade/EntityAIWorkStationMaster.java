package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationImportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.jobs.JobStationMaster;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.util.AdvancementUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STATION;

public class EntityAIWorkStationMaster extends AbstractEntityAIInteract<JobStationMaster, BuildingStation>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final int MESSAGE_COOLDOWN_TIME = 1000;

    public static final String TRACK_VALIDATIONS = "tracks_validated";

    public static final int BASE_XP_NEW_TRACK = 5;
    public static final int BASE_XP_EXISTING_TRACK = 1;
    public static final int BASE_XP_SEND_SHIPMENT = 2;


    protected static final int OUTPOST_COOLDOWN_TIMER = 10;
    protected static int outpostCooldown = OUTPOST_COOLDOWN_TIMER;

    public static final Map<StationData, Integer> remoteStationMessageCooldown = new HashMap<>();

    public enum StationMasterStates implements IAIState
    {
        CHECK_CONNECTION,
        FIND_MATCHING_OFFERS,
        SEND_SHIPMENT,
        ELIMINATE_OLD_ORDER,
        REQUEST_FUNDS,
        WALK_THE_TRACK,
        HANDLE_OUTPOST_REQUESTS;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    private StationData currentRemoteStation = null;
    private ExportData currentExport = null;
    private Integer currentFundRequest = null;
    private BlockPos currentTargetWalkingPosition = null;
    Queue<BlockPos> currentCheckingTrack = new ArrayDeque<>();

    @SuppressWarnings("unchecked")
    public EntityAIWorkStationMaster(@NotNull JobStationMaster job)
    {
        super(job);
        super.registerTargets(
          new AITarget<IAIState>(IDLE, START_WORKING, 10),
          new AITarget<IAIState>(START_WORKING, DECIDE, 10),
          new AITarget<IAIState>(DECIDE, this::decideWhatToDo, 10),
          new AITarget<IAIState>(StationMasterStates.HANDLE_OUTPOST_REQUESTS, this::handleOutpostRequests, 10),
          new AITarget<IAIState>(GET_MATERIALS, this::getMaterials, 50),
          new AITarget<IAIState>(StationMasterStates.ELIMINATE_OLD_ORDER, this::eliminateOldOrder, 50),
          new AITarget<IAIState>(StationMasterStates.FIND_MATCHING_OFFERS, this::findMatchingOffers, 50),
          new AITarget<IAIState>(StationMasterStates.CHECK_CONNECTION, this::checkConnection, 50),
          new AITarget<IAIState>(StationMasterStates.REQUEST_FUNDS, this::requestFunds, 50),
          new AITarget<IAIState>(StationMasterStates.SEND_SHIPMENT, this::sendShipment, 50),
          new AITarget<IAIState>(StationMasterStates.WALK_THE_TRACK, this::walkTheTrack, 2)
        );
        worker.setCanPickUpLoot(true);
    }

    /**
     * Decides what to do next. 
     * 
     * @return the next AI state to transition to.
     */
    protected IAIState decideWhatToDo()
    {

        if (outpostCooldown-- <= 0)
        {
            outpostCooldown = OUTPOST_COOLDOWN_TIMER;
            return StationMasterStates.HANDLE_OUTPOST_REQUESTS;
        }

        if (shouldCheckConnection())
        {
            return StationMasterStates.CHECK_CONNECTION;
        }

        currentFundRequest = building.removePaymentRequest();
        if (currentFundRequest != null)
        {
            return StationMasterStates.REQUEST_FUNDS;
        }

        if (building.getModule(MCTPBuildingModules.EXPORTS).exportCount() > 0)
        {
            return StationMasterStates.FIND_MATCHING_OFFERS;
        }

        EntityNavigationUtils.walkToRandomPos(worker, 15, 0.6D);

        return AIWorkerState.DECIDE;
    }

    /**
     * Instructs building to handle outpost requests.
     * @return The next AI state to transition to.
     */
    protected IAIState handleOutpostRequests()
    {
        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        building.handleOutpostRequests();
        return AIWorkerState.DECIDE;
    }

    /**
     * Cancels an out of date export designation.
     * @return the next AI state to transition to.
     */
    protected IAIState eliminateOldOrder()
    {
        if (currentExport != null)
        {
            building.getModule(MCTPBuildingModules.EXPORTS).removeExport(currentExport.getDestinationStationData(), currentExport.getTradeItem().getItemStack());
            building.markTradesDirty();
            currentExport = null;
        }

        return StationMasterStates.FIND_MATCHING_OFFERS;
    }

    /**
     * This method is responsible for finding a matching export for a given set of exports.
     * It will iterate through all the exports and check if the destination exists or if the order should be eliminated.
     * It will also check if the track connection is valid or if the remote station is staffed.
     * If all the above conditions are met, it will mark the export for shipment and return the next state.
     * If not all conditions are met, it will notify the remote station of insufficient funds and mark the export for cooldown.
     * If no exports are found to be shipped, it will return the next state.
     *
     * @return The next AI state to transition to.
     */
    protected IAIState findMatchingOffers()
    {
        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        final BuildingStationExportModule exportsModule = building.getModule(MCTPBuildingModules.EXPORTS);

        if (exportsModule.exportCount() == 0)
        {
            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("No exports configured for {}.", worker.getName()));
            setDelay(10);
            currentExport = null;
            return AIWorkerState.DECIDE;
        }

        int unsatisfiedNeeds = 0;

        for (ExportData exportData : exportsModule.getExports())
        {
            // Skip if already shipping or already shipped today
            if (isAlreadyShippingToday(exportData)) continue;

            // 1) Destination must exist or we eliminate the order
            IAIState state = resolveDestinationOrEliminate(exportData);
            if (state != null) return state;

            // From here on, these are safe to dereference
            final StationData dest = exportData.getDestinationStationData();
            final ITradeCapable remote = dest.getStation();

            // 2) Connection lookup or check
            final TrackConnectionResult conn = building.getTrackConnectionResult(dest);
            state = getConnectionOrCheck(conn, dest, exportData);
            if (state != null) return state;

            final boolean stationConnected = conn.isConnected();

            // 3) If not an outpost, the remote must offer the trade and be connected, else eliminate
            state = validateRemoteImportOrEliminate(dest, stationConnected, exportData);
            if (state != null) return state;

            // 4) Do we have the goods locally?
            final boolean hasExports = hasLocalSupply(exportData);

            // 5) Remote must be staffed, else eliminate
            state = ensureRemoteStaffedOrEliminate(remote, exportData);
            if (state != null) return state;

            // 6) Remote funds check
            final boolean remoteHasFunds = remoteHasFunds(remote, exportData);

            // 7) Decide next state based on supply, connection, and funds
            if (hasExports)
            {
                if (!stationConnected)
                {
                    TraceUtils.dynamicTrace(TRACE_STATION,
                        () -> LOGGER.info("No connection to remote station for export of {} - check connection (status {}).",
                            exportData.getTradeItem(),
                            conn));
                    currentRemoteStation = dest;
                    return StationMasterStates.CHECK_CONNECTION; // (Outposts path; non-outposts were eliminated earlier)
                }

                if (remoteHasFunds)
                {
                    TraceUtils.dynamicTrace(TRACE_STATION,
                        () -> LOGGER.info("Supply of {} {} and necessary funds are available - mark for shipment.",
                            exportData.getQuantity(),
                            exportData.getTradeItem()));
                    currentExport = exportData;
                    return StationMasterStates.SEND_SHIPMENT;
                }
                else
                {
                    notifyInsufficientFundsAndCooldown(remote, dest, exportData);
                    building.markTradesDirty();
                    // keep scanning other exports
                }
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_STATION,
                    () -> LOGGER
                        .info("Supplies needed to export {}: {}.", exportData.getTradeItem().getItem(), exportData.getQuantity()));
                unsatisfiedNeeds++;
                // keep scanning other exports
            }
        }

        if (unsatisfiedNeeds > 0)
        {
            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Supplies needed for exports."));
            return AIWorkerState.GET_MATERIALS;
        }

        currentExport = null;
        return AIWorkerState.DECIDE;
    }

    /**
     * Returns true if the export is already in progress or happened already today.
     * @param e the export data to check
     * @return true if the export is already in progress or happened already today, false otherwise
     */
    protected boolean isAlreadyShippingToday(final ExportData e)
    {
        if ((e.getShipDistance() >= 0) || (e.getLastShipDay() == building.getColony().getDay()))
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info("Export of {} is already in progress ({} of {} progress) or happened already today.",
                    e.getTradeItem(),
                    e.getShipDistance(),
                    e.getTrackDistance()));
            return true;
        }
        return false;
    }


    /**
     * Resolves the destination station for a given export data object.
     * If the destination station no longer exists, the export is marked for removal and the AI state is set to ELIMINATE_OLD_ORDER.
     * @param e The export data object containing the trade item, cost, and destination station data.
     * @return The next AI state to transition to, or null if no transition is needed.
     */
    protected IAIState resolveDestinationOrEliminate(final ExportData e)
    {
        final ITradeCapable remote = e.getDestinationStationData().getStation();
        if (remote == null)
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info(
                    "Export of {} for {} is no longer valid (destination station no longer exists) - marking for removal.",
                    e.getTradeItem(),
                    e.getCost()));
            currentExport = e;
            return StationMasterStates.ELIMINATE_OLD_ORDER;
        }
        return null;
    }

    /**
     * Checks if the remote station is connected and offers the trade.
     * If the connection is unknown (null), the export is marked for removal and the AI state is set to CHECK_CONNECTION.
     * @param conn The connection result between the local station and the remote station.
     * @param dest The station data object of the remote station.
     * @param e The export data object containing the trade item, cost, and destination station data.
     * @return The next AI state to transition to, or null if no transition is needed.
     */
    protected IAIState getConnectionOrCheck(final TrackConnectionResult conn, final StationData dest, final ExportData e)
    {
        if (conn == null)
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info("UNKNOWN connection to remote station for export of {}.", e.getTradeItem()));
            currentRemoteStation = dest;
            return StationMasterStates.CHECK_CONNECTION;
        }
        return null;
    }

    /**
     * Validates if the remote station is connected and offers the trade.
     * If either condition is not met, the export is marked for removal.
     * Outposts skip import validation.
     * 
     * @param dest the destination station to validate
     * @param stationConnected whether the station is connected
     * @param e the export data to validate
     * @return the next AI state to transition to, or null if no transition is needed
     */
    protected IAIState validateRemoteImportOrEliminate(final StationData dest, final boolean stationConnected, final ExportData e)
    {
        if (dest.isOutpost()) return null; // outposts skip import validation

        final BuildingStationImportModule remoteImport = dest.getStation().getModule(MCTPBuildingModules.IMPORTS);

        if (remoteImport == null)
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info("Export of {} for {} no longer has a valid destination station - marking for removal.",
                    e.getTradeItem(),
                    e.getCost()));
            currentExport = e;
            return StationMasterStates.ELIMINATE_OLD_ORDER;
        }

        final boolean tradeOffered = remoteImport.hasTrade(e.getTradeItem().getItemStack(), e.getCost(), e.getQuantity());

        if (!stationConnected || !tradeOffered)
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info("Export of {} for {} is no longer valid (trade not offered) - marking for removal.",
                    e.getTradeItem(),
                    e.getCost()));
            currentExport = e;
            return StationMasterStates.ELIMINATE_OLD_ORDER;
        }

        return null;
    }

    /**
     * Returns true if the building has enough of the specified item to fulfill the export request.
     * 
     * @param e the export data to check against the building's inventory.
     * @return true if the building has enough of the item, false otherwise.
     */
    protected boolean hasLocalSupply(final ExportData e)
    {
        final int available = BuildingUtil.availableCount(building, e.getTradeItem());
        return available >= e.getQuantity();
    }

    /**
     * Ensures that a remote station has a worker assigned to process an export.
     * If the remote station does not have a worker, the export is marked for removal.
     * If the remote station is an outpost and does not have a record of this expected shipment, the export is marked for removal.
     * @param remote the remote station to check.
     * @param e the export data to validate against the remote station.
     * @return the next AI state to transition to, or null if no transition is needed.
     */
    protected IAIState ensureRemoteStaffedOrEliminate(final ITradeCapable remote, final ExportData e)
    {
        if (remote == null || remote.getAllAssignedCitizen().isEmpty())
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info("Export of {} for {} is no longer valid (remote station not staffed) - marking for removal.",
                    e.getTradeItem(),
                    e.getCost()));
            currentExport = e;
            return StationMasterStates.ELIMINATE_OLD_ORDER;
        }

        if (remote instanceof BuildingOutpost outpost)
        {
            boolean hasExpectedShipment = outpost.hasExpectedShipment(e);
            if (!hasExpectedShipment)
            {
                TraceUtils.dynamicTrace(TRACE_STATION,
                    () -> LOGGER.info("Export of {} to outpost {} is no longer valid (no record of this expected shipment) - marking for removal.",
                        e.getTradeItem(),
                        outpost.getBuildingDisplayName()));
                        
                currentExport = e;
                return StationMasterStates.ELIMINATE_OLD_ORDER;
            }
        }

        return null;
    }

    protected boolean remoteHasFunds(final ITradeCapable remote, final ExportData e)
    {
        final int availableRemoteFunds = BuildingUtil.availableCount(remote, new ItemStorage(MCTradePostMod.MCTP_COIN_ITEM.get()));
        final ICitizenData remoteWorker = remote.getAllAssignedCitizen().toArray(ICitizenData[]::new)[0];
        final boolean ok = (availableRemoteFunds >= e.getCost()) && (remoteWorker != null);
        if (ok) e.setInsufficientFunds(false);
        return ok;
    }

    protected void notifyInsufficientFundsAndCooldown(final ITradeCapable remote, final StationData dest, final ExportData e)
    {
        e.setInsufficientFunds(true);
        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Necessary funds are NOT available.", e.getTradeItem()));

        int cooldown = remoteStationMessageCooldown.getOrDefault(dest, 0);
        if (cooldown == 0)
        {
            TraceUtils.dynamicTrace(TRACE_STATION,
                () -> LOGGER.info("Informed remote colony of funding need for {}.", e.getTradeItem()));

            if (remote instanceof BuildingStation remoteStation)
            {
                remoteStation.addPaymentRequest(e.getCost());
            }
            remoteStationMessageCooldown.put(dest, MESSAGE_COOLDOWN_TIME);
        }
        else
        {
            remoteStationMessageCooldown.put(dest, cooldown - 1);
        }
    }


    /**
     * Initiates trade shipment by setting the shipment distance to 0 for the current export. 
     * If there is no current export, does nothing.
     * 
     * @return the next AI state to transition to, which is always IDLE.
     */
    protected IAIState sendShipment() 
    {
        if (currentExport != null)
        {   
            TrackConnectionResult tcr = ((BuildingStation) building).getTrackConnectionResult(currentExport.getDestinationStationData());
            if (tcr == null)
            {
                currentRemoteStation = currentExport.getDestinationStationData();
                return StationMasterStates.CHECK_CONNECTION;
            }


            int trackDistance = tcr.path.size();
            currentExport.setShipDistance(0);
            currentExport.setTrackDistance(trackDistance);
            currentExport.setLastShipDay(building.getColony().getDay());

            // Remove the outbound export from this building/worker
            if (MCTPInventoryUtils.combinedInventoryRemoval(building, currentExport.getTradeItem(), currentExport.getQuantity())) 
            {
                // Remove the inbound payment from remote building/worker.
                if (!MCTPInventoryUtils.combinedInventoryRemoval(currentExport.getDestinationStationData().getStation(), new ItemStorage(MCTradePostMod.MCTP_COIN_ITEM.get()), currentExport.getCost()))
                {
                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Receiving station no longer has adequate funds.  Restoring items."));
                    MCTPInventoryUtils.InsertOrDropByQuantity(building, currentExport.getTradeItem(), currentExport.getQuantity());

                    currentExport = null;
                    incrementActionsDoneAndDecSaturation();
                    return AIWorkerState.DECIDE;
                } 
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("No longer enough {} in worker inventory to ship.", currentExport.getTradeItem().getItem()));
                currentExport = null;
                return AIWorkerState.DECIDE;
            }

            worker.getCitizenExperienceHandler().addExperience(BASE_XP_EXISTING_TRACK);
            GhostCartEntity cart = currentExport.spawnCartForTrade(tcr.path);
            if (cart == null) {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Could not spawn cart for export: {}", currentExport));
            }

            building.markTradesDirty();
            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Shipment initiated for export: {}", currentExport));

            currentExport = null;
        }
        
        return AIWorkerState.DECIDE;
    }

    /**
     * Determines whether there is a station with an unknown track connection status
     * that should be checked. Iterates through all colonies and their buildings to
     * find stations. If a station is found with an unknown track connection status,
     * sets it as the current remote station and returns true. If a new station is
     * discovered, adds it to the list and returns true. Returns false if no such
     * station is found.
     *
     * @return true if a station with an unknown connection status is found, false otherwise.
     */
    protected boolean shouldCheckConnection() 
    { 
        for (IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            for (IBuilding checkbuilding : colony.getBuildingManager().getBuildings().values())
            {
                // Disregard if it's not a station, or if it is the current station.
                if (!(checkbuilding instanceof ITradeCapable tradeCapableBuilding) || tradeCapableBuilding.getPosition().equals(worker.getCitizenData().getWorkBuilding().getPosition()))
                {
                    continue;
                }

                if (this.building.hasStationAt(tradeCapableBuilding.getPosition()))
                {
                    StationData stationData = this.building.getStationAt(tradeCapableBuilding.getPosition());

                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Determining if trade capable building at {} should be checked: {}", tradeCapableBuilding.getPosition(), stationData));

                    ITradeCapable remoteTradeCapable = stationData.getStation();
                    TrackConnectionResult connectionResult = building.getTrackConnectionResult(stationData);
                    
                    if ((connectionResult == null) || (connectionResult.ageOfCheck(world.getGameTime()) > MCTPConfig.trackValidationFrequency.get())
                    )                   
                    {
                        currentRemoteStation = stationData;
                        return true;
                    }
                }
                else
                {
                    // Add the newly discovered station.
                    currentRemoteStation = new StationData(tradeCapableBuilding);
                    this.building.addStation(currentRemoteStation);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if the remote station is connected to the current station.
     * If the remote station is connected, sets its track connection status
     * to CONNECTED, otherwise sets it to DISCONNECTED.
     *
     * @return IDLE state.
     */
    protected IAIState checkConnection()
    {
        if (currentRemoteStation != null)
        {
            TrackConnectionResult connectionResult = building.getTrackConnectionResult(currentRemoteStation);

            if (connectionResult == null)
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("No cached result. Checking connection to remote station: {} seeking endpoint: {}", building.getRailStartPosition(), currentRemoteStation.getRailStartPosition()));
                connectionResult = TrackPathConnection.arePointsConnectedByTracks(world, building.getRailStartPosition(), currentRemoteStation.getRailStartPosition(),true);
                building.putTrackConnectionResult(currentRemoteStation, connectionResult);
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Cached connection found. Validating it."));

                boolean isValid = TrackPathConnection.validateExistingPath(world, connectionResult);

                currentCheckingTrack.addAll(connectionResult.path);

                connectionResult.setConnected(isValid);
            }

            if (connectionResult.connected)
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Remote station {} is connected!", currentRemoteStation));

                AdvancementUtils.TriggerAdvancementPlayersForColony(building.getColony(),
                        player -> MCTPAdvancementTriggers.COLONY_CONNECTED.get().trigger(player));
            }
            else
            {
                final TrackConnectionResult logResult = connectionResult;
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Remote station {} is NOT connected. Closest track found at {}", currentRemoteStation, logResult.closestPoint));
            }
        }
        
        currentRemoteStation = null;
        worker.getCitizenExperienceHandler().addExperience(BASE_XP_EXISTING_TRACK);
        incrementActionsDoneAndDecSaturation();
        StatsUtil.trackStat(building, TRACK_VALIDATIONS, 1);
        return StationMasterStates.WALK_THE_TRACK;
    }


    /**
     * Walks the station master along the track to simulate verifying the connection.
     * If there is no track to check, transitions to the DECIDE state.
     * Otherwise, the station master moves to the next target position on the track.
     * If the target position is outside the colony borders, the process is stopped 
     * and transitions to the DECIDE state. Otherwise, continues walking the track.
     *
     * @return the next AI state to transition to, either continuing to walk the track
     *         or deciding the next action if the track is complete or invalid.
     */
    private IAIState walkTheTrack()
    {
        if (currentCheckingTrack == null || currentCheckingTrack.isEmpty())
        {
            return DECIDE;
        }
        else
        {
            if (currentTargetWalkingPosition == null)
            {
                currentTargetWalkingPosition = currentCheckingTrack.poll();
            }

            if (this.walkToSafePos(currentTargetWalkingPosition)) 
            {

                // Station master will only walk to the colony border while checking tracks.
                IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(building.getColony().getWorld(), currentTargetWalkingPosition);
                if (colony == null || !colony.equals(building.getColony()))
                {
                    currentTargetWalkingPosition = null;
                    currentCheckingTrack.clear();
                    return DECIDE;
                }

                currentTargetWalkingPosition = null;
            }
            return StationMasterStates.WALK_THE_TRACK;
        }
    }
        

    /**
     * Method for the AI to try to get the coins needed to pay for a shipment.
     *
     * @return the new IAIState after doing this
     */
    private IAIState requestFunds()
    {
        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        final ArrayList<ItemStack> itemList = new ArrayList<>();
        itemList.add(new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get(), currentFundRequest));

        worker.getCitizenData()
            .createRequestAsync(new StackList(itemList,
                BuildingStation.FUNDING_ITEMS,
                currentFundRequest.intValue(),
                currentFundRequest.intValue(),
                0));

        currentFundRequest = null;

        setDelay(2);
        return DECIDE;
    }

    /**
     * Method for the AI to try to get the materials needed for the task they're doing. Will request if there are no materials
     *
     * @return the new IAIState after doing this
     */
    private IAIState getMaterials()
    {
        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        final ArrayList<ItemStack> itemList = new ArrayList<>();
        for (ExportData exportData : building.getModule(MCTPBuildingModules.EXPORTS).getExports())
        {

            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Analyzing need for {}.", exportData.getTradeItem().getItemStack().getHoverName()));

            if (building.isItemStackInRequest(exportData.getTradeItem().getItemStack()))
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Skipping request for {} - request already outstanding.", exportData.getTradeItem().getItemStack().getHoverName()));
                continue;
            }

            int availableExportItemCount = BuildingUtil.availableCount(building, exportData.getTradeItem());

            itemList.clear();
            int quantity = exportData.getQuantity();
            
            int amountStillNeeded = quantity - availableExportItemCount;

            while (amountStillNeeded > 0)
            {
                final ItemStack itemStack = exportData.getTradeItem().getItemStack().copy();

                int amountToTake = Math.min(itemStack.getMaxStackSize(), amountStillNeeded);
                itemStack.setCount(amountToTake);
                amountStillNeeded -= amountToTake;

                final int currentAmountStillNeeded = amountStillNeeded;
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Adding {} to delivery request list. Total order: {}. On hand: {}. Amount still needed: {}. Taking {} in this stack.", 
                    itemStack, quantity, availableExportItemCount, currentAmountStillNeeded, amountToTake));

                itemList.add(itemStack);
            }

            if (itemList.isEmpty())
            {
                continue;
            }

            worker.getCitizenData().createRequestAsync(new StackList(itemList, BuildingStation.EXPORT_ITEMS, quantity));
        }

        setDelay(2);
        return DECIDE;
    }

    /**
     * The building class this AI is intended to be used with.
     * 
     * @return the building class
     */
    @Override
    public Class<BuildingStation> getExpectedBuildingClass()
    {
        return BuildingStation.class;
    }
    
}
