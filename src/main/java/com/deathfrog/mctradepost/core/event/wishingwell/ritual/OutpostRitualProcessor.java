package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.core.blocks.BlockOutpostMarker;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualState.RitualResult;
import com.deathfrog.mctradepost.item.OutpostClaimMarkerItem;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.util.ChunkDataHelper;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class OutpostRitualProcessor 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Processes an outpost ritual at the specified BlockPos within the ServerLevel. This ritual sets up an outpost claim marker at the
     * specified BlockPos and connects it to the nearest station. It handles all validations to ensure outposts are in valid locations.
     * 
     * @param level       the ServerLevel where the ritual is taking place
     * @param pos         the BlockPos of the wishing well structure
     * @param ritual      the ritual definition containing the target item and companion item count
     * @param state       the current state of the ritual, including companion item count
     * @param marketplace the BuildingMarketplace containing the Colony and its buildings
     * @return RitualResult indicating whether the ritual was completed, needed ingredients, or failed due to an error
     **/
    public static RitualResult processRitualOutpost(@Nonnull BuildingMarketplace marketplace,
        @Nonnull BlockPos pos,
        RitualDefinitionHelper ritual,
        RitualState state)
    {
        ServerLevel level = (ServerLevel) marketplace.getColony().getWorld();

        if (level == null)
        {
            return RitualResult.FAILED;
        }

        if (state.getCompanionCount() < ritual.companionItemCount())
        {
            return RitualResult.NEEDS_INGREDIENTS;
        }

        if (!MCTPConfig.outpostEnabled.get())
        {
            LOGGER.warn("The Outpost is disabled on this server.", state.getCompanionCount());
            MessageUtils.format("The Outpost is disabled on this server.").sendTo(marketplace.getColony()).forAllPlayers();
            return RitualResult.FAILED;
        }

        IBuilding outpostBuilding =
            marketplace.getColony().getServerBuildingManager().getFirstBuildingMatching(b -> b.getBuildingType() == ModBuildings.outpost);
        if (outpostBuilding != null)
        {
            LOGGER.warn("Only one outpost may be claimed per colony.", state.getCompanionCount());
            MessageUtils.format("Only one outpost may be claimed per colony.").sendTo(marketplace.getColony()).forAllPlayers();
            return RitualResult.FAILED;
        }

        double outpostClaimLevel =
            marketplace.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.OUTPOST_CLAIM);

        if (outpostClaimLevel == 0)
        {
            MessageUtils.format("To wish for an outpost claim you must first complete the research.")
                .sendTo(marketplace.getColony())
                .forAllPlayers();
            return RitualResult.FAILED;
        }

        try
        {
            if (state.getCompanionCount() != 1)
            {
                LOGGER.warn(
                    "Outpost ritual called with incorrect number of companion items ({}). One and only one outpost claim marker is required.",
                    state.getCompanionCount());
                MessageUtils.format("One and only one outpost claim marker is required.")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }

            ItemStack companionItem = state.companionItems.get(0).getItem();

            if (!(companionItem.getItem() instanceof OutpostClaimMarkerItem))
            {
                LOGGER.warn("Outpost ritual called with unrecognized companion item.");
                MessageUtils.format("Outpost ritual called with unrecognized companion item.")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }

            BlockPos claimLocation = OutpostClaimMarkerItem.getLinkedBlockPos(companionItem);

            if (claimLocation == null || BlockPos.ZERO.equals(claimLocation))
            {
                LOGGER.warn("Outpost ritual called with unset claim location.");
                MessageUtils.format("Outpost ritual called with unset claim location.")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }

            boolean connected = false;
            Colony colony = (Colony) marketplace.getColony();

            BlockPos colonyCenter = colony.getCenter();
            int maxDistance = MCTPConfig.maxDistance.get();

            if (colonyCenter.distSqr(claimLocation) > (maxDistance * maxDistance))
            {
                LOGGER.warn("Outpost ritual called distance greater than max distance.");
                MessageUtils.format("Outpost distance is too large - the maximum is " + maxDistance + ".")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }

            List<BuildingStation> stations = new ArrayList<>();

            Collection<IBuilding> buildings = colony.getServerBuildingManager().getBuildings().values();
            for (IBuilding building : buildings)
            {
                if (building instanceof BuildingStation station)
                {
                    stations.add(station);
                    TrackConnectionResult result =
                        TrackPathConnection.arePointsConnectedByTracks((ServerLevel) marketplace.getColony().getWorld(),
                            claimLocation,
                            station.getRailStartPosition(),
                            true);

                    if (result.isConnected())
                    {
                        connected = true;
                        break;
                    }
                }
            }

            if (stations.isEmpty())
            {
                LOGGER.warn("A station is required for the outpost ritual.");
                MessageUtils.format("A station is required for the outpost ritual.").sendTo(marketplace.getColony()).forAllPlayers();
                return RitualResult.FAILED;
            }

            if (connected)
            {
                final int range = 1;
                boolean canClaim = ChunkDataHelper.canClaimChunksInRange(colony.getWorld(), claimLocation, range + 1);

                if (canClaim)
                {
                    ChunkDataHelper.staticClaimInRange(colony, true, claimLocation, range, (ServerLevel) colony.getWorld(), false);
                    BlockOutpostMarker.placeOutpostMarker(level, claimLocation, null);
                }
                else
                {
                    LOGGER.warn("The attempted claim is too close to another colony.");
                    MessageUtils.format("The attempted claim is too close to another colony.")
                        .sendTo(marketplace.getColony())
                        .forAllPlayers();
                    return RitualResult.FAILED;
                }
            }
            else
            {
                LOGGER.warn("The outpost claim at {} must be connected to one of these train stations {} via track.",
                    claimLocation,
                    stations);
                MessageUtils.format("The outpost claim location must be connected to a train station via track.")
                    .sendTo(marketplace.getColony())
                    .forAllPlayers();
                return RitualResult.FAILED;
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to drop items for ritual", e);
            return RitualResult.FAILED;
        }

        WishingWellHandler.showRitualEffect(level, pos);
        MessageUtils.format("An outpost has been claimed at " + pos.toShortString()).sendTo(marketplace.getColony()).forAllPlayers();

        return RitualResult.COMPLETED;
    }    
}
