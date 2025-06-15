package com.deathfrog.mctradepost;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.deathfrog.mctradepost.core.commands.CommandTree;
import com.deathfrog.mctradepost.core.commands.CommandFrameStatus;
import com.deathfrog.mctradepost.core.commands.CommandSetTrace;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public class MCTPCommands 
{

    public static final String CMD_BUILDING = "building";
    public static final String CMD_CITIZEN = "citizen";
    public static final String CMD_VISITOR = "visitor";
    public static final String CMD_BUILDING_FRAMESTATUS = "framestatus";
    public static final String CMD_DYNTRACE_SETTRACE = "trace";

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) 
    {

        /*
         * Building command tree.
         */
        final CommandTree mctpMarketplaceCommands = new CommandTree("marketplace")
            .addNode(new CommandFrameStatus(CMD_BUILDING_FRAMESTATUS).build());

        /*
         * Root TradePost command tree, all subtrees are added here.
         */
        final CommandTree mctpRoot = new CommandTree("mctp")
            .addNode(mctpMarketplaceCommands)
            .addNode(new CommandSetTrace(CMD_DYNTRACE_SETTRACE).build());

        // Adds all command trees to the dispatcher to register the commands.
        event.getDispatcher().register(mctpRoot.build());
    }
}