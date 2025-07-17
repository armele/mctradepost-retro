package com.deathfrog.mctradepost;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.deathfrog.mctradepost.core.commands.CommandTree;
import com.deathfrog.mctradepost.core.commands.CommandVacationClear;
import com.deathfrog.mctradepost.core.commands.CommandVacationStatus;
import com.deathfrog.mctradepost.core.commands.CommandFrameStatus;
import com.deathfrog.mctradepost.core.commands.CommandPendingRecycling;
import com.deathfrog.mctradepost.core.commands.CommandPetshopStatus;
import com.deathfrog.mctradepost.core.commands.CommandSetTrace;
import com.deathfrog.mctradepost.core.commands.CommandStationStatus;
import com.deathfrog.mctradepost.core.commands.CommandStationsClear;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public class MCTPCommands 
{

    public static final String CMD_BUILDING = "building";
    public static final String CMD_CITIZEN = "citizen";
    public static final String CMD_VISITOR = "visitor";
    public static final String CMD_STATION_STATIONDATA = "stationdata";
    public static final String CMD_ANIMALTRAINER_PETDATA = "petdata";   
    public static final String CMD_STATION_CLEARCONNECTIONS = "clearconnections";
    public static final String CMD_MARKETPLACE_FRAMESTATUS = "framestatus";
    public static final String CMD_RECYCLING_PENDING = "pending";
    public static final String CMD_RESORT_VACATIONSTATUS = "vacationstatus";
    public static final String CMD_RESORT_CLEARVACATION = "clearvacations";
    public static final String CMD_DYNTRACE_SETTRACE = "trace";

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) 
    {

        /*
         * Building command tree.
         */
        final CommandTree mctpAnimalTrainerCommands = new CommandTree("trainer")
            .addNode(new CommandPetshopStatus(CMD_ANIMALTRAINER_PETDATA).build());


        final CommandTree mctpStationCommands = new CommandTree("station")
            .addNode(new CommandStationStatus(CMD_STATION_STATIONDATA).build())
            .addNode(new CommandStationsClear(CMD_STATION_CLEARCONNECTIONS).build());

        final CommandTree mctpRecyclingCommands = new CommandTree("recycling")
            .addNode(new CommandPendingRecycling(CMD_RECYCLING_PENDING).build());

        final CommandTree mctpMarketplaceCommands = new CommandTree("marketplace")
            .addNode(new CommandFrameStatus(CMD_MARKETPLACE_FRAMESTATUS).build());

        final CommandTree mctpResortCommands = new CommandTree("resort")
            .addNode(new CommandVacationStatus(CMD_RESORT_VACATIONSTATUS).build())
            .addNode(new CommandVacationClear(CMD_RESORT_CLEARVACATION).build());

        /*
         * Root TradePost command tree, all subtrees are added here.
         */
        final CommandTree mctpRoot = new CommandTree("mctp")
            .addNode(mctpMarketplaceCommands)
            .addNode(mctpResortCommands)
            .addNode(mctpRecyclingCommands)
            .addNode(mctpStationCommands)
            .addNode(mctpAnimalTrainerCommands)
            .addNode(new CommandSetTrace(CMD_DYNTRACE_SETTRACE).build());

        // Adds all command trees to the dispatcher to register the commands.
        event.getDispatcher().register(mctpRoot.build());
    }
}