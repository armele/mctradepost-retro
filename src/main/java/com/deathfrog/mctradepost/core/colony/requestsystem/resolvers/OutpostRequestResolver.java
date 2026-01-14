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
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
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
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Checking OutpostRequestResolver suitability metric for request {} - {}  (state {}) .", request.getId(), request.getShortDisplayString(), request.getState()));
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
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("This request {}  (state {})  has been assigned to the outpost: {}", request.getId(), request.getState(), request.getLongDisplayString()));
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
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Outpost assigned request {} (state {}) is being cancelled: {}", request.getId(), request.getState(), request.getLongDisplayString()));
    }

    @Override
    public void onAssignedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<? extends IDeliverable> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Outpost assigned request {} (state {}) cancelled: {}", request.getId(), request.getState(), request.getLongDisplayString()));
    }

    @Override
    public void onRequestedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<?> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Requested outpost request {} (state {}) cancelled: {}", request.getId(), request.getState(), request.getLongDisplayString()));
    }

    @Override
    public void onRequestedRequestComplete(@NotNull IRequestManager arg0, @NotNull IRequest<?> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Requested outpost request {} (state {}) complete: {}", request.getId(), request.getState(), request.getLongDisplayString()));
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
            IRequest<?> request = manager.getRequestForToken(token);

            if (request == null)
            {
                return Optional.empty();
            }

            return Optional.ofNullable(manager.getColony().getRequesterBuildingForPosition(request.getRequester().getLocation().getInDimensionLocation()));
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

        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Attempting to resolve outpost request from building {}: {} (state {}) - {}", 
            requestingBuilding.getPosition(), requestToCheck.getId(), requestToCheck.getState(), requestToCheck.getLongDisplayString()));

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

        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("requesting building {} has an associated outpost building {} connected to {}", 
            requestingBuilding.getBuildingDisplayName(), outpostBuilding.getBuildingDisplayName(), connectedStation.getBuildingDisplayName()));

        OutpostShipmentTracking tracking = ((BuildingOutpost) outpostBuilding).trackingForRequest(requestToCheck);
        List<IToken<?>> prerequisiteRequests = new ArrayList<>();


        if (requestToCheck.getRequest() instanceof IDeliverable deliverable)
        {
            IRequestManager requestManager = outpostBuilding.getColony().getRequestManager();
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
                        IRequest<?> deliveryRequest = requestManager.getRequestForToken(deliveryRequestToken);
                        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created satisfied delivery request {} (state {}) - {}.", deliveryRequestToken, deliveryRequest.getState(), deliveryRequest.getLongDisplayString()));
                    }
                    else
                    {
                        ItemStack partialShipment = stack.getA().copyWithCount(totalRequested - totalScheduled);
                        Delivery delivery = new Delivery(connectedStation.getLocation(), outpostBuilding.getLocation(), partialShipment, 0);
                        IToken<?> deliveryRequestToken = outpostBuilding.createRequest(delivery, true);
                        prerequisiteRequests.add(deliveryRequestToken);
                        IRequest<?> deliveryRequest = requestManager.getRequestForToken(deliveryRequestToken);
                        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created partial delivery request {} (state {}) - {}.", deliveryRequestToken, deliveryRequest.getState(), deliveryRequest.getLongDisplayString()));
                        break;
                    }
                }
            }
            
            final int totalRemainingRequired = Math.max(totalRequested - totalInStation, 0);

            final int stationCountForLog = totalInStation;
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("This is deliverable request {} (state {}) of type {} with {} items in the station, and {} items remaining to be collected for shipment: {}", 
                requestToCheck.getId(),
                requestToCheck.getState(),
                deliverable.getClass().getName(),    
                stationCountForLog, 
                totalRemainingRequired,
                requestToCheck.getLongDisplayString()));

            if (totalRemainingRequired > 0)
            {
                IDeliverable deliverableCopy = ((IDeliverable) requestToCheck.getRequest()).copyWithCount(totalRemainingRequired);
                tracking.setState(OutpostOrderState.NEEDS_ITEM_FOR_SHIPPING);
                IToken<?> finalShipmentToken = connectedStation.createRequest(deliverableCopy, true);
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created final shipment request for {} (state {}) as {} for: {}", requestToCheck.getId(), requestToCheck.getState(), finalShipmentToken, deliverableCopy.toString()));
                prerequisiteRequests.add(finalShipmentToken);
            }
            else
            {
                // We have everything we need, but the station master still needs to make the shipment(s).
                tracking.setState(OutpostOrderState.ITEM_READY_TO_SHIP);
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Station has everything needed to fulfil request {} (state {}): {}", requestToCheck.getId(), requestToCheck.getState(), requestToCheck.getLongDisplayString()));
            }
        }

        return prerequisiteRequests;
    }


    /**
     * Determines if this resolver can resolve the given request.
     * This resolver can resolve requests if the requesting building is an outpost (or outpost child building) and the outpost is connected to this building.
     * If the request is a delivery, it also checks if the start position of the delivery is not a station.
     * @param manager the request manager for the colony.
     * @param requestToCheck the request to check if this resolver can resolve it.
     * @return true if this resolver can resolve the request, false otherwise.
     */
   @Override
   public boolean canResolveRequest(@NotNull IRequestManager manager, IRequest<? extends IDeliverable> requestToCheck) 
   {
        final AbstractBuilding requestingBuilding =
            getBuilding(manager, requestToCheck.getId())
                .filter(AbstractBuilding.class::isInstance)
                .map(AbstractBuilding.class::cast)
                .orElse(null);

        ILocation requesterLocation = requestToCheck.getRequester().getLocation();

        if (requesterLocation == null || requestingBuilding == null)
        {
            return false;
        }

        IBuilding outpostBuilding = BuildingOutpost.toOutpostBuilding(requestingBuilding);

        if (outpostBuilding == null)
        {
            return false;
        }

        // We can't handle deliveries from the station - that's for the TrainRequestResolver to take.
        if (requestToCheck.getRequest() instanceof Delivery delivery)
        {
            BlockPos deliveryStartPos = delivery.getStart().getInDimensionLocation();
            IBuilding deliveryStartBuilding = outpostBuilding.getColony().getBuildingManager().getBuilding(deliveryStartPos);

            if (deliveryStartBuilding instanceof BuildingStation)
            {
                return false;
            }

        }

        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () ->LOGGER.info("Outpost can resolve for requesting building {} at {} for {} (state {}).", 
            requestingBuilding == null ? "null" : requestingBuilding.getBuildingDisplayName(), 
            requestingBuilding == null ? "null" : requestingBuilding.getLocation(), 
            requestToCheck.getId(), 
            requestToCheck.getState()));

        return (outpostBuilding.getLocation().equals(this.getLocation()));
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

        boolean isOutpostBuilding = BuildingOutpost.isOutpostBuilding(requestingBuilding);

        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Requesting building {} at {} for {} (state {}) - is outpost? {}", 
            requestingBuilding.getBuildingDisplayName(), requestingBuilding.getLocation(), requestToCheck.getId(), requestToCheck.getState(), isOutpostBuilding));

        return isOutpostBuilding;
    }

    /**
     * Resolves the given request for the given building.
     * This method is called by the outpost request resolver when it has finished resolving the request.
     * It marks the request as resolved and logs the resolution of the request.
     * @param manager the request manager for the colony.
     * @param request the request to resolve.
     * @param building the building that made the request.
     */
    @Override
    public void resolveForBuilding(@NotNull IRequestManager manager,
        @NotNull IRequest<? extends IDeliverable> request,
        @NotNull AbstractBuilding building)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Requested outpost request {} (state {}) resolved: {}", request.getId(), request.getState(), request.getLongDisplayString()));
        manager.updateRequestState(request.getId(), RequestState.RESOLVED);
    }
}
