package com.deathfrog.mctradepost.core.commands;

import java.util.ArrayList;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.outpost.OutpostChildBuildingBootstrapper;
import com.deathfrog.mctradepost.core.outpost.OutpostChildBuildingBootstrapper.BootstrapResult;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Initializes embedded child buildings for existing level 0 outposts in the player's current colony.
 */
public class CommandOutpostInit extends AbstractCommands
{
    /**
     * Creates the outpost initialization command.
     *
     * @param name the command literal to register below {@code /mctp outpost}.
     */
    public CommandOutpostInit(final String name)
    {
        super(name);
    }

    /**
     * Runs outpost child-building initialization for every level 0 outpost in the player's current colony.
     *
     * @param context the Brigadier command context.
     * @return 1 if at least one builder hut was placed or adopted; otherwise 0.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendSuccess(() -> Component.literal("This command must be run by a player inside a colony."), false);
            return 0;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), player.blockPosition());
        if (colony == null)
        {
            source.sendSuccess(() -> Component.literal("These commands are only valid from within a colony."), false);
            return 0;
        }

        int eligible = 0;
        int placed = 0;
        int adopted = 0;
        int collisions = 0;
        int failed = 0;
        int noBuilder = 0;
        String scannedPath = "";

        for (final IBuilding building : new ArrayList<>(colony.getServerBuildingManager().getBuildings().values()))
        {
            if (!(building instanceof BuildingOutpost outpost) || outpost.getBuildingLevel() != 0)
            {
                continue;
            }

            eligible++;
            final BootstrapResult result = OutpostChildBuildingBootstrapper.initExisting(outpost, player);
            placed += result.placed;
            adopted += result.adopted;
            collisions += result.collisions;
            failed += result.failed;
            if (result.structurePack != null && result.blueprintPath != null)
            {
                scannedPath = result.structurePack + ":" + result.blueprintPath;
            }
            if (result.scanned == 0)
            {
                noBuilder++;
            }
        }

        if (eligible == 0)
        {
            source.sendSuccess(() -> Component.literal("No eligible level 0 outposts found in this colony."), false);
            return 0;
        }

        final int outpostCount = eligible;
        final int placedCount = placed;
        final int adoptedCount = adopted;
        final int collisionCount = collisions;
        final int failedCount = failed;
        final int noBuilderCount = noBuilder;
        final String scannedPathMessage = scannedPath.isEmpty() ? "" : " Last scanned " + scannedPath + ".";
        source.sendSuccess(() -> Component.literal(
            "Initialized " + outpostCount + " level 0 outpost(s). Placed " + placedCount
                + " builder hut(s), adopted " + adoptedCount
                + ", collisions " + collisionCount
                + ", failed " + failedCount
                + ", no embedded builder " + noBuilderCount + "." + scannedPathMessage),
            false);

        return placed > 0 || adopted > 0 ? 1 : 0;
    }
}
