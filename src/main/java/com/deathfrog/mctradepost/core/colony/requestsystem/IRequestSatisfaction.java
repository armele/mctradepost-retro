package com.deathfrog.mctradepost.core.colony.requestsystem;

import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.api.util.ItemHandlerHelpers;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.crafting.ItemStorage;

/**
 * "Does this building's inventory satisfy the given request?"
 */
public interface IRequestSatisfaction extends IBuilding
{

    /**
     * True if the building has enough of something that matches the request's criteria.
     */
    default ItemStorage inventorySatisfiesRequest(@NotNull IRequest<? extends IRequestable> request, boolean allowPartial)
    {
        return ItemHandlerHelpers.inventorySatisfiesRequest(request, getItemHandlerCap(), allowPartial);
    }
}
