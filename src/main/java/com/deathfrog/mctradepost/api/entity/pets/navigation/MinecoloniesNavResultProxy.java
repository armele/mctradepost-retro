package com.deathfrog.mctradepost.api.entity.pets.navigation;

import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;

public record MinecoloniesNavResultProxy(PathResult<?> delegate)
    implements IPetNavResult
{
    @Override
    public boolean failedToReachDestination()
    {
        return delegate == null || delegate.failedToReachDestination();
    }
}
