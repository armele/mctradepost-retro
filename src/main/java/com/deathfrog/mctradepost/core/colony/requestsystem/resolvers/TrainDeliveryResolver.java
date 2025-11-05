package com.deathfrog.mctradepost.core.colony.requestsystem.resolvers;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST_REQUESTS;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractRequestResolver;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class TrainDeliveryResolver extends AbstractRequestResolver<Delivery>
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public TrainDeliveryResolver(
      @Nonnull final ILocation location,
      @Nonnull final IToken<?> token)
    {
        super(location, token);
    }

    @Override
    public boolean canResolveRequest(final IRequestManager manager, final IRequest<? extends Delivery> requestToCheck)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Checking TrainDeliveryResolver for Request {} - {} from {} to {}.", 
            requestToCheck.getId(),
            requestToCheck.getLongDisplayString(), 
            requestToCheck.getRequest().getStart(), 
            requestToCheck.getRequest().getTarget()));

        ILocation destination = requestToCheck.getRequest().getTarget();
        ILocation start = requestToCheck.getRequest().getStart();

        IBuilding outpostCandidate = manager.getColony().getBuildingManager().getBuilding(destination.getInDimensionLocation());

        if (outpostCandidate instanceof BuildingOutpost outpost)
        {
            if (outpost.getConnectedStation() != null && outpost.getConnectedStation().getPosition().equals(start.getInDimensionLocation()))
            {
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Request {} - {} can be resolved by TrainDeliveryResolver", requestToCheck.getId(), requestToCheck.getLongDisplayString()));
                return true;
            }
        }

        return false;

    }

    @Override
    public TypeToken<? extends Delivery> getRequestType()
    {
        // TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Checking TrainDeliveryResolver request type."));
        return TypeConstants.DELIVERY;
    }

    @Override
    public @Nullable List<IToken<?>> attemptResolveRequest(@NotNull IRequestManager manager, @NotNull IRequest<? extends Delivery> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Attempting TrainDeliveryResolver resolution {} - {}.", request.getId(), request.getLongDisplayString()));
        return Lists.newArrayList();
    }

    @Override
    public boolean isValid()
    {
        return true;
    }

    @Override
    public void onAssignedRequestBeingCancelled(@NotNull IRequestManager manager, @NotNull IRequest<? extends Delivery> request)
    {
        // no-op
    }

    @Override
    public void onAssignedRequestCancelled(@NotNull IRequestManager manager, @NotNull IRequest<? extends Delivery> request)
    {
        if (!manager.getColony().getWorld().isClientSide)
        {
            final Colony colony = (Colony) manager.getColony();
            final IBuilding station = colony.getBuildingManager().getBuilding(getLocation().getInDimensionLocation(), BuildingStation.class);
            if (station == null)
            {
                return;
            }

            final WarehouseRequestQueueModule module = station.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
            module.getMutableRequestList().remove(request.getId());
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Request {} - {} cancelled.", request.getId(), request.getLongDisplayString()));
        }
    }

    @Override
    public void resolveRequest(@NotNull IRequestManager manager, @NotNull IRequest<? extends Delivery> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Request {} - {} resolved by TrainDeliveryResolver.", request.getId(), request.getLongDisplayString()));
        final Colony colony = (Colony) manager.getColony();

        final IBuilding station = colony.getBuildingManager().getBuilding(request.getRequest().getStart().getInDimensionLocation());
        if (station == null)
        {
            return;
        }

        final WarehouseRequestQueueModule module = station.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
        module.addRequest(request.getId());
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Request {} - {} added to station queue.", request.getId(), request.getLongDisplayString()));
    }

    @Override
    public void onRequestedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<?> arg1)
    {
        // no-op
    }

    @Override
    public void onRequestedRequestComplete(@NotNull IRequestManager arg0, @NotNull IRequest<?> arg1)
    {
        // no-op
    }

    @Override
    public int getSuitabilityMetric(@NotNull IRequestManager manager, @NotNull IRequest<? extends Delivery> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Checking TrainDeliveryResolver suitability metric for request {} - {}.", request.getId(), request.getLongDisplayString()));
        return -2;
    }

    /**
     * Returns the display name of the building that made the request.
     * 
     * @param manager the request manager from which the request was made
     * @param request the request for which the display name is being requested
     * @return the display name of the building that made the request
     */
    @Override
    public @NotNull MutableComponent getRequesterDisplayName(@NotNull IRequestManager manager, @NotNull IRequest<?> request) 
    {
        return Component.translatable("com.mctradepost.building.station");
    }

    /**
     * Retrieves the building associated with the given request token.
     * If the world is not on the client side, returns the building associated with the position of the given request token.
     * Otherwise, returns an empty Optional.
     * @param manager the request manager which contains the token
     * @param token the token of the resolver to find the building for
     * @return the building associated with the given resolver token, or an empty Optional if the world is on the client side
     */
    public Optional<IRequester> getBuilding(@NotNull final IRequestManager manager, @NotNull final IToken<?> token)
    {
        if (!manager.getColony().getWorld().isClientSide)
        {
            return Optional.ofNullable(manager.getColony().getRequesterBuildingForPosition(getLocation().getInDimensionLocation()));
        }

        return Optional.empty();
    }
}

