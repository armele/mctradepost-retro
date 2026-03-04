package com.deathfrog.mctradepost.core.economy;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static net.minecraft.core.registries.BuiltInRegistries.ITEM;

/**
 * Writes a small datapack into the current world folder:
 * <world>/datapacks/mctp_generated/data/<modid>/item_values.json
 *
 * This allows /reload to pick it up without touching the jar.
 */
public final class GeneratedValuePackWriter
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GeneratedValuePackWriter() { }

    /**
     * @return path to the written json file
     */
    public static Path writeDatapack(final MinecraftServer server,
                                    final Map<Item, Integer> values,
                                    final boolean replace)
        throws Exception
    {
        final Path datapacksDir = server.getWorldPath(NullnessBridge.assumeNonnull(LevelResource.DATAPACK_DIR));
        final Path packRoot = datapacksDir.resolve("mctp_generated");
        final Path dataDir = packRoot.resolve("data").resolve(MCTradePostMod.MODID);
        final Path outFile = dataDir.resolve("item_values.json");

        Files.createDirectories(dataDir);

        // Stable ordering for diffs
        final Map<String, Integer> sorted = new TreeMap<>();
        for (final var e : values.entrySet())
        {
            final Item item = e.getKey();
            final Integer v = e.getValue();
            if (item == null || v == null) continue;

            final ResourceLocation id = ITEM.getKey(item);
            sorted.put(id.toString(), v);
        }

        final JsonObject root = new JsonObject();
        root.addProperty("replace", replace);

        final JsonObject vals = new JsonObject();
        for (final var e : sorted.entrySet())
        {
            vals.addProperty(e.getKey(), e.getValue());
        }
        root.add("values", vals);

        try (BufferedWriter w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8))
        {
            GSON.toJson(root, w);
        }

        // Minimal pack.mcmeta (required by datapack system)
        final Path mcmeta = packRoot.resolve("pack.mcmeta");
        if (!Files.exists(mcmeta))
        {
            final JsonObject pack = new JsonObject();
            final JsonObject packInner = new JsonObject();
            // pack_format changes between MC versions; 1.21.1 uses a newer value.
            // The game will warn if this is off, but will still load. You can adjust as needed.
            packInner.addProperty("pack_format", 26);
            packInner.addProperty("description", "MC Trade Post - Generated Item Values");
            pack.add("pack", packInner);

            try (BufferedWriter w = Files.newBufferedWriter(mcmeta, StandardCharsets.UTF_8))
            {
                GSON.toJson(pack, w);
            }
        }

        MCTradePostMod.LOGGER.info("Wrote generated item values to {}", outFile);
        return outFile;
    }
}