package com.deathfrog.mctradepost.core.colony.requestsystem.resolvers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.OutpostShipmentTracking;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractBuildingDependentRequestResolver;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST_REQUESTS;

/*
 * Modelled after DeliverymenRequestResolver
 */
public class OutpostRequestResolver extends AbstractBuildingDependentRequestResolver<IDeliverable>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public OutpostRequestResolver(@NotNull ILocation location, @NotNull IToken<?> token)
    {
        super(location, token);
    }

    @Override
    public int getPriority()
    {
        return 300;
    }

    @Override
    public int getSuitabilityMetric(@NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Checking OutpostRequestResolver suitability metric for request {} - {}.", request.getId(), request.getLongDisplayString()));
        return -1;
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
        return Component.translatable("com.mctradepost.building.outpost");
    }

    /**
     * Called by the manager given to indicate that this request has been assigned to you.
     *
     * @param manager    The systems manager.
     * @param request    The request assigned.
     * @param simulation True when simulating.
     */
    public void onRequestAssigned(@NotNull final IRequestManager manager, @NotNull final IRequest<? extends IDeliverable> request, boolean simulation)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("This request {} has been assigned to the outpost: {}", request.getId(), request.getLongDisplayString()));
    }

    @Override
    public boolean isValid()
    {
        // Always valid
        return true;
    }
    @Override
    public void onAssignedRequestBeingCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<? extends IDeliverable> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Outpost assigned request being cancelled: {}", request.getLongDisplayString()));
    }

    @Override
    public void onAssignedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<? extends IDeliverable> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Outpost assigned request cancelled: {}", request.getLongDisplayString()));
    }

    @Override
    public void onRequestedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<?> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Requested outpost request cancelled: {}", request.getLongDisplayString()));
    }

    @Override
    public void onRequestedRequestComplete(@NotNull IRequestManager arg0, @NotNull IRequest<?> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Requested outpost request complete: {}", request.getLongDisplayString()));
    }

    @Override
    public TypeToken<IDeliverable> getRequestType()
    {
        // LOGGER.info("Checking outpost request type.");
        return TypeConstants.DELIVERABLE;
    }

    /**
     * Retrieves the building associated with the given request token.
     * If the world is not on the client side, returns the building associated with the position of the given request token.
     * Otherwise, returns an empty Optional.
     * @param manager the request manager which contains the token
     * @param token the token of the resolver to find the building for
     * @return the building associated with the given resolver token, or an empty Optional if the world is on the client side
     */
    @Override
    public Optional<IRequester> getBuilding(@NotNull final IRequestManager manager, @NotNull final IToken<?> token)
    {
        if (!manager.getColony().getWorld().isClientSide)
        {
            return Optional.ofNullable(manager.getColony().getRequesterBuildingForPosition(getLocation().getInDimensionLocation()));
        }

        return Optional.empty();
    }

    /**
     * Attempts to resolve the outpost request by generating any necessary prerequisite requests, such as deliveries from the station
     * or shipments from the outpost to the station.
     *
     * @param manager the request manager from which to resolve the request
     * @param requestToCheck the request to check for resolution
     * @param requestingBuilding the building from which the request is being made
     * @return a list of prerequisite requests, or null if the request cannot be resolved
     */
    @Override
    public @Nullable List<IToken<?>> attemptResolveForBuilding(@NotNull IRequestManager manager,
        @NotNull IRequest<? extends IDeliverable> requestToCheck,
        @NotNull AbstractBuilding requestingBuilding)
    {
        if (manager.getColony().getWorld().isClientSide)
        {
            return null;
        }

        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Attempting to resolve outpost request: {} - {}", requestToCheck.getId(), requestToCheck.getLongDisplayString()));

        if (requestingBuilding == null)
        {
            return null;
        }

        IBuilding outpostBuilding = BuildingOutpost.toOutpostBuilding(requestingBuilding);

        if (outpostBuilding == null)
        {
            return null;
        }

        IBuilding connectedStation = ((BuildingOutpost) outpostBuilding).getConnectedStation();

        if (connectedStation == null)
        {
            // If we have an outpost request while the outpost is disconnected from the station for some reason, 
            // we can't propagate the request chain - but we do want to hold the request with the outpost request
            // resolver to prevent a warehouse request from being attempted.
            return Lists.newArrayList();
        }

        OutpostShipmentTracking tracking = ((BuildingOutpost) outpostBuilding).trackingForRequest(requestToCheck);
        List<IToken<?>> prerequisiteRequests = new ArrayList<>();

        if (requestToCheck.getRequest() instanceof IDeliverable deliverable)
        {
            final int totalRequested = deliverable.getCount();

            /*
            int totalInOutpost = 0;
            final List<Tuple<ItemStack, BlockPos>> outpostInv = BuildingUtil.getMatchingItemStacksInBuilding(outpostBuilding, itemStack -> deliverable.matches(itemStack));

            for (final Tuple<ItemStack, BlockPos> stack : outpostInv)
            {
                if (!stack.getA().isEmpty())
                {
                    totalInOutpost += stack.getA().getCount();
                }
            }
            */

            int totalInStation = 0;
            int totalScheduled = 0;
            final List<Tuple<ItemStack, BlockPos>> stationInv = BuildingUtil.getMatchingItemStacksInBuilding(connectedStation, itemStack -> ((IDeliverable) requestToCheck.getRequest()).matches(itemStack));

            for (final Tuple<ItemStack, BlockPos> stack : stationInv)
            {
                if (!stack.getA().isEmpty())
                {
                    totalInStation += stack.getA().getCount();

                    if (totalInStation <= totalRequested)
                    {
                        totalScheduled = totalScheduled + stack.getA().getCount();
                        Delivery delivery = new Delivery(connectedStation.getLocation(), outpostBuilding.getLocation(), stack.getA().copy(), 0);
                        IToken<?> deliveryRequestToken = outpostBuilding.createRequest(delivery, true);
                        prerequisiteRequests.add(deliveryRequestToken);
                        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created partial delivery request {}.", deliveryRequestToken));
                    }
                    else
                    {
                        ItemStack partialShipment = stack.getA().copyWithCount(totalRequested - totalScheduled);
                        Delivery delivery = new Delivery(connectedStation.getLocation(), outpostBuilding.getLocation(), partialShipment, 0);
                        IToken<?> deliveryRequestToken = outpostBuilding.createRequest(delivery, true);
                        prerequisiteRequests.add(deliveryRequestToken);
                        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created satisfied delivery request {}.", deliveryRequestToken));
                        break;
                    }
                }
            }
            
            final int totalRemainingRequired = Math.max(totalRequested - totalInStation, 0);

            final int stationCountForLog = totalInStation;
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("This is deliverable request {} of type {} with {} items in the station, and {} items remaining to be collected for shipment: {}", 
                requestToCheck.getId(),
                deliverable.getClass().getName(),    
                stationCountForLog, 
                totalRemainingRequired,
                requestToCheck.getLongDisplayString()));

            if (totalRemainingRequired > 0)
            {
                IDeliverable deliverableCopy = ((IDeliverable) requestToCheck.getRequest()).copyWithCount(totalRemainingRequired);
                tracking.setState(OutpostOrderState.NEEDS_ITEM_FOR_SHIPPING);
                IToken<?> finalShipmentToken = connectedStation.createRequest(deliverableCopy, true);
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created final shipment request for {} as {} for: {}", requestToCheck.getId(), finalShipmentToken, deliverableCopy.toString()));
                prerequisiteRequests.add(finalShipmentToken);
            }
            else
            {
                // We have everything we need, but the station master still needs to make the shipment(s).
                tracking.setState(OutpostOrderState.ITEM_READY_TO_SHIP);
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Station has everything needed to fulfil request {}: {}", requestToCheck.getId(), requestToCheck.getLongDisplayString()));
            }
        }

        return prerequisiteRequests;
    }


    /**
     * Checks if this resolver can resolve the given request for the given building.
     * This resolver can only resolve requests for outpost buildings.
     * If the request is not for an outpost building, this method will return false.
     * If the world is the client side, this method will also return false.
     * If the building is null, this method will also return false.
     * 
     * @param manager the request manager for the colony.
     * @param requestToCheck the request to check if this resolver can resolve it.
     * @param requestingBuilding the building that made the request.
     * @return true if this resolver can resolve the request, false otherwise.
     */
    @Override
    public boolean canResolveForBuilding(@NotNull IRequestManager manager,
        @NotNull IRequest<? extends IDeliverable> requestToCheck,
        @NotNull AbstractBuilding requestingBuilding)
    {

        if (manager.getColony().getWorld().isClientSide)
        {
            return false;
        }

        if (requestingBuilding == null)
        {
            return false;
        }

        if (manager.getColony().getID() != requestingBuilding.getColony().getID())
        {
            return false;
        }

        return BuildingOutpost.isOutpostBuilding(requestingBuilding);
    }

    @Override
    public void resolveForBuilding(@NotNull IRequestManager manager,
        @NotNull IRequest<? extends IDeliverable> request,
        @NotNull AbstractBuilding building)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Requested outpost request resolved: {}", request.getLongDisplayString()));
        manager.updateRequestState(request.getId(), RequestState.RESOLVED);
    }
}
