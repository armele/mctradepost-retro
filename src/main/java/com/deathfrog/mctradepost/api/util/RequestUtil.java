package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.Log;

public class RequestUtil 
{
    /**
     * Retrieves all open requests from a given building.
     * 
     * @param building The building to retrieve the requests from.
     * @param originatorOnly Whether to only retrieve requests that were originally requested from this building, or to
     *          also retrieve requests that were propagated to this building.
     * @return A list of all open requests in the given building.
     */
    public static ImmutableList<IRequest<?>> getOpenRequestsFromBuilding(final IBuilding building, boolean originatorOnly)
    {
        final ArrayList<IRequest<?>> requests = Lists.newArrayList();

        if (building == null || building.getColony() == null || requests == null)
        {
            return ImmutableList.of();
        }

        final IRequestManager requestManager = building.getColony().getRequestManager();

        if (requestManager == null)
        {
            return ImmutableList.of();
        }

        try
        {
            final IPlayerRequestResolver resolver = requestManager.getPlayerResolver();
            final IRetryingRequestResolver retryingRequestResolver = requestManager.getRetryingRequestResolver();

            final Set<IToken<?>> requestTokens = new HashSet<>();
            requestTokens.addAll(resolver.getAllAssignedRequests());
            requestTokens.addAll(retryingRequestResolver.getAllAssignedRequests());

            for (final IToken<?> token : requestTokens)
            {
                IRequest<?> request = requestManager.getRequestForToken(token);

                if (originatorOnly)
                {
                    while (request != null && request.hasParent())
                    {
                        request = requestManager.getRequestForToken(request.getParent());
                    }
                }

                if (request != null && !requests.contains(request))
                {
                    requests.add(request);
                }
            }
        }
        catch (Exception e)
        {
            Log.getLogger().warn("Exception trying to retreive requests:", e);
            requestManager.reset();
            return ImmutableList.of();
        }

        return ImmutableList.copyOf(requests);
    }
}
