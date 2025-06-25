package com.deathfrog.mctradepost.core.commands;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.DisplayCase;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandStationStatus extends AbstractCommands 
{
    public CommandStationStatus(String name) 
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

        BuildingStation station = (BuildingStation) colony.getBuildingManager().getBuilding(
            colony.getBuildingManager().getBestBuilding(pos, BuildingStation.class));

        if (station != null) {
            if (station.getStations().isEmpty()) {
                source.sendSuccess(() -> 
                    net.minecraft.network.chat.Component.literal("No station data found."),
                    false
                );
                return 1;
            }

            for (StationData display : station.getStations().values()) {
                source.sendSuccess(() -> 
                    net.minecraft.network.chat.Component.literal(display.toString()),
                    false
                );
            }

            return 1;
        }
        
        return 1;
    }
    
}