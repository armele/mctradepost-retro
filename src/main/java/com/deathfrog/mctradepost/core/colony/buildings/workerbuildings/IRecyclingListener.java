package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.List;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;

public interface IRecyclingListener 
{
    public void onFinishedRecycling(List<ItemStorage> output, IBuilding recyclingBuilding);    
}
