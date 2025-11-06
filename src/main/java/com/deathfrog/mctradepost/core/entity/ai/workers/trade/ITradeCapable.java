package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.minecolonies.api.colony.buildings.IBuilding;

import net.minecraft.core.BlockPos;

public interface ITradeCapable extends IBuilding
{
    public BlockPos getRailStartPosition();
    public TrackConnectionResult getTrackConnectionResult(StationData stationData);
    public void markTradesDirty();
    public void onShipmentReceived(ExportData itemRecieved);
    public void onShipmentDelivered(ExportData itemShipped);
}
