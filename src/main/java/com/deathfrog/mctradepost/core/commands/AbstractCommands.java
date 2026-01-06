package com.deathfrog.mctradepost.core.commands;

import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public abstract class AbstractCommands implements IMCCommand
{
    protected String name = "";

    protected AbstractCommands()
    {
        // Do not use.
    }

    @Override
    public boolean checkPreCondition(CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();

        // 1) Vanilla OP / dedicated server permission level
        if (source.hasPermission(4))
        {
            return true;
        }

        // 2) Must be a player for colony/permissions logic
        final Entity entity = source.getEntity();
        if (!(entity instanceof Player player))
        {
            context.getSource().sendSuccess(() -> Component.literal("These commands are not available to non-players."), false);
            return false;
        }

        // 3) MineColonies "oped" (their internal concept; keep if you rely on it elsewhere)
        if (IMCCommand.isPlayerOped(player))
        {
            return true;
        }

        GameProfile profile = player.getGameProfile();

        // 4) Singleplayer / integrated server owner
        if (profile != null && source.getServer() != null &&
            source.getServer().isSingleplayerOwner(profile))
        {
            return true;
        }

        context.getSource().sendSuccess(() -> Component.literal("These commands are only available to server ops or players designated as MineColonies ops."), false);

        return false;
    }

    public AbstractCommands(String name)
    {
        this.name = name;
    }

    /**
     * Returns the name of the command.
     * 
     * @return the name of the command.
     */
    @Override
    public String getName()
    {
        return name;
    }
}
