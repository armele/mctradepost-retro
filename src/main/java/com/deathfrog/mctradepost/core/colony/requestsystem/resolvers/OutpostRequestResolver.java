package com.deathfrog.mctradepost.core.colony.requestsystem.resolvers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.OutpostShipmentTracking;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractBuildingDependentRequestResolver;
import com.minecolonies.core.util.DomumOrnamentumUtils;
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
        List<IToken<?>> prerequisiteRequests = getActiveChildRequests(manager, requestToCheck);

        if (tracking.getState() == OutpostOrderState.SHIPMENT_INITIATED
            || tracking.getState() == OutpostOrderState.RECEIVED
            || tracking.getState() == OutpostOrderState.READY_FOR_DELIVERY
            || tracking.getState() == OutpostOrderState.DELIVERED)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                () -> LOGGER.info("Request {} already advanced to {}. Reusing {} active prerequisite requests.",
                    requestToCheck.getId(),
                    tracking.getState(),
                    prerequisiteRequests.size()));
            return prerequisiteRequests;
        }


        if (requestToCheck.getRequest() instanceof IDeliverable deliverable)
        {
            IRequestManager requestManager = outpostBuilding.getColony().getRequestManager();
            final int totalRequested = deliverable.getCount();
            final Predicate<ItemStack> stationItemMatcher = createInventoryMatcher(requestToCheck, deliverable);

            if (stationItemMatcher == null)
            {
                return null;
            }

            int totalInStation = 0;
            int totalScheduled = 0;
            final List<Tuple<ItemStack, BlockPos>> stationInv = BuildingUtil.getMatchingItemStacksInBuilding(connectedStation, stationItemMatcher);

            for (final Tuple<ItemStack, BlockPos> stack : stationInv)
            {
                if (!stack.getA().isEmpty())
                {
                    totalInStation += stack.getA().getCount();

                    if (totalInStation <= totalRequested)
                    {
                        totalScheduled = totalScheduled + stack.getA().getCount();
                        Delivery delivery = new Delivery(connectedStation.getLocation(), outpostBuilding.getLocation(), stack.getA().copy(), 0);
                        IToken<?> deliveryRequestToken = findMatchingDeliveryChild(manager, requestToCheck, delivery);
                        if (deliveryRequestToken == null)
                        {
                            deliveryRequestToken = outpostBuilding.createRequest(delivery, true);
                        }
                        addPrerequisiteToken(requestManager, prerequisiteRequests, deliveryRequestToken);
                        final IToken<?> deliveryRequestTokenForLog = deliveryRequestToken;
                        final IRequest<?> deliveryRequest = getReusableRequest(requestManager, deliveryRequestToken);
                        if (deliveryRequest != null)
                        {
                            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created satisfied delivery request {} (state {}) - {}.", deliveryRequestTokenForLog, deliveryRequest.getState(), deliveryRequest.getLongDisplayString()));
                        }
                    }
                    else
                    {
                        ItemStack partialShipment = stack.getA().copyWithCount(totalRequested - totalScheduled);
                        Delivery delivery = new Delivery(connectedStation.getLocation(), outpostBuilding.getLocation(), partialShipment, 0);
                        IToken<?> deliveryRequestToken = findMatchingDeliveryChild(manager, requestToCheck, delivery);
                        
                        if (deliveryRequestToken == null)
                        {
                            deliveryRequestToken = outpostBuilding.createRequest(delivery, true);
                        }

                        addPrerequisiteToken(requestManager, prerequisiteRequests, deliveryRequestToken);
                        final IToken<?> deliveryRequestTokenForLog = deliveryRequestToken;
                        final IRequest<?> deliveryRequest = getReusableRequest(requestManager, deliveryRequestToken);
                        if (deliveryRequest != null)
                        {
                            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created partial delivery request {} (state {}) - {}.", deliveryRequestTokenForLog, deliveryRequest.getState(), deliveryRequest.getLongDisplayString()));
                        }
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
                IRequestable stationEchoRequest = createStationEchoRequest(requestToCheck, totalRemainingRequired);

                tracking.setState(OutpostOrderState.NEEDS_ITEM_FOR_SHIPPING);

                BlockPos stationPos = connectedStation.getPosition();
                BlockPos outpostPos = outpostBuilding.getPosition();
                IToken<?> finalShipmentToken = null;

                if (stationPos != null && outpostPos != null) 
                {
                    finalShipmentToken = findMatchingChildRequest(manager,
                                        requestToCheck,
                                        stationPos,
                                        outpostPos,
                                        NullnessBridge.assumeNonnull(stationEchoRequest));
                }

                if (finalShipmentToken == null)
                {
                    finalShipmentToken = connectedStation.createRequest(stationEchoRequest, true);
                }

                final IToken<?> finalShipmentTokenForLog = finalShipmentToken;
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Created final shipment request for {} (state {}) as {} for: {}", requestToCheck.getId(), requestToCheck.getState(), finalShipmentTokenForLog, stationEchoRequest.toString()));
                addPrerequisiteToken(requestManager, prerequisiteRequests, finalShipmentToken);
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
            IBuilding deliveryStartBuilding = outpostBuilding.getColony().getServerBuildingManager().getBuilding(deliveryStartPos);

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

    /**
     * Returns a list of active child tokens for the given request.
     * Child tokens which are no longer registered in the request manager or request handler
     * are ignored and removed from the parent request.
     * Child tokens which have been cancelled, failed, completed, or resolved are also ignored.
     * All other child tokens are returned in the list.
     * @param manager the request manager to validate against.
     * @param request the request to check for active child tokens.
     * @return a list of active child tokens for the given request.
     */    
    protected List<IToken<?>> getActiveChildRequests(@NotNull IRequestManager manager, @NotNull IRequest<?> request)
    {
        final List<IToken<?>> activeChildren = new ArrayList<>();

        for (IToken<?> childToken : request.getChildren())
        {
            if (childToken == null)
            {
                continue;
            }

            final IRequest<?> childRequest = getReusableRequest(manager, childToken);
            if (childRequest == null)
            {
                request.removeChild(childToken);
                TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                    () -> LOGGER.info("Skipping stale child token {} attached to request {}.", childToken, request.getId()));
                continue;
            }

            if (childRequest.getState() != RequestState.CANCELLED
                && childRequest.getState() != RequestState.FAILED
                && childRequest.getState() != RequestState.COMPLETED
                && childRequest.getState() != RequestState.RESOLVED)
            {
                activeChildren.add(childToken);
            }
        }

        return activeChildren;
    }

    /**
     * Returns a request only if MineColonies still considers it reusable through both the request manager and request handler layers.
     * Tokens that are manager-visible but no longer registered in the handler should be treated as stale and ignored.
     *
     * @param manager the request manager to validate against.
     * @param token the token to validate.
     * @return the reusable request, or null if the token is stale or terminal.
     */
    protected @Nullable IRequest<?> getReusableRequest(@NotNull IRequestManager manager, @Nullable IToken<?> token)
    {
        if (token == null)
        {
            return null;
        }

        final IRequest<?> request = manager.getRequestForToken(token);
        if (request == null)
        {
            return null;
        }

        if (request.getState() == RequestState.CANCELLED
            || request.getState() == RequestState.FAILED
            || request.getState() == RequestState.COMPLETED
            || request.getState() == RequestState.RESOLVED)
        {
            return null;
        }

        try
        {
            manager.getResolverForRequest(token);
        }
        catch (IllegalArgumentException e)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                () -> LOGGER.info("Request token {} is visible to the manager but no longer registered with the handler.", token, e));
            return null;
        }

        return request;
    }

    /**
     * Finds a child request that matches the given delivery requestable information.
     * @param manager the request manager to search
     * @param parentRequest the parent request to search children of
     * @param expectedDelivery the delivery requestable to search for
     * @return the token of the matching request, or null if none is found
     */
    protected IToken<?> findMatchingDeliveryChild(@Nonnull IRequestManager manager, @Nonnull IRequest<?> parentRequest, @Nonnull Delivery expectedDelivery)
    {
        BlockPos start = expectedDelivery.getStart().getInDimensionLocation();
        BlockPos target = expectedDelivery.getTarget().getInDimensionLocation();
        ItemStack stack = expectedDelivery.getStack();

        if (start == null || target == null || stack == null)
        {
            return null;
        }

        return findMatchingChildRequest(manager,
            parentRequest,
            start,
            target,
            expectedDelivery,
            stack,
            expectedDelivery.getStack().getCount());
    }

    /**
     * Finds a child request that matches the given requestable information.
     * @param manager the request manager to search
     * @param parentRequest the parent request to search children of
     * @param expectedStart the start location of the expected requestable
     * @param expectedTarget the target location of the expected requestable
     * @param expectedRequest the requestable to search for
     * @return the token of the matching request, or null if none is found
     */
    protected IToken<?> findMatchingChildRequest(@Nonnull IRequestManager manager,
        @Nonnull IRequest<?> parentRequest,
        @Nonnull BlockPos expectedStart,
        @Nonnull BlockPos expectedTarget,
        @Nonnull IRequestable expectedRequest)
    {
        final ItemStack expectedStack = extractRepresentativeStack(null, expectedRequest);

        if (expectedStack == null)
        {
            return null;
        }

        final int expectedCount = expectedRequest instanceof IDeliverable expectedDeliverable ? expectedDeliverable.getCount() : expectedStack.getCount();
        return findMatchingChildRequest(manager, parentRequest, expectedStart, expectedTarget, expectedRequest, expectedStack, expectedCount);
    }

    /**
     * Finds a child request that matches the given requestable information.
     * @param manager the request manager to search
     * @param parentRequest the parent request to search children of
     * @param expectedStart the start location of the expected requestable
     * @param expectedTarget the target location of the expected requestable
     * @param expectedRequest the requestable to search for
     * @param expectedStack the representative stack to search for
     * @param expectedCount the count of the expected stack
     * @return the token of the matching request, or null if none is found
     */
    protected IToken<?> findMatchingChildRequest(@Nonnull IRequestManager manager,
        @Nonnull IRequest<?> parentRequest,
        @Nonnull BlockPos expectedStart,
        @Nonnull BlockPos expectedTarget,
        @Nonnull IRequestable expectedRequest,
        @Nonnull ItemStack expectedStack,
        int expectedCount)
    {
        for (IToken<?> childToken : getActiveChildRequests(manager, parentRequest))
        {
            final IRequest<?> childRequest = getReusableRequest(manager, childToken);
            if (childRequest == null)
            {
                continue;
            }

            final BlockPos requesterPos = childRequest.getRequester() == null || childRequest.getRequester().getLocation() == null
                ? null
                : childRequest.getRequester().getLocation().getInDimensionLocation();
            if (requesterPos == null || !requesterPos.equals(expectedStart))
            {
                continue;
            }

            if (childRequest.getRequest() instanceof Delivery existingDelivery)
            {
                ItemStack stack = existingDelivery.getStack();

                if (stack == null) continue;

                if (existingDelivery.getStart().getInDimensionLocation().equals(expectedStart)
                    && existingDelivery.getTarget().getInDimensionLocation().equals(expectedTarget)
                    && ItemStack.isSameItemSameComponents(stack, expectedStack)
                    && existingDelivery.getStack().getCount() == expectedCount)
                {
                    return childToken;
                }
                continue;
            }

            if (childRequest.getRequest() instanceof IDeliverable existingDeliverable
                && existingDeliverable.getCount() == expectedCount)
            {
                final ItemStack existingStack = extractRepresentativeStack(childRequest, childRequest.getRequest());
                if (expectedStack.isEmpty())
                {
                    if (childRequest.getRequest().getClass().equals(expectedRequest.getClass()))
                    {
                        return childToken;
                    }
                }
                else if (!existingStack.isEmpty() && ItemStack.isSameItemSameComponents(existingStack, expectedStack))
                {
                    return childToken;
                }
            }
        }

        return null;
    }

    /**
     * Creates a new StationEchoRequest based on the given request to check, expecting the given count of items.
     * If the request to check is for a stack of items, a new Stack requestable is created with the same items and count.
     * If the request to check is not for a stack of items, the original request is returned with the count updated.
     * @param requestToCheck the request to check
     * @param expectedCount the expected count of items
     * @return the new StationEchoRequest
     */
    protected IRequestable createStationEchoRequest(@NotNull IRequest<? extends IDeliverable> requestToCheck, int expectedCount)
    {
        final IDeliverable deliverableCopy = requestToCheck.getRequest().copyWithCount(expectedCount);
        final ItemStack requestedStack = extractRepresentativeStack(requestToCheck, requestToCheck.getRequest());

        if (!requestedStack.isEmpty() && DomumOrnamentumUtils.getBlock(requestedStack) != null)
        {
            return new Stack(new ItemStorage(requestedStack.copyWithCount(expectedCount), expectedCount));
        }

        return deliverableCopy;
    }

    /**
     * Creates a predicate that matches items in an inventory against the given request and deliverable.
     * If the request is for a stack of items, a predicate is created that matches the exact items and count.
     * If the request is not for a stack of items, the deliverable's matches predicate is used instead.
     * @param requestToCheck the request to check
     * @param deliverable the deliverable to create the predicate for
     * @return the created predicate
     */
    protected Predicate<ItemStack> createInventoryMatcher(@NotNull IRequest<? extends IDeliverable> requestToCheck, @NotNull IDeliverable deliverable)
    {
        final ItemStack requestedStack = extractRepresentativeStack(requestToCheck, requestToCheck.getRequest());

        if (!requestedStack.isEmpty() && DomumOrnamentumUtils.getBlock(requestedStack) != null)
        {
            return stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, requestedStack);
        }

        return deliverable::matches;
    }

    /**
     * Retrieves the representative ItemStack for the given request and requestable.
     * If the request is not null, it first tries to extract the requested stack from the request.
     * If the requested stack is empty or the request is null, it then tries to extract the stack from the requestable.
     * If the requestable is a Delivery, the stack is extracted directly from the delivery.
     * If the requestable is a Stack, the stack is extracted directly from the stack.
     * Otherwise, an empty ItemStack is returned.
     * @param request the request to extract the stack from, or null
     * @param requestable the requestable to extract the stack from
     * @return the representative ItemStack for the given request and requestable, or an empty ItemStack if none is found
     */
    protected ItemStack extractRepresentativeStack(@Nullable IRequest<?> request, @NotNull IRequestable requestable)
    {
        if (request != null)
        {
            final ItemStack requestedStack = DomumOrnamentumUtils.getRequestedStack(request);
            if (!requestedStack.isEmpty())
            {
                return requestedStack;
            }
        }

        return extractRepresentativeStack(requestable);
    }

    /**
     * Retrieves the representative ItemStack for the given requestable.
     * If the requestable is a Delivery, the stack is extracted directly from the delivery.
     * If the requestable is a Stack, the stack is extracted directly from the stack.
     * Otherwise, an empty ItemStack is returned.
     * @param requestable the requestable to extract the stack from
     * @return the representative ItemStack for the given requestable, or an empty ItemStack if none is found
     */
    protected ItemStack extractRepresentativeStack(@NotNull IRequestable requestable)
    {
        if (requestable instanceof Delivery delivery)
        {
            return delivery.getStack();
        }

        if (requestable instanceof Stack stack)
        {
            return stack.getStack();
        }

        return ItemStack.EMPTY;
    }

    /**
     * Adds the given prerequisite token to the list of prerequisite requests if it is not already present and is still reusable.
     * If the token is null or is already present in the list, this function does nothing.
     * If the token is no longer reusable by the time the parent request is resolved, a message is traced but the token is not added to the list.
     * @param manager the request manager containing the token
     * @param prerequisiteRequests the list of prerequisite requests to add the token to
     * @param token the token to add to the list, or null to do nothing
     */
    protected void addPrerequisiteToken(@NotNull IRequestManager manager, @NotNull List<IToken<?>> prerequisiteRequests, @Nullable IToken<?> token)
    {
        if (token == null || prerequisiteRequests.contains(token))
        {
            return;
        }

        if (getReusableRequest(manager, token) == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                () -> LOGGER.info("Skipping prerequisite token {} because it is no longer reusable by the time the parent request is resolved.", token));
            return;
        }

        prerequisiteRequests.add(token);
    }
}
