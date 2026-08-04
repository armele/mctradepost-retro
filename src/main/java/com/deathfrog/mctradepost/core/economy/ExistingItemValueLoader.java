package com.deathfrog.mctradepost.core.economy;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.io.Reader;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.minecraft.core.registries.BuiltInRegistries.ITEM;

/** Loads authoritative item values that are available to economy generation. */
public final class ExistingItemValueLoader
{
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "item_values.json");
    private static final String GENERATED_PACK = "mctp_generated";

    private ExistingItemValueLoader() { }

    /**
     * Merges {@code mctradepost:item_values.json} in normal low-to-high datapack order.
     * A resource with {@code replace: true} clears values from earlier included resources.
     * The generator-owned datapack is ignored so an earlier run cannot become input to a later run.
     *
     * @param server source of the active datapack resource stack
     * @return resolved, loaded items and their authoritative values
     */
    @SuppressWarnings("null")
    public static Map<Item, Integer> load(final MinecraftServer server)
    {
        final List<ValueDocument> documents = new ArrayList<>();
        server.getResourceManager().getResourceStack(FILE).forEach(resource ->
        {
            if (isGeneratedPack(resource.sourcePackId())) return;
            try (Reader reader = resource.openAsReader())
            {
                final JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) documents.add(new ValueDocument(resource.sourcePackId(), parsed.getAsJsonObject()));
            }
            catch (Exception ex)
            {
                MCTradePostMod.LOGGER.warn("Unable to read item values from pack {}", resource.sourcePackId(), ex);
            }
        });
        final Map<ResourceLocation, Integer> merged = merge(documents);
        final Map<Item, Integer> result = new HashMap<>();
        merged.forEach((id, value) -> ITEM.getOptional(id).ifPresent(item -> result.put(item, value)));
        MCTradePostMod.LOGGER.info("Loaded {} authoritative item values for economy generation", result.size());
        return Map.copyOf(result);
    }

    /** Package-visible merge seam used by focused tests. */
    static Map<ResourceLocation, Integer> merge(final List<ValueDocument> documents)
    {
        final Map<ResourceLocation, Integer> result = new HashMap<>();
        for (final ValueDocument document : documents)
        {
            if (isGeneratedPack(document.packId())) continue;
            final JsonObject root = document.root();
            if (root.has("replace") && root.get("replace").getAsBoolean()) result.clear();
            if (!root.has("values") || !root.get("values").isJsonObject()) continue;
            for (final Map.Entry<String, JsonElement> entry : root.getAsJsonObject("values").entrySet())
            {
                String entryVal = entry.getKey();

                if (entryVal == null) continue;

                final ResourceLocation id = ResourceLocation.tryParse(entryVal);
                if (id == null) continue;
                try
                {
                    result.put(id, entry.getValue().getAsInt());
                }
                catch (RuntimeException ex)
                {
                    MCTradePostMod.LOGGER.warn("Ignoring invalid item value {} from pack {}", entry.getKey(), document.packId());
                }
            }
        }
        return Map.copyOf(result);
    }

    static boolean isGeneratedPack(final String packId)
    {
        return packId.equals(GENERATED_PACK) || packId.endsWith("/" + GENERATED_PACK);
    }

    record ValueDocument(String packId, JsonObject root) { }
}
