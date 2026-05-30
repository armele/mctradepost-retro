package com.deathfrog.mctradepost.core.commands;

import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.core.blocks.AbstractBlockPetWorkingLocation;
import com.deathfrog.mctradepost.core.blocks.blockentity.PetWorkingBlockEntity;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

public class CommandPetshopWorkLocations extends AbstractCommands
{
    public CommandPetshopWorkLocations(String name)
    {
        super(name);
    }

    @Override
    public int onExecute(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null)
        {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, playerPos);

        if (colony == null)
        {
            source.sendSuccess(() -> Component.literal("These commands are only valid from within a colony."), false);
            return 0;
        }

        List<BlockPos> workLocations = PetRegistryUtil.getWorkLocations(colony).stream()
            .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getX())
                .thenComparingInt(pos -> pos.getY())
                .thenComparingInt(pos -> pos.getZ()))
            .toList();

        source.sendSuccess(() -> Component.literal(
            "Registered pet work locations for colony " + colony.getID() + ": " + workLocations.size()), false);

        if (workLocations.isEmpty())
        {
            return 1;
        }

        for (BlockPos workLocation : workLocations)
        {
            if (workLocation == null) continue;

            source.sendSuccess(() -> Component.literal(formatWorkLocation(level, workLocation)), false);
        }

        return 1;
    }

    private @Nonnull String formatWorkLocation(ServerLevel level, @Nonnull BlockPos workLocation)
    {
        BlockState state = level.getBlockState(workLocation);
        boolean validBlock = state.getBlock() instanceof AbstractBlockPetWorkingLocation;
        PetRoles role = validBlock ? PetData.roleFromPosition(level, workLocation) : null;
        String roleName = role == null ? "Unknown" : role.name();
        String blockName = state.getBlock().getDescriptionId();
        String displayName = "";

        BlockEntity blockEntity = level.getBlockEntity(workLocation);
        if (blockEntity instanceof PetWorkingBlockEntity petWorkingBlockEntity)
        {
            displayName = " | Name: " + petWorkingBlockEntity.getDefaultName().getString();
        }

        return workLocation.toShortString()
            + " | Valid: " + validBlock
            + " | Role: " + roleName
            + " | Block: " + blockName
            + displayName;
    }
}
