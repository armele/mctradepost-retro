package com.deathfrog.mctradepost.api.util;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests.ItemStackRequest;
import com.mojang.logging.LogUtils;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_AUTOMINT;

public class RequestUtil 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Counts the number of coins needed to fulfill open requests in the colony.
     * 
     * @return the number of coins needed
     */
    public static int matchingRequestsInColony(@Nonnull IColony colony, @Nonnull Item searchItem)
    {
        int coinsNeeded = 0;

        ItemStack searchStack = new ItemStack(searchItem, 1);

        final IRequestManager requestManager = colony.getRequestManager();
        final IPlayerRequestResolver resolver = requestManager.getPlayerResolver();
        final IRetryingRequestResolver retryingRequestResolver = requestManager.getRetryingRequestResolver();

        final Set<IToken<?>> requestTokens = new HashSet<>();
        requestTokens.addAll(resolver.getAllAssignedRequests());

        final int playerRequests = requestTokens.size();

        requestTokens.addAll(retryingRequestResolver.getAllAssignedRequests());

        final int retryingRequests = requestTokens.size() - playerRequests;

        TraceUtils.dynamicTrace(TRACE_AUTOMINT,
            () -> LOGGER.info("Colony {} Shopkeeper: Colony has {} current player-resolved requests and {} retrying requests.", colony.getID(), playerRequests, retryingRequests));

        for (IToken<?> token : requestTokens)
        {
            final IRequest<?> request = requestManager.getRequestForToken(token);

            if (request instanceof ItemStackRequest itemRequest)
            {
                TraceUtils.dynamicTrace(TRACE_AUTOMINT,
                    () -> LOGGER.info("Colony {} Shopkeeper: inspecting request: {}", colony.getID(), request.getShortDisplayString()));

                if (itemRequest.getRequest().matches(searchStack))
                {
                    final int addToNeed = itemRequest.getRequest().getCount();
                    TraceUtils.dynamicTrace(TRACE_AUTOMINT,
                        () -> LOGGER.info("Colony {} Shopkeeper: adding {} to needed coin coint.", colony.getID(), addToNeed));
                    coinsNeeded = coinsNeeded + addToNeed;
                }
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_AUTOMINT,
                    () -> LOGGER.info("Colony {} Shopkeeper: ignoring request: {} of type {}", colony.getID(), request.getShortDisplayString(), request.getClass().getSimpleName()));
            }
        }

        return coinsNeeded;
    }

    /**
     * Counts items in pending delivery requests from a particular building.
     * Requests waiting for a resolver and requests already queued at a warehouse
     * are both included.
     *
     * @param colony    colony whose requests should be inspected
     * @param source    building from which the deliveries originate
     * @param searchItem item being delivered
     * @return number of matching items pending delivery
     */
    public static int matchingPendingDeliveries(
        @Nonnull final IColony colony,
        @Nonnull final IBuilding source,
        @Nonnull final Item searchItem)
    {
        final IRequestManager requestManager = colony.getRequestManager();
        final Set<IToken<?>> requestTokens = new HashSet<>(
            requestManager.getRetryingRequestResolver().getAllAssignedRequests());

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof IWareHouse warehouse)
            {
                requestTokens.addAll(
                    warehouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE).getMutableRequestList());
            }
        }

        int pendingCount = 0;
        for (final IToken<?> token : requestTokens)
        {
            final IRequest<?> request;
            try
            {
                request = requestManager.getRequestForToken(token);
            }
            catch (final IllegalArgumentException exception)
            {
                // A request may finish between collecting its queue token and resolving it.
                continue;
            }

            if (request.getRequest() instanceof Delivery delivery
                && delivery.getStart().equals(source.getLocation())
                && delivery.getStack().is(searchItem))
            {
                pendingCount += delivery.getStack().getCount();
            }
        }

        return pendingCount;
    }
}
