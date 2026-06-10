package com.deathfrog.mctradepost.core.commands;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.deathfrog.mctradepost.api.colony.buildings.modules.RecyclingItemListModule.PendingWarehouseRequest;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandPendingRecycling extends AbstractCommands
{
    public CommandPendingRecycling(String name)
    {
        super(name);
    }

    /**
     * Outputs the status of all display shelves in the marketplace building nearest to the player. If the player is not in a colony,
     * outputs a message saying so.
     * 
     * @return 0 if the player is not in a colony, 1 if the player is in a colony
     */
    @Override
    public int onExecute(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = context.getSource().getPlayer();

        if (player == null)
        {
            return 0;
        }

        BlockPos pos = player.blockPosition();

        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), pos);
        if (colony == null)
        {
            context.getSource().sendSuccess(() -> Component.literal("These commands are only valid from within a colony."), false);
            return 0;
        }

        BuildingRecycling recycling = (BuildingRecycling) colony.getServerBuildingManager()
            .getBuilding(colony.getServerBuildingManager().getBestBuilding(pos, BuildingRecycling.class));

        if (recycling != null)
        {
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Pending recycling warehouse requests:"),
                false);

            for (PendingWarehouseRequest pending : recycling.getPendingWarehouseRequests())
            {
                if (pending == null)
                {
                    continue;
                }

                IRequest<?> request = pending.token() == null ? null : recycling.getColony().getRequestManager().getRequestForToken(pending.token());
                String state = request == null ? "unknown" : request.getState().name();
                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(pending.item() + " token=" + pending.token() + " state=" + state), false);
            }

            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Accepted recycling inputs:"), false);

            for (ItemStorage accepted : recycling.getAcceptedRecyclingInputs())
            {
                if (accepted == null)
                {
                    continue;
                }

                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(accepted.toString() + ""), false);
            }

            return 1;
        }
        else
        {
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("There is no recycling center in this colony."),
                false);
        }

        return 1;
    }
}
