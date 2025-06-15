package com.deathfrog.mctradepost.core.commands;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import static com.deathfrog.mctradepost.api.util.TraceUtils.*;

public class CommandSetTrace extends AbstractCommands
{

    public static final String TRACE_ON_OFF     = "setting";
    public static final String TRACE_KEY        = "class";

    public CommandSetTrace(String name)
    {
        super(name);
    }

    @Override
    public int onExecute(CommandContext<CommandSourceStack> context)
    {
        final String traceKey = StringArgumentType.getString(context, TRACE_KEY);   

        if (TRACE_NONE.equals(traceKey))
        {
            for (String key : TraceUtils.getTraceKeys())
            {
                TraceUtils.setTrace(key, false);
            }
        }
        else 
        {
            final boolean traceSetting = BoolArgumentType.getBool(context, TRACE_ON_OFF); 
            TraceUtils.setTrace(traceKey, traceSetting);
        }

        return 1;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
        .then(IMCCommand.newArgument(TRACE_KEY, StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TraceUtils.getTraceKeys(), builder))
            .then(IMCCommand.newArgument(TRACE_ON_OFF, BoolArgumentType.bool())
        .executes(this::checkPreConditionAndExecute)));
    }
}
