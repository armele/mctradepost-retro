package com.deathfrog.mctradepost.core.commands;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.economy.DerivedItemValueGenerator;
import com.deathfrog.mctradepost.core.economy.DerivedItemValueGenerator.Options;
import com.deathfrog.mctradepost.core.economy.DerivedItemValueGenerator.Report;
import com.deathfrog.mctradepost.core.economy.ExistingItemValueLoader;
import com.deathfrog.mctradepost.core.economy.GeneratedValuePackWriter;
import com.deathfrog.mctradepost.core.economy.ItemValueSeedLoader;
import com.deathfrog.mctradepost.core.economy.ItemValueSeedLoader.SeedData;
import com.deathfrog.mctradepost.core.rarefinds.generation.RareFindGenerationCommand;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public final class MctpEconomyCommands
{
    private MctpEconomyCommands() { }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event)
    {
        register(event.getDispatcher());
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
            Commands.literal("mctp")
                .requires(src -> src.hasPermission(2) && src.getServer() != null)
                .then(
                    Commands.literal("generateItemValues")
                        .executes(ctx -> run(ctx.getSource(), false))
                        .then(Commands.literal("dryRun").executes(ctx -> run(ctx.getSource(), true)))
                )
                .then(RareFindGenerationCommand.command())
        );
    }

    @SuppressWarnings("null")
    private static int run(final CommandSourceStack source, final boolean dryRun)
    {
        final MinecraftServer server = source.getServer();
        if (server == null)
        {
            source.sendFailure(Component.literal("No server available."));
            return 0;
        }

        try
        {
            // 1) Load authoritative datapack values and generator-specific seeds separately.
            final Map<?, Integer> authoritativeValues = ExistingItemValueLoader.load(server);
            final SeedData seedData = ItemValueSeedLoader.loadSeedData(server);
            final Map<?, Integer> seeds = seedData.values();

            // 2) Derive values via fixpoint recipe propagation.
            final Options options = new DerivedItemValueGenerator.Options()
                .setApplyCookingPremium(false) // recommended default
                .setMaxIterations(50)
                .setNamespaceExclusions(seedData.namespaceExclusions());

            final Report report = DerivedItemValueGenerator.generate(server, authoritativeValues, seeds, options);

            // 3) Write datapack JSON (unless dryRun).
            if (!dryRun)
            {
                final Path outPath = GeneratedValuePackWriter.writeDatapack(server, report.values(), false);
                source.sendSuccess(() -> Component.literal("Generated item values datapack written to: " + outPath), true);
                source.sendSuccess(() -> Component.literal("Run /reload to apply, or re-enter the world."), false);
            }

            source.sendSuccess(() -> Component.literal(
                "Seeds: " + seeds.size()
                    + ", Authoritative inputs: " + authoritativeValues.size()
                    + ", Derived: " + report.derivedCount()
                    + ", Values to write: " + report.values().size()
                    + ", Iterations: " + report.iterations()
                    + ", Recipes Considered: " + report.recipesConsidered()
                    + ", Recipes Applied: " + report.recipesApplied()
                    + ", Still Unknown Outputs: " + report.unknownOutputs().size()
            ), false);

            if (dryRun && !report.unknownOutputs().isEmpty())
            {
                // Print a small sample to chat, full list is in logs via report.toLogString()
                source.sendSuccess(() -> Component.literal("Unknown sample: " + report.unknownOutputs().stream().limit(20).toList()), false);
            }

            final List<Map.Entry<String, Integer>> topUnknown = report.unknownIngredientCounts().entrySet().stream().limit(20).toList();
            if (!topUnknown.isEmpty())
            {
                source.sendSuccess(() -> Component.literal("Top unknown ingredients (seed these for best ROI):"), false);
                for (final Entry<String, Integer> e : topUnknown)
                {
                    source.sendSuccess(() -> Component.literal(" - " + e.getKey() + " : " + e.getValue()), false);
                }
            }

            // Optional: log full details
            MCTradePostMod.LOGGER.info(report.toLogString());

            return 1;
        }
        catch (final Exception ex)
        {
            MCTradePostMod.LOGGER.error("Failed to generate item values", ex);
            source.sendFailure(Component.literal("Failed to generate item values: " + ex.getMessage()));
            return 0;
        }
    }
}
