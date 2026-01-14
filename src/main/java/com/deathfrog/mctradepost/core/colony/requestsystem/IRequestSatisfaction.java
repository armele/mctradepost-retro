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
        TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("inventorySatisfiesRequest for {} from building {}.", request.getShortDisplayString(), this.getBuildingDisplayName()));

        return ItemHandlerHelpers.inventorySatisfiesRequest(request, getItemHandlerCap(), allowPartial, this.getColony().getWorld());
    }
}
