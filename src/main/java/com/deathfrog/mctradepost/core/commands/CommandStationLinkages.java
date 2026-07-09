package com.deathfrog.mctradepost.core.commands;

import java.util.List;

import com.deathfrog.mctradepost.api.items.datacomponent.DimensionalLinkageRecord;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationConnectionModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationConnectionModule.LinkageValidation;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.deathfrog.mctradepost.item.DimensionalLinkageItem;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Debug command that reports installed dimensional linkages for the nearest station in the player's colony.
 */
public class CommandStationLinkages extends AbstractCommands
{
    /**
     * Creates the station linkage debug command.
     *
     * @param name command node name
     */
    public CommandStationLinkages(String name)
    {
        super(name);
    }

    /**
     * Prints linkage capacity, endpoint data, and validation status for the nearest station.
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

        BuildingStationConnectionModule module = station.getModule(MCTPBuildingModules.STATION_CONNECTION);
        if (module == null)
        {
            source.sendSuccess(() -> Component.literal("Nearest station has no station connection module."), false);
            return 0;
        }

        List<LinkageValidation> validations = module.validateLinkages();
        source.sendSuccess(() -> Component.literal("Dimensional linkages installed: " + validations.size() + " / " + module.getDimensionalLinkageLimit()), false);

        if (validations.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No dimensional linkages installed."), false);
            return 1;
        }

        for (int i = 0; i < validations.size(); i++)
        {
            LinkageValidation validation = validations.get(i);
            ItemStack stack = validation.stack();
            DimensionalLinkageRecord record = DimensionalLinkageItem.linkageRecord(stack);
            String overworld = record.overworldEndpoint().map(DimPos::shortDescription).orElse("unset");
            String nether = record.netherEndpoint().map(DimPos::shortDescription).orElse("unset");
            int index = i + 1;

            source.sendSuccess(() -> Component.literal("[" + index + "] " + validation.status() + " id=" + record.id()), false);
            source.sendSuccess(() -> Component.literal("    Overworld: " + overworld), false);
            source.sendSuccess(() -> Component.literal("    Nether: " + nether), false);
            source.sendSuccess(() -> Component.literal("    Message key: " + validation.messageKey()), false);
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
