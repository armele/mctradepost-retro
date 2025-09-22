package com.deathfrog.mctradepost.core.commands;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
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
     * Outputs the status of all display shelves in the marketplace building nearest to the player.
     * If the player is not in a colony, outputs a message saying so.
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
        if (colony == null) {
            context.getSource().sendSuccess(
                () -> Component.literal("These commands are only valid from within a colony."),
                false
            );
            return 0;
        }

        BuildingRecycling recycling = (BuildingRecycling) colony.getBuildingManager().getBuilding(
            colony.getBuildingManager().getBestBuilding(pos, BuildingRecycling.class));

        if (recycling != null) {
            source.sendSuccess(() -> 
                net.minecraft.network.chat.Component.literal("Pending recycling orders from the warehouse:"),
                false
            );

            for (ItemStorage pending : recycling.getPendingRecyclingQueue()) 
            {
                source.sendSuccess(() -> 
                    net.minecraft.network.chat.Component.literal(pending.toString()),
                    false
                );
            }

            return 1;
        }
        else 
        {
            source.sendSuccess(() -> 
                net.minecraft.network.chat.Component.literal("There is no recycling center in this colony."),
                false
            );
        }
        
        return 1;
    }
    
}