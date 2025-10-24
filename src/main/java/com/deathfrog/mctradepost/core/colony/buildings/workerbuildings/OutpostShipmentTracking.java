package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;
import net.minecraft.core.BlockPos;

public class OutpostShipmentTracking
{
    protected BlockPos outpostDestination;
    protected OutpostOrderState state;
    protected ExportData exportData = null;

    public OutpostShipmentTracking(BlockPos outpostDestination, ExportData exportData, OutpostOrderState state)
    {
        this.outpostDestination = outpostDestination;
        this.exportData = exportData;
        this.state = state;
    }

    public ExportData getExportData()
    {
        return exportData;
    }

    public void setAssociatedExportData(ExportData associatedExportData)
    {
        this.exportData = associatedExportData;
    }

    public OutpostOrderState getState()
    {
        return state;
    }

    public void setState(OutpostOrderState state)
    {
        this.state = state;
    }

    public BlockPos getOutpostDestination()
    {
        return outpostDestination;
    }

    public void setOutpostDestination(BlockPos outpostDestination)
    {
        this.outpostDestination = outpostDestination;
    }

}