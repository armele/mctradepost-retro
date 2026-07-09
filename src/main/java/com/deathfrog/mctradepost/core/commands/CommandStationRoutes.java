package com.deathfrog.mctradepost.core.commands;

import java.util.List;
import java.util.Map;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug command that prints cached station route data, including segmented dimensional routes.
 */
public class CommandStationRoutes extends AbstractCommands
{
    /**
     * Creates the station routes debug command.
     *
     * @param name command node name
     */
    public CommandStationRoutes(String name)
    {
        super(name);
    }

    /**
     * Prints cached route connectivity, distance, and segment details for the nearest station.
     *
     * @param context command context
     * @return command result code
     */
    @SuppressWarnings("null")
    @Override
    public int onExecute(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        BuildingStation station = nearestStation(context);
        if (station == null)
        {
            return 0;
        }

        Map<StationData, TrackConnectionResult> results = station.getConnectionResults();
        if (results.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No cached station routes found."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Cached station routes: " + results.size()), false);
        for (Map.Entry<StationData, TrackConnectionResult> entry : results.entrySet())
        {
            StationData stationData = entry.getKey();
            TrackConnectionResult result = entry.getValue();
            TrackRoute route = result == null ? null : result.getRoute();

            source.sendSuccess(() -> Component.literal(stationData.toString()), false);
            if (result == null)
            {
                source.sendSuccess(() -> Component.literal("    No connection result."), false);
                continue;
            }

            source.sendSuccess(() -> Component.literal("    Connected: " + result.connected +
                " distance=" + result.getRouteDistance() +
                " closest=" + (result.closestPoint == null ? "null" : result.closestPoint.toShortString()) +
                " ageTicks=" + result.ageOfCheck(station.getColony().getWorld().getGameTime())), false);

            if (route == null)
            {
                source.sendSuccess(() -> Component.literal("    Route: none"), false);
                continue;
            }

            List<TrackRoute.Segment> segments = route.segments();
            for (int i = 0; i < segments.size(); i++)
            {
                TrackRoute.Segment segment = segments.get(i);
                int index = i + 1;
                if (segment.type() == TrackRoute.SegmentType.TRANSFER)
                {
                    source.sendSuccess(() -> Component.literal("    [" + index + "] TRANSFER " +
                        segment.transferFrom().shortDescription() + " -> " + segment.transferTo().shortDescription()), false);
                }
                else
                {
                    String start = segment.path().isEmpty() ? "empty" : segment.path().getFirst().toShortString();
                    String end = segment.path().isEmpty() ? "empty" : segment.path().getLast().toShortString();
                    source.sendSuccess(() -> Component.literal("    [" + index + "] RAIL " +
                        segment.dimension().location() +
                        " nodes=" + segment.path().size() +
                        " distance=" + segment.distance() +
                        " start=" + start +
                        " end=" + end), false);
                }
            }
        }

        return 1;
    }

    private BuildingStation nearestStation(CommandContext<CommandSourceStack> context)
    {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null)
        {
            return null;
        }

        BlockPos pos = player.blockPosition();
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), pos);
        if (colony == null)
        {
            context.getSource().sendSuccess(() -> Component.literal("These commands are only valid from within a colony."), false);
            return null;
        }

        return (BuildingStation) colony.getServerBuildingManager()
            .getBuilding(colony.getServerBuildingManager().getBestBuilding(pos, BuildingStation.class));
    }
}
