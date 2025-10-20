package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationImportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.jobs.JobStationMaster;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData.TrackConnectionStatus;
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
import com.minecolonies.api.util.InventoryUtils;
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

    public static final Map<StationData, Integer> remoteStationMessageCooldown = new HashMap<>();

    public enum StationMasterStates implements IAIState
    {
        CHECK_CONNECTION,
        FIND_MATCHING_OFFERS,
        SEND_SHIPMENT,
        ELIMINATE_OLD_ORDER,
        REQUEST_FUNDS,
        WALK_THE_TRACK;

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
     * Determines if the Station Master should export a configured item. It checks if there are any exports configured, and if so,
     * if the items are available in the building's inventory. If the items are available, it records the export data and returns true.
     * Otherwise, it records null and returns false.
     * 
     * @return true if an export should be sent, false otherwise.
     */
    protected IAIState findMatchingOffers() 
    {
        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        if (building.getModule(MCTPBuildingModules.EXPORTS).exportCount() > 0)
        {
            int unsatisfiedNeeds = 0;

            for (ExportData exportData : building.getModule(MCTPBuildingModules.EXPORTS).getExports())
            {
                if ((exportData.getShipDistance() >= 0) || (exportData.getLastShipDay() == building.getColony().getDay()))
                {
                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Export of {} is already in progress ({} of {} progress) or happened already today.", exportData.getTradeItem(), exportData.getShipDistance(), exportData.getTrackDistance()));
                    continue;
                }

                boolean hasExports = false;
                boolean remoteHasFunds = false;

                BuildingStation destinationStation = exportData.getDestinationStationData().getStation();

                if (destinationStation == null)
                {
                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Export of {} for {} is no longer valid (destination station no longer exists) - marking for removal.", exportData.getTradeItem(), exportData.getCost()));
                    currentExport = exportData;
                    return StationMasterStates.ELIMINATE_OLD_ORDER;
                }

                BuildingStationImportModule remoteImportModule = destinationStation.getModule(MCTPBuildingModules.IMPORTS);

                if (!TrackConnectionStatus.CONNECTED.equals(exportData.getDestinationStationData().getTrackConnectionStatus()) 
                    || !remoteImportModule.hasTrade(exportData.getTradeItem().getItemStack(), exportData.getCost(), exportData.getQuantity()))
                {
                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Export of {} for {} is no longer valid - marking for removal.", exportData.getTradeItem(), exportData.getCost()));
                    currentExport = exportData;
                    return StationMasterStates.ELIMINATE_OLD_ORDER;
                }

                int availableExportItemCount = availableCount(building, exportData.getTradeItem());
                
                if ((availableExportItemCount >= exportData.getQuantity())
                    && TrackConnectionStatus.CONNECTED.equals(exportData.getDestinationStationData().getTrackConnectionStatus())
                )
                {
                    hasExports = true;
                }

                BuildingStation remoteStation = (BuildingStation) exportData.getDestinationStationData().getStation();

                if (remoteStation == null || remoteStation.getAllAssignedCitizen().isEmpty())
                {
                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Export of {} for {} is no longer valid - marking for removal.", exportData.getTradeItem(), exportData.getCost()));
                    currentExport = exportData;
                    return StationMasterStates.ELIMINATE_OLD_ORDER;
                }

                ICitizenData remoteWorker = remoteStation.getAllAssignedCitizen().toArray(ICitizenData[]::new)[0];

                int availableRemoteFunds = availableCount(remoteStation, new ItemStorage(MCTradePostMod.MCTP_COIN_ITEM.get()));

                if ((availableRemoteFunds >= exportData.getCost()) && (remoteWorker != null))  
                {
                    remoteHasFunds = true;
                    exportData.setInsufficientFunds(false);
                }

                if (hasExports)
                {
                    if (remoteHasFunds)
                    {
                        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Supply of {} and necessary funds are available - mark for shipment.", exportData.getTradeItem()));
                        currentExport = exportData;

                        return StationMasterStates.SEND_SHIPMENT;
                    }
                    else
                    {
                        exportData.setInsufficientFunds(true);
                        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Necessary funds are NOT available.", exportData.getTradeItem()));

                        // Announce to the remote colony that the station does not have the required funds (with a cooldown)
                        int cooldown = remoteStationMessageCooldown.getOrDefault(exportData.getDestinationStationData(), 0);
                        if (cooldown == 0)
                        {
                            TraceUtils.dynamicTrace(TRACE_STATION, () -> TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Informed remote colony of funding need for {}.", exportData.getTradeItem())));
                            remoteStation.addPaymentRequest(exportData.getCost());
                            remoteStationMessageCooldown.put(exportData.getDestinationStationData(), MESSAGE_COOLDOWN_TIME);
                        }     
                        else
                        {
                            remoteStationMessageCooldown.put(exportData.getDestinationStationData(), cooldown - 1);
                        }         
                        building.markTradesDirty();
                    }
                }
                else
                {
                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Supplies needed to export {}: {}.", exportData.getTradeItem().getItem(), exportData.getQuantity()));
                    unsatisfiedNeeds += 1;
                }
            }

            if (unsatisfiedNeeds > 0)
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Supplies needed for exports."));
                return AIWorkerState.GET_MATERIALS;
            }
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("No exports configured for {}.", worker.getName()));
            setDelay(10);
        }

        currentExport = null;
        return AIWorkerState.DECIDE;
    }

    /**
     * Counts the number of items in the given {@link ItemStorage} that are available in the given {@link BuildingStation} and its assigned worker.
     * 
     * @param building The building to check.
     * @param stack    The item storage to check.
     * @return The count of available items.
     */
    protected int availableCount(BuildingStation buildingStation, ItemStorage stack)
    {
        int amountInBuilding = 0;
        int amountInWorkerInventory = 0;
        
        if (buildingStation != null)
        {
            amountInBuilding =
                InventoryUtils.getItemCountInItemHandler(buildingStation.getItemHandlerCap(), ExportData.hasExportItem(stack));

            if (buildingStation.getAllAssignedCitizen().isEmpty())
            {
                amountInWorkerInventory = 0;
            }
            else
            {
                ICitizenData  worker = buildingStation.getAllAssignedCitizen().toArray(ICitizenData[]::new)[0];
                amountInWorkerInventory = InventoryUtils.getItemCountInItemHandler(worker.getInventory(), ExportData.hasExportItem(stack));  
            }


        }
        
        return amountInBuilding + amountInWorkerInventory;
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
                if (!(checkbuilding instanceof BuildingStation station) || station.getPosition().equals(worker.getCitizenData().getWorkBuilding().getPosition()))
                {
                    continue;
                }

                if (building.hasStationAt(station.getPosition()))
                {
                    StationData stationData = building.getStationAt(station.getPosition());

                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Determining if station should be checked: {}", stationData));

                    if (stationData.getTrackConnectionStatus().equals(TrackConnectionStatus.UNKNOWN) 
                        || (stationData.ageOfCheck() > MCTPConfig.trackValidationFrequency.get())
                    )                   
                    {
                        currentRemoteStation = stationData;
                        return true;
                    }
                }
                else
                {
                    // Add the newly discovered station.
                    currentRemoteStation = new StationData(station);
                    building.addStation(currentRemoteStation);
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

                boolean isValid = TrackPathConnection.validateExistingPath(world, connectionResult.path);

                currentCheckingTrack.addAll(connectionResult.path);

                connectionResult.setConnected(isValid);
            }

            if (connectionResult.connected)
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Remote station {} is connected!", currentRemoteStation));
                currentRemoteStation.setTrackConnectionStatus(TrackConnectionStatus.CONNECTED);

                AdvancementUtils.TriggerAdvancementPlayersForColony(building.getColony(),
                        player -> MCTPAdvancementTriggers.COLONY_CONNECTED.get().trigger(player));
            }
            else
            {
                final TrackConnectionResult logResult = connectionResult;
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Remote station {} is NOT connected. Closest track found at {}", currentRemoteStation, logResult.closestPoint));
                currentRemoteStation.setTrackConnectionStatus(TrackConnectionStatus.DISCONNECTED);
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

        if (!building.hasWorkerOpenRequests(worker.getCitizenData().getId()))
        {
            final ArrayList<ItemStack> itemList = new ArrayList<>();
            for (ExportData exportData : building.getModule(MCTPBuildingModules.EXPORTS).getExports())
            {
                int availableExportItemCount = availableCount(building, exportData.getTradeItem());

                itemList.clear();
                int quantity = exportData.getQuantity();
                
                int amountStillNeeded = quantity - availableExportItemCount;

                while (amountStillNeeded > 0)
                {
                    final ItemStack itemStack = exportData.getTradeItem().getItemStack().copy();

                    int amountToTake = Math.min(itemStack.getMaxStackSize(), amountStillNeeded);
                    itemStack.setCount(amountToTake);
                    amountStillNeeded -= amountToTake;

                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Adding {} to delivery request list.", itemStack));

                    itemList.add(itemStack);
                }

                if (itemList.isEmpty())
                {
                    continue;
                }

                worker.getCitizenData().createRequestAsync(new StackList(itemList, BuildingStation.EXPORT_ITEMS, quantity));
            }
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("{} already has open requests. Not placing new ones.", worker.getCitizenData().getName()));
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
