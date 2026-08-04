package com.deathfrog.mctradepost.core.commands;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.economy.DerivedItemValueGenerator;
import com.deathfrog.mctradepost.core.economy.DerivedItemValueGenerator.Options;
import com.deathfrog.mctradepost.core.economy.DerivedItemValueGenerator.Report;
import com.deathfrog.mctradepost.core.economy.ExistingItemValueLoader;
import com.deathfrog.mctradepost.core.economy.GeneratedValuePackWriter;
import com.deathfrog.mctradepost.core.economy.ItemValueSeedLoader;
import com.deathfrog.mctradepost.core.economy.ItemValueSeedLoader.SeedData;
import com.deathfrog.mctradepost.core.economy.TierDerivedItemValueProvider;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class MctpEconomyCommands
{
    private MctpEconomyCommands() { }

    /** Builds the operator-only item-value generation command branch. */
    public static LiteralArgumentBuilder<CommandSourceStack> generateItemValuesCommand()
    {
        return Commands.literal("generateItemValues")
            .requires(src -> src.hasPermission(2) && src.getServer() != null)
            .executes(ctx -> run(ctx.getSource(), false, false))
            .then(Commands.literal("dryRun")
                .executes(ctx -> run(ctx.getSource(), true, false))
                .then(Commands.literal("deriveFromTier")
                    .executes(ctx -> run(ctx.getSource(), true, true))))
            .then(Commands.literal("deriveFromTier")
                .executes(ctx -> run(ctx.getSource(), false, true))
                .then(Commands.literal("dryRun")
                    .executes(ctx -> run(ctx.getSource(), true, true))));
    }

    @SuppressWarnings("null")
    private static int run(final CommandSourceStack source, final boolean dryRun, final boolean deriveFromTier)
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
            final Map<Item, Integer> authoritativeValues = ExistingItemValueLoader.load(server);
            final SeedData seedData = ItemValueSeedLoader.loadSeedData(server);
            final Map<Item, Integer> explicitSeeds = seedData.values();

            // 2) Derive values via fixpoint recipe propagation.
            final Options options = new DerivedItemValueGenerator.Options()
                .setApplyCookingPremium(false) // recommended default
                .setMaxIterations(50)
                .setNamespaceExclusions(seedData.namespaceExclusions());

            final Report firstPass = DerivedItemValueGenerator.generate(
                server, authoritativeValues, explicitSeeds, options);
            final Map<Item, Integer> tierValues;
            final Report report;
            if (deriveFromTier)
            {
                // Only items unresolved after ordinary recipe propagation receive a tier fallback.
                tierValues = TierDerivedItemValueProvider.derive(
                    server, authoritativeValues, firstPass.values());
                final Map<Item, Integer> secondPassSeeds =
                    TierDerivedItemValueProvider.mergeWithFallbacks(firstPass.values(), tierValues);
                report = DerivedItemValueGenerator.generate(
                    server, authoritativeValues, secondPassSeeds, options);
            }
            else
            {
                tierValues = Map.of();
                report = firstPass;
            }

            // 3) Write datapack JSON (unless dryRun).
            if (!dryRun)
            {
                final Path outPath = GeneratedValuePackWriter.writeDatapack(server, report.values(), false);
                source.sendSuccess(() -> Component.literal("Generated item values datapack written to: " + outPath), true);
                source.sendSuccess(() -> Component.literal("Run /reload to apply, or re-enter the world."), false);
            }

            source.sendSuccess(() -> Component.literal(
                "Seeds: " + explicitSeeds.size()
                    + ", Tier-derived inputs: " + tierValues.size()
                    + ", Authoritative inputs: " + authoritativeValues.size()
                    + ", Recipe-derived: " + firstPass.derivedCount()
                    + (deriveFromTier ? ", Newly unlocked recipes: " + report.derivedCount() : "")
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
