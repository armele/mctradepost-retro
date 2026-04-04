package com.deathfrog.mctradepost.core.colony.requestsystem;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.ItemHandlerHelpers;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.crafting.ItemStorage;
import com.mojang.logging.LogUtils;

import net.neoforged.neoforge.items.IItemHandler;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST_REQUESTS;

/**
 * "Does this building's inventory satisfy the given request?"
 */
public interface IRequestSatisfaction extends IBuilding
{
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * True if the building has enough of something that matches the request's criteria.
     */
    default ItemStorage inventorySatisfiesRequest(@NotNull IRequest<? extends IRequestable> request, boolean allowPartial)
    {
        final String requestDescription = safeDescribeRequest(request);
        final String buildingDescription = safeDescribeBuilding();

        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
            () -> LOGGER.info("inventorySatisfiesRequest for {} from building {}.", requestDescription, buildingDescription));

        try
        {
            IItemHandler itemHandler = this.getItemHandlerCap();

            if (itemHandler == null) return null;

            return ItemHandlerHelpers.inventorySatisfiesRequest(request, itemHandler, allowPartial, this.getColony().getWorld());

        } catch (Exception e)
        {
            // Minecolonies can throw an NPE getting the item handler capacity - catch it and handle it (slightly) more gracefully.
            
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS,
                () -> LOGGER.warn("Error retrieving item handler from colony {}, building {} for request {}.",
                    safeDescribeColony(),
                    buildingDescription,
                    requestDescription,
                    e));
            return null;
        }

    }

    private String safeDescribeRequest(@NotNull final IRequest<? extends IRequestable> request)
    {
        try
        {
            return request.getShortDisplayString().getString();
        }
        catch (Exception e)
        {
            return "<request description unavailable>";
        }
    }

    private String safeDescribeBuilding()
    {
        try
        {
            return this.getBuildingDisplayName();
        }
        catch (Exception e)
        {
            return "<building description unavailable>";
        }
    }

    private String safeDescribeColony()
    {
        try
        {
            return String.valueOf(this.getColony().getID());
        }
        catch (Exception e)
        {
            return "<colony unavailable>";
        }
    }
}
