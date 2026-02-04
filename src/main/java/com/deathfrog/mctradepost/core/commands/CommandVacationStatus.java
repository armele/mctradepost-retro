package com.deathfrog.mctradepost.core.commands;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandVacationStatus extends AbstractCommands 
{
    public CommandVacationStatus(String name) 
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

        BuildingResort resort = (BuildingResort) colony.getServerBuildingManager().getBuilding(
            colony.getServerBuildingManager().getBestBuilding(pos, BuildingResort.class));

        if (resort != null) {
            if (resort.getGuests().isEmpty()) {
                source.sendSuccess(() -> Component.literal("There are no guests."), false);
                return 1;
            }

            for (Vacationer vacation : resort.getGuests()) {
                String vacationerName = colony.getCitizen(vacation.getCivilianId()).getName();
                source.sendSuccess(() -> 
                    net.minecraft.network.chat.Component.literal(vacationerName + ": " + vacation.toString()),
                    false
                );
            }

            return 1;
        }
        
        return 1;
    }
    
}