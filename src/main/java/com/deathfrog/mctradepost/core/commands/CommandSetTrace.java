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

import com.deathfrog.mctradepost.api.util.NullnessBridge;

public class CommandSetTrace extends AbstractCommands
{

    public static final String TRACE_ON_OFF     = "setting";
    public static final String TRACE_KEY        = "class";

    public CommandSetTrace(String name)
    {
        super(name);
    }

    /**
     * Sets the trace state for the given trace key.
     * If the trace key is TRACE_NONE, all trace keys will be set to false.
     * Otherwise, the trace state for the given trace key will be set to the given trace setting.
     * @param context the command context
     * @return 1, always
     */
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

    /**
     * Builds the command structure for the set_trace command.
     * 
     * This command requires two arguments: the trace key to set, and a boolean indicating whether to enable or disable tracing for that key.
     * 
     * The first argument is a string that indicates which trace key to set. The possible values can be retrieved using the {@link com.deathfrog.mctradepost.api.util.TraceUtils#getTraceKeys()} method.
     * 
     * The second argument is a boolean indicating whether to enable or disable tracing for the specified key.
     * 
     * The command is executed using the {@link #checkPreConditionAndExecute(CommandContext, CommandSourceStack)} method.
     */
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
        .then(IMCCommand.newArgument(TRACE_KEY, StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TraceUtils.getTraceKeys(), NullnessBridge.assumeNonnull(builder)))
            .then(IMCCommand.newArgument(TRACE_ON_OFF, BoolArgumentType.bool())
        .executes(this::checkPreConditionAndExecute)));
    }
}
