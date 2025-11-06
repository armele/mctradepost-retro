package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;

public class OutpostShipmentTracking
{
    protected OutpostOrderState state;


    public OutpostShipmentTracking(OutpostOrderState state)
    {

        this.state = state;
    }

    public OutpostOrderState getState()
    {
        return state;
    }

    public void setState(OutpostOrderState state)
    {
        this.state = state;
    }

}