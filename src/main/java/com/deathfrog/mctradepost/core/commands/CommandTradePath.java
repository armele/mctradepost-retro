package com.deathfrog.mctradepost.core.commands;

import java.util.stream.Collectors;

import com.deathfrog.mctradepost.core.entity.ai.workers.trade.MultimodalRouteConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute;
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
        TrackConnectionResult result = MultimodalRouteConnection.findRoute(player.serverLevel(), start, end, false);
        TrackRoute route = result.getRoute();
        if (!result.isConnected() || route == null)
        {
            PacketDistributor.sendToPlayer(player, TradePathDebugPacket.clearOverlay());
            context.getSource().sendFailure(Component.literal("No trade route found between " + start.toShortString() + " and " + end.toShortString() + "."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, TradePathDebugPacket.show(route));
        String modes = route.segments().stream().map(segment -> segment.type().name()).collect(Collectors.joining(" -> "));
        context.getSource().sendSuccess(() -> Component.literal("Trade route: connected, distance=" + route.totalDistance()
            + ", segments=" + route.segments().size() + ", modes=" + modes + ". Overlay expires in 30 seconds."), false);
        return 1;
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
