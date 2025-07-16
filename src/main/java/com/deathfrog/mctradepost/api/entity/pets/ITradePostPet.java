package com.deathfrog.mctradepost.api.entity.pets;

import com.minecolonies.api.colony.buildings.IBuilding;

public interface ITradePostPet
{
    public void setColonyId(int colonyId);
    public int getColonyId();

    public void setBuilding(IBuilding building);
    public IBuilding getBuilding();

}
