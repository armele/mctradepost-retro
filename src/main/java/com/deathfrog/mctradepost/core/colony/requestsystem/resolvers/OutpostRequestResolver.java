package com.deathfrog.mctradepost.core.colony.requestsystem.resolvers;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.requesters.IBuildingBasedRequester;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractRequestResolver;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;

/*
 * Modelled after DeliverymenRequestResolver
 */
public class OutpostRequestResolver extends AbstractRequestResolver<IRequestable> implements IBuildingBasedRequester
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

    @Nullable
    @Override
    public List<IToken<?>> attemptResolveRequest(@NotNull final IRequestManager manager, @NotNull final IRequest<? extends IRequestable> requestToCheck)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Attempting to resolve outpost request: {}", requestToCheck));

        IBuilding requestingBuilding = buildingForRequest(manager, requestToCheck);

        if (requestingBuilding == null)
        {
            return null;
        }

        boolean qualifies = BuildingOutpost.isOutpostBuilding(requestingBuilding);

        if (manager.getColony().getWorld().isClientSide || !qualifies)
        {
            return null;
        }

        return Lists.newArrayList();
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
        IBuilding requestingBuilding = buildingForRequest(manager, request);

        if (requestingBuilding == null)
        {
            return Component.translatable("com.mctradepost.building.outpost");
        }

        return Component.translatable(requestingBuilding.getBuildingDisplayName());
    }

    @Override
    public boolean canResolveRequest(@NotNull final IRequestManager manager, final IRequest<? extends IRequestable> requestToCheck)
    {

        if (manager.getColony().getWorld().isClientSide)
        {
            return false;
        }

        IBuilding requestingBuilding = buildingForRequest(manager, requestToCheck);

        if (requestingBuilding == null)
        {
            return false;
        }

        return BuildingOutpost.isOutpostBuilding(requestingBuilding);
    }
    
    /**
     * Called by the manager given to indicate that this request has been assigned to you.
     *
     * @param manager    The systems manager.
     * @param request    The request assigned.
     * @param simulation True when simulating.
     */
    public void onRequestAssigned(@NotNull final IRequestManager manager, @NotNull final IRequest<? extends IRequestable> request, boolean simulation)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("This request has been assigned to the outpost: {}", request.getLongDisplayString()));
    }

    @Override
    public boolean isValid()
    {
        // Always valid
        return true;
    }
    @Override
    public void onAssignedRequestBeingCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<? extends IRequestable> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Outpost assigned request being cancelled: {}", request.getLongDisplayString()));
    }

    @Override
    public void onAssignedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<? extends IRequestable> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Outpost assigned request cancelled: {}", request.getLongDisplayString()));
    }

    @Override
    public void resolveRequest(@NotNull IRequestManager arg0, @NotNull IRequest<? extends IRequestable> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Resolving outpost request.: {}", request.getLongDisplayString()));
    }

    @Override
    public void onRequestedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<?> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Requested outpost request cancelled: {}", request.getLongDisplayString()));
    }

    @Override
    public void onRequestedRequestComplete(@NotNull IRequestManager arg0, @NotNull IRequest<?> request)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Requested outpost request complete: {}", request.getLongDisplayString()));
    }

    @Override
    public TypeToken<IRequestable> getRequestType()
    {
        // LOGGER.info("Checking outpost request type.");
        return TypeConstants.REQUESTABLE;
    }


    /**
     * Returns the building associated with the given resolver token.
     * If the request manager is null or the resolver is null, returns null.
     * Otherwise, returns the building associated with the location of the given resolver token.
     * @param token The token of the resolver to find the building for.
     * @return The building associated with the given resolver token, or null if the request manager or resolver is null.
     */
    public IBuilding buildingForRequest(@NotNull final IRequestManager manager, @NotNull final IRequest<?> request)
    {
        IBuilding requestingBuilding = null;
        
        if (manager.getColony() == null)
        {
            LOGGER.warn("buildingForRequest: null colony for manager");
            return null;
        }
        
        if (manager.getColony().getBuildingManager() == null)
        {
            LOGGER.warn("buildingForRequest has a null building manager.");
            return null;
        }
        
        if (request.getRequester() == null)
        {
            LOGGER.warn("buildingForRequest has a null requester.");
            return null;
        }
        
        if (request.getRequester().getLocation() == null)
        {
            LOGGER.warn("buildingForRequest has a null requester location.");
            return null;
        }
        
        if (request.getRequester().getLocation().getInDimensionLocation() == null)
        {
            LOGGER.warn("buildingForRequest has a in-dimension location.");
            return null;
        }


        requestingBuilding = manager.getColony().getBuildingManager().getBuilding(request.getRequester().getLocation().getInDimensionLocation());

        return requestingBuilding;
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
     * Clones a requestable by serializing it through the factory controller if it supports requestables,
     * or manually if it doesn't.
     * @param manager the request manager from which to clone the requestable
     * @param src the source requestable to clone
     * @return the cloned requestable, or null if the type is unsupported
     */
    public static @Nullable IRequestable cloneRequestable(IRequestManager manager, IRequestable src)
    {

        // Common manual cases
        try
        {
            if (src instanceof Stack s)
            {
                IDeliverable clone = s.copyWithCount(s.getCount());
                return clone;
            } 
            else if (src instanceof Delivery d)
            { 
                // Delivery usually wraps a Stack; rebuild similarly
                ItemStack stackCopy = d.getStack().copy();
                return new Delivery(d.getStart(), d.getTarget(), stackCopy, d.getPriority());
            } 
            else if (src instanceof StackList sl) 
            {
                StackList clone = (StackList) sl.copyWithCount(sl.getCount());
                return clone;
            }
            else if (src instanceof Tool tr)
            {
                IDeliverable clone = tr.copyWithCount(0);
                return clone;
            }
            else
            {
                LOGGER.warn("Unknown requestable type: {}", src.getClass().getName());
            }
            // TODO: Add other requestables as a fallback as needed...
        }
        catch (Throwable t)
        {
            LOGGER.error("cloneRequestable failed (manual): {}", t.getMessage());
        }
        
        return null;
    }
}
