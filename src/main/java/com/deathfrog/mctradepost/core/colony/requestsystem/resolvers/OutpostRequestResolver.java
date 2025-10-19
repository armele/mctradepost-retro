package com.deathfrog.mctradepost.core.colony.requestsystem.resolvers;

import java.util.List;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractRequestResolver;
import com.mojang.logging.LogUtils;

/*
 * Modelled after DeliverymenRequestResolver
 */
public class OutpostRequestResolver<R extends IRequestable> extends AbstractRequestResolver<R>
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
    public List<IToken<?>> attemptResolveRequest(@NotNull final IRequestManager manager, @NotNull final IRequest<? extends R> requestToCheck)
    {
        LOGGER.info("Attempting to resolve outpost request.");

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

    @Override
    public boolean canResolveRequest(@NotNull final IRequestManager manager, final IRequest<? extends R> requestToCheck)
    {
        LOGGER.info("Determining if outpost can resolve request.");

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
    
    @Override
    public boolean isValid()
    {
        // Always valid
        return true;
    }
    @Override
    public void onAssignedRequestBeingCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<? extends R> arg1)
    {
        // TODO: Decide if logic is required.
    }

    @Override
    public void onAssignedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<? extends R> arg1)
    {
        // TODO: Decide if logic is required.
    }

    @Override
    public void resolveRequest(@NotNull IRequestManager arg0, @NotNull IRequest<? extends R> arg1)
    {
        // TODO: have this method place the ExportData object in the train station module.
    }

    @Override
    public void onRequestedRequestCancelled(@NotNull IRequestManager arg0, @NotNull IRequest<?> arg1)
    {
        // TODO: Have this remove the ExportData object from the train station module.
    }

    @Override
    public void onRequestedRequestComplete(@NotNull IRequestManager arg0, @NotNull IRequest<?> arg1)
    {
        // TODO: Record stats?
    }

    @Override
    public TypeToken<R> getRequestType()
    {
        LOGGER.info("Checking outpost request type.");
        return (TypeToken<R>) TypeConstants.REQUESTABLE;
    }


    /**
     * Returns the building associated with the given resolver token.
     * If the request manager is null or the resolver is null, returns null.
     * Otherwise, returns the building associated with the location of the given resolver token.
     * @param token The token of the resolver to find the building for.
     * @return The building associated with the given resolver token, or null if the request manager or resolver is null.
     */
    public IBuilding buildingForRequest(@NotNull final IRequestManager manager, @NotNull final IRequest<? extends R> request)
    {
        IBuilding requestingBuilding = null;
        
        if (manager.getColony() == null || manager.getColony().getBuildingManager() == null || request.getRequester() == null || request.getRequester().getLocation() == null || request.getRequester().getLocation().getInDimensionLocation() == null)
        {
            return null;
        }
        
        requestingBuilding = manager.getColony().getBuildingManager().getBuilding(request.getRequester().getLocation().getInDimensionLocation());

        return requestingBuilding;
    }
}
