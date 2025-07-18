package com.deathfrog.mctradepost.api.entity.pets;

import com.minecolonies.api.colony.buildings.IBuilding;

public interface ITradePostPet
{
    public void setTrainerBuilding(IBuilding building);
    public IBuilding getTrainerBuilding();
    public void setWorkBuilding(IBuilding building);
    public IBuilding getWorkBuilding();
    public String getAnimalType();
    public PetData getPetData();
}
