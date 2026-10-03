package com.deathfrog.mctradepost.core.commands;

import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.entity.ai.workers.trade.AirRouteConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.ITradeCapable;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.MultimodalRouteConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.deathfrog.mctradepost.network.TradePathDebugPacket;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Runs production route selection and displays its result to the requesting player. */
public class CommandTradePath extends AbstractCommands
{
    private static final String START = "start";
    private static final String END = "end";

    public CommandTradePath(String name) { super(name); }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
            .then(IMCCommand.newLiteral("clear").executes(this::clear))
            .then(IMCCommand.newArgument(START, BlockPosArgument.blockPos())
                .then(IMCCommand.newArgument(END, BlockPosArgument.blockPos())
                    .executes(this::checkPreConditionAndExecute)));
    }

    @SuppressWarnings("null")
    @Override
    public int onExecute(CommandContext<CommandSourceStack> context)
    {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        BlockPos start = BlockPosArgument.getBlockPos(context, START);
        BlockPos end = BlockPosArgument.getBlockPos(context, END);

        ITradeCapable sourceBuilding = nearestTradeBuilding(player, start);
        ITradeCapable destinationBuilding = nearestTradeBuilding(player, end);
        if (sourceBuilding != null && destinationBuilding != null &&
            !sourceBuilding.getPosition().equals(destinationBuilding.getPosition()))
        {
            context.getSource().sendSuccess(() -> Component.literal("Air endpoint resolution: " +
                describeBuilding(sourceBuilding) + " -> " + describeBuilding(destinationBuilding) + "."), false);

            TrackConnectionResult airResult = AirRouteConnection.findRoute(sourceBuilding,
                new StationData(destinationBuilding), false, true, false);
            TrackRoute airRoute = airResult == null ? null : airResult.getRoute();
            if (airResult != null && airResult.isConnected() && airRoute != null)
            {
                boolean productionValid = AirRouteConnection.canLaunch(sourceBuilding, destinationBuilding, airRoute);
                PacketDistributor.sendToPlayer(player, TradePathDebugPacket.show(airRoute));
                String modes = routeModes(airRoute);
                String mooringBays = mooringBays(airRoute);
                String validity = productionValid ? "production-valid" : "physically connected but research unavailable";
                context.getSource().sendSuccess(() -> Component.literal("Air connection identified (" + validity + "): distance=" +
                    airRoute.totalDistance() + ", Mooring Bays=" + mooringBays + ", segments=" + airRoute.segments().size() + ", modes=" + modes +
                    ". Overlay expires in 30 seconds."), false);
                return 1;
            }
            context.getSource().sendSuccess(() -> Component.literal(
                "No physical air connection found for the resolved trade buildings; trying the literal-coordinate multimodal route."), false);
        }
        else
        {
            String reason = sourceBuilding != null && destinationBuilding != null
                ? "Both coordinates resolved to the same trade building"
                : "Could not resolve both coordinates to a Trade Station or Outpost";
            context.getSource().sendSuccess(() -> Component.literal(
                reason + "; trying the literal-coordinate multimodal route."), false);
        }

        TrackConnectionResult result = MultimodalRouteConnection.findRoute(player.serverLevel(), start, end, false);
        context.getSource().sendSuccess(() -> Component.literal("Debug route searches bypass colony research restrictions."), false);
        TrackRoute route = result.getRoute();
        if (!result.isConnected() || route == null)
        {
            PacketDistributor.sendToPlayer(player, TradePathDebugPacket.clearOverlay());
            context.getSource().sendFailure(Component.literal("No trade route found between " + start.toShortString() + " and " + end.toShortString() + "."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, TradePathDebugPacket.show(route));
        String modes = routeModes(route);
        context.getSource().sendSuccess(() -> Component.literal("Trade route: connected, distance=" + route.totalDistance()
            + ", segments=" + route.segments().size() + ", modes=" + modes + ". Overlay expires in 30 seconds."), false);
        return 1;
    }

    /**
     * Resolves a coordinate to the nearest Trade Station or Outpost in the colony containing that coordinate.
     *
     * @param player player issuing the command
     * @param pos coordinate used for colony and distance selection
     * @return nearest trade-capable building, or {@code null} when no suitable building is available
     */
    private static ITradeCapable nearestTradeBuilding(ServerPlayer player, @Nonnull BlockPos pos)
    {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.serverLevel(), pos);
        if (colony == null) return null;

        ITradeCapable nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (!(building instanceof ITradeCapable candidate)) continue;
            double distance = candidate.getRailStartPosition().distSqr(pos);
            if (distance < nearestDistance)
            {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * Creates a concise diagnostic description of a resolved trade building.
     *
     * @param building resolved building
     * @return building type, colony id, building position, and route-start position
     */
    private static String describeBuilding(ITradeCapable building)
    {
        return building.getClass().getSimpleName() + " colony=" + building.getColony().getID() +
            " building=" + building.getPosition().toShortString() + " routeStart=" + building.getRailStartPosition().toShortString();
    }

    /**
     * Formats route segment types in travel order.
     *
     * @param route route to describe
     * @return arrow-separated segment type names
     */
    private static String routeModes(TrackRoute route)
    {
        return route.segments().stream().map(segment -> segment.type().name()).collect(Collectors.joining(" -> "));
    }

    /**
     * Formats the Mooring Bay handoff positions selected by an air route.
     *
     * @param route air route to describe
     * @return arrow-separated Mooring Bay coordinates
     */
    private static String mooringBays(TrackRoute route)
    {
        return route.segments().stream()
            .filter(segment -> segment.type() == TrackRoute.SegmentType.MOORING && !segment.path().isEmpty())
            .map(segment -> segment.path().getFirst().toShortString())
            .collect(Collectors.joining(" -> "));
    }

    @SuppressWarnings("null")
    private int clear(CommandContext<CommandSourceStack> context)
    {
        if (!checkPreCondition(context)) return 0;
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        PacketDistributor.sendToPlayer(player, TradePathDebugPacket.clearOverlay());
        context.getSource().sendSuccess(() -> Component.literal("Trade-path overlay cleared."), false);
        return 1;
    }
}
