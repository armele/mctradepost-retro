package com.deathfrog.mctradepost;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.deathfrog.mctradepost.core.commands.CommandTree;
import com.deathfrog.mctradepost.core.commands.CommandFrameStatus;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public class MCTPCommands 
{

    public static final String CMD_BUILDING = "building";
    public static final String CMD_CITIZEN = "citizen";
    public static final String CMD_VISITOR = "visitor";
    public static final String CMD_BUILDING_FRAMESTATUS = "framestatus";

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
            .addNode(mctpMarketplaceCommands);

        // Adds all command trees to the dispatcher to register the commands.
        event.getDispatcher().register(mctpRoot.build());
    }
}