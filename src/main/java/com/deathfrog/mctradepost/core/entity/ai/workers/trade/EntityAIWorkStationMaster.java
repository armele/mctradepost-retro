package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.jobs.JobStationMaster;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData.TrackConnectionStatus;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.mojang.logging.LogUtils;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STATION;

public class EntityAIWorkStationMaster extends AbstractEntityAIInteract<JobStationMaster, BuildingStation>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public enum StationMasterStates implements IAIState
    {
        CHECK_CONNECTION;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    private StationData currentRemoteStation = null;

    public EntityAIWorkStationMaster(@NotNull JobStationMaster job)
    {
        super(job);
        super.registerTargets(
          new AITarget<IAIState>(IDLE, START_WORKING, 10),
          new AITarget<IAIState>(START_WORKING, DECIDE, 10),
          new AITarget<IAIState>(DECIDE, this::decideWhatToDo, 10),
          new AITarget<IAIState>(StationMasterStates.CHECK_CONNECTION, this::checkConnection, 10)
        );
        worker.setCanPickUpLoot(true);
    }

    protected IAIState decideWhatToDo()
    {

        if (shouldCheckConnection())
        {
            return StationMasterStates.CHECK_CONNECTION;
        }

        EntityNavigationUtils.walkToRandomPos(worker, 10, 0.6D);
        return AIWorkerState.IDLE;
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
            TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Checking connection to remote station: {} seeking endpoint: {}", currentRemoteStation, building.getRailStartPosition()));
            TrackConnectionResult connectionResult = TrackPathConnection.arePointsConnectedByTracks(world, currentRemoteStation.getRailStartPosition(), building.getRailStartPosition());

            if (connectionResult.connected)
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Remote station {} is connected!", currentRemoteStation));
                currentRemoteStation.setTrackConnectionStatus(TrackConnectionStatus.CONNECTED);
                currentRemoteStation = null;
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Remote station {} is NOT connected. Closest track found at {}", currentRemoteStation, connectionResult.closestPoint));
                currentRemoteStation.setTrackConnectionStatus(TrackConnectionStatus.DISCONNECTED);
                currentRemoteStation = null;
            }
        }

        return AIWorkerState.IDLE;
    }

    @Override
    public Class<BuildingStation> getExpectedBuildingClass()
    {
        return BuildingStation.class;
    }
    
}
