package com.deathfrog.mctradepost.core.rarefinds.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/** Command facade for the pack-level analyzer. */
public final class RareFindGenerationCommand
{
    /** Prevents instantiation of this command utility. */
    private RareFindGenerationCommand() { }

    /**
     * Builds the {@code generateRareFindTiers} command branch.
     *
     * @return command branch supporting normal and dry-run execution
     */
    public static LiteralArgumentBuilder<CommandSourceStack> command()
    {
        return Commands.literal("generateRareFindTiers")
            .requires(source -> source.hasPermission(2) && source.getServer() != null)
            .executes(context -> run(context.getSource(), false))
            .then(Commands.literal("dryRun").executes(context -> run(context.getSource(), true)));
    }

    /**
     * Runs pack analysis and optionally writes the generated datapack.
     *
     * @param source invoking command source
     * @param dryRun whether output files should be suppressed
     * @return one on success, zero on failure
     */
    @SuppressWarnings("null")
    private static int run(final CommandSourceStack source, final boolean dryRun)
    {
        final MinecraftServer server = source.getServer();
        if (server == null) return 0;
        try
        {
            final RareFindGenerationReport report = RareFindGenerator.generate(server);
            if (!dryRun)
            {
                final Path output = GeneratedRareFindPackWriter.write(server, report);
                source.sendSuccess(() -> Component.literal("Generated Rare Finds datapack report: " + output), true);
                source.sendSuccess(() -> Component.literal("Run /reload to apply the generated companion tags."), false);
            }
            source.sendSuccess(() -> Component.literal(summary(report, dryRun)), false);
            MCTradePostMod.LOGGER.info("{}", summary(report, dryRun));
            return 1;
        }
        catch (Exception ex)
        {
            MCTradePostMod.LOGGER.error("Failed to generate Rare Finds tiers", ex);
            source.sendFailure(Component.literal("Failed to generate Rare Finds tiers: " + ex.getMessage()));
            return 0;
        }
    }

    /**
     * Formats compact command and log output for a generation result.
     *
     * @param report completed report
     * @param dryRun whether the report came from a dry run
     * @return single-line summary
     */
    private static String summary(final RareFindGenerationReport report, final boolean dryRun)
    {
        return (dryRun ? "Rare Finds dry run" : "Rare Finds generation")
            + ": T0=" + report.generatedTiers().get(RareFindTier.TIER0).size()
            + ", T1=" + report.generatedTiers().get(RareFindTier.TIER1).size()
            + ", T2=" + report.generatedTiers().get(RareFindTier.TIER2).size()
            + ", T3=" + report.generatedTiers().get(RareFindTier.TIER3).size()
            + ", T4=" + report.generatedTiers().get(RareFindTier.TIER4).size()
            + ", namespace-blacklisted=" + report.generatedBlacklist().size()
            + ", spawn-eggs=" + report.spawnEggs().size()
            + ", tier-without-value=" + report.tierWithoutValue().size()
            + ", undefined structures=" + report.undefinedStructures().size()
            + ", manual conflicts=" + report.manualConflicts().size();
    }
}
