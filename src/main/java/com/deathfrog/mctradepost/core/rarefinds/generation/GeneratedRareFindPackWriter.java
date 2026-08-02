package com.deathfrog.mctradepost.core.rarefinds.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/** Writes only generator-owned companion tags and its diagnostic report. */
public final class GeneratedRareFindPackWriter
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Prevents instantiation of this utility class. */
    private GeneratedRareFindPackWriter() { }

    /**
     * Writes generator-owned companion tags, diagnostics, and current pack metadata.
     *
     * @param server server whose world datapack directory receives the output
     * @param report completed classification report
     * @return path to the detailed generated report
     * @throws Exception when an output directory or file cannot be written
     */
    public static Path write(final MinecraftServer server, final RareFindGenerationReport report) throws Exception
    {
        final Path packRoot = server.getWorldPath(NullnessBridge.assumeNonnull(LevelResource.DATAPACK_DIR)).resolve("mctp_generated");
        final Path tagDir = packRoot.resolve("data").resolve(MCTradePostMod.MODID).resolve("tags").resolve("item");
        Files.createDirectories(tagDir);
        for (final RareFindTier tier : RareFindTier.values())
        {
            writeTag(tagDir.resolve("rarefinds_generated_tier" + tier.level() + ".json"), report.generatedTiers().get(tier));
        }
        writeTag(tagDir.resolve("rarefinds_generated_blacklist.json"), report.generatedBlacklist());
        writeTag(tagDir.resolve("spawn_eggs.json"), report.spawnEggs());
        writeTag(tagDir.resolve("rarefinds_generated_tier_without_value.json"), report.tierWithoutValue());

        final Path reportDir = packRoot.resolve("data").resolve(MCTradePostMod.MODID).resolve("reports");
        Files.createDirectories(reportDir);
        final Path reportFile = reportDir.resolve("rare_find_generation.json");
        writeJson(reportFile, reportJson(report));
        ensurePackMetadata(packRoot);
        MCTradePostMod.LOGGER.info("Wrote generated Rare Finds tags and report beneath {}", packRoot);
        return reportFile;
    }

    @SuppressWarnings("null")
    /**
     * Writes a stable item tag whose entries are individually optional for cross-pack safety.
     *
     * @param file destination tag file
     * @param ids item IDs to include
     * @throws Exception when the file cannot be written
     */
    private static void writeTag(final Path file, final Set<ResourceLocation> ids) throws Exception
    {
        final JsonObject root = new JsonObject();
        root.addProperty("replace", false);
        final JsonArray values = new JsonArray();
        ids.stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> {
            final JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            entry.addProperty("required", false);
            values.add(entry);
        });
        root.add("values", values);
        writeJson(file, root);
    }

    @SuppressWarnings("null")
    /**
     * Serializes report records into a stable, pack-author-facing JSON document.
     *
     * @param report classification report
     * @return JSON representation of the report
     */
    private static JsonObject reportJson(final RareFindGenerationReport report)
    {
        final JsonObject root = new JsonObject();
        final JsonObject summary = new JsonObject();
        for (final RareFindTier tier : RareFindTier.values())
            summary.addProperty("generated_tier" + tier.level(), report.generatedTiers().get(tier).size());
        summary.addProperty("classified_total", report.items().size());
        summary.addProperty("blacklisted_namespaces", report.blacklistedNamespaces().size());
        summary.addProperty("namespace_tier_floors", report.namespaceTierFloors().size());
        summary.addProperty("generated_blacklist", report.generatedBlacklist().size());
        summary.addProperty("spawn_eggs", report.spawnEggs().size());
        summary.addProperty("tier_without_value", report.tierWithoutValue().size());
        summary.addProperty("undefined_structures", report.undefinedStructures().size());
        summary.addProperty("structure_loot_references", report.structureLootReferences().size());
        summary.addProperty("manual_conflicts", report.manualConflicts().size());
        root.add("summary", summary);

        final JsonArray blacklistedNamespaces = new JsonArray();
        report.blacklistedNamespaces().stream().sorted().forEach(blacklistedNamespaces::add);
        root.add("blacklisted_namespaces", blacklistedNamespaces);

        final JsonObject namespaceTierFloors = new JsonObject();
        report.namespaceTierFloors().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(entry -> namespaceTierFloors.addProperty(entry.getKey(), entry.getValue().level()));
        root.add("namespace_tier_floors", namespaceTierFloors);

        final JsonArray generatedBlacklist = new JsonArray();
        report.generatedBlacklist().stream().sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(id -> generatedBlacklist.add(id.toString()));
        root.add("generated_blacklist", generatedBlacklist);

        final JsonArray spawnEggs = new JsonArray();
        report.spawnEggs().stream().sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(id -> spawnEggs.add(id.toString()));
        root.add("spawn_eggs", spawnEggs);

        final JsonArray undefined = new JsonArray();
        report.undefinedStructures().stream().sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(id -> undefined.add(id.toString()));
        root.add("undefined_structures", undefined);

        final JsonArray reverse = new JsonArray();
        for (final StructureLootScanner.Reference reference : report.structureLootReferences())
        {
            final JsonObject value = new JsonObject();
            value.addProperty("loot_table", reference.lootTable().toString());
            value.addProperty("structure", reference.structure().toString());
            value.addProperty("nbt_path", reference.nbtPath());
            reverse.add(value);
        }
        root.add("structure_loot_references", reverse);

        final JsonArray conflicts = new JsonArray();
        report.manualConflicts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            final JsonObject value = new JsonObject();
            value.addProperty("item", entry.getKey().toString());
            final JsonArray tiers = new JsonArray();
            entry.getValue().forEach(tier -> tiers.add(tier.level()));
            value.add("tiers", tiers);
            conflicts.add(value);
        });
        root.add("manual_conflicts", conflicts);

        final JsonArray items = new JsonArray();
        report.items().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            final RareFindGenerationReport.ItemResult item = entry.getValue();
            final JsonObject value = new JsonObject();
            value.addProperty("item", entry.getKey().toString());
            value.addProperty("final_tier", item.finalTier().level());
            if (item.derivedTier() != null) value.addProperty("derived_tier", item.derivedTier().level());
            value.addProperty("definitive", item.definitive());
            value.addProperty("resolution", item.resolution());
            if (item.namespaceFloor() != null) value.addProperty("namespace_floor", item.namespaceFloor().level());
            if (item.value() != null) value.addProperty("value", item.value());
            final JsonArray evidence = new JsonArray();
            for (final TierEvidence reason : item.evidence())
            {
                final JsonObject evidenceValue = new JsonObject();
                evidenceValue.addProperty("type", reason.type());
                evidenceValue.addProperty("tier", reason.tier().level());
                evidenceValue.addProperty("weight", reason.weight());
                evidenceValue.addProperty("detail", reason.detail());
                evidence.add(evidenceValue);
            }
            value.add("evidence", evidence);
            items.add(value);
        });
        root.add("items", items);
        return root;
    }

    /**
     * Writes metadata using the running Minecraft version's server-data pack format.
     *
     * @param packRoot generated datapack root
     * @throws Exception when metadata cannot be written
     */
    private static void ensurePackMetadata(final Path packRoot) throws Exception
    {
        final Path file = packRoot.resolve("pack.mcmeta");
        final JsonObject root = new JsonObject();
        final JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        pack.addProperty("description", "MC Trade Post - Generated Data");
        root.add("pack", pack);
        writeJson(file, root);
    }

    /**
     * Pretty-prints one JSON object as UTF-8.
     *
     * @param file destination file
     * @param root JSON document root
     * @throws Exception when the file cannot be written
     */
    private static void writeJson(final Path file, final JsonObject root) throws Exception
    {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
        {
            GSON.toJson(root, writer);
        }
    }
}
