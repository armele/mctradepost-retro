package com.deathfrog.mctradepost.core.rarefinds.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Datapack-configurable inputs used only by the pack-level Rare Finds generator.
 *
 * @param tier2Value minimum coin value for tier two; lower positive values map to tier zero
 * @param tier3Value minimum coin value for tier three
 * @param tier4Value minimum coin value for tier four
 * @param structureTiers exact or trailing-wildcard structure mappings
 * @param itemTiers explicit item-tier overrides for the generator
 * @param blacklistedNamespaces item registry namespaces excluded from Rare Finds
 * @param namespaceTierFloors minimum generated tier assigned by item registry namespace
 */
public record RareFindGenerationRules(int tier2Value, int tier3Value, int tier4Value,
                                      Map<String, RareFindTier> structureTiers,
                                      Map<ResourceLocation, RareFindTier> itemTiers,
                                      Set<String> blacklistedNamespaces,
                                      Map<String, RareFindTier> namespaceTierFloors)
{
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(
        MCTradePostMod.MODID, "rare_find_generation_rules.json");

    /**
     * Loads and merges all Rare Finds rule resources in datapack order.
     * Unknown structure IDs remain harmless string patterns.
     *
     * @param server source of the resolved resource stack
     * @return validated merged generation rules
     * @throws IllegalArgumentException when value thresholds are not positive and ascending
     */
    @SuppressWarnings("null")
    public static RareFindGenerationRules load(final MinecraftServer server)
    {
        int tier2 = 1_000;
        int tier3 = 8_000;
        int tier4 = 64_000;
        final Map<String, RareFindTier> structures = new LinkedHashMap<>();
        final Map<ResourceLocation, RareFindTier> items = new LinkedHashMap<>();
        final Set<String> namespaces = new LinkedHashSet<>();
        final Map<String, RareFindTier> namespaceFloors = new LinkedHashMap<>();

        for (final Resource resource : server.getResourceManager().getResourceStack(FILE))
        {
            try (Reader reader = resource.openAsReader())
            {
                final JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) continue;
                if (root.has("replace") && root.get("replace").getAsBoolean())
                {
                    structures.clear();
                    items.clear();
                    namespaces.clear();
                    namespaceFloors.clear();
                }

                final JsonObject thresholds = root.getAsJsonObject("value_thresholds");
                if (thresholds != null)
                {
                    if (thresholds.has("tier2")) tier2 = thresholds.get("tier2").getAsInt();
                    if (thresholds.has("tier3")) tier3 = thresholds.get("tier3").getAsInt();
                    if (thresholds.has("tier4")) tier4 = thresholds.get("tier4").getAsInt();
                }

                final JsonObject mappings = root.getAsJsonObject("structure_tiers");
                if (mappings != null)
                {
                    for (final Map.Entry<String, JsonElement> entry : mappings.entrySet())
                    {
                        structures.put(entry.getKey(), RareFindTier.of(entry.getValue().getAsInt()));
                    }
                }

                final JsonObject itemMappings = root.getAsJsonObject("item_tiers");
                if (itemMappings != null)
                {
                    for (final Map.Entry<String, JsonElement> entry : itemMappings.entrySet())
                    {
                        final ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                        if (id != null) items.put(id, RareFindTier.of(entry.getValue().getAsInt()));
                    }
                }

                if (root.has("blacklisted_namespaces") && root.get("blacklisted_namespaces").isJsonArray())
                {
                    for (final JsonElement element : root.getAsJsonArray("blacklisted_namespaces"))
                    {
                        final String namespace = element.getAsString().trim();
                        if (!namespace.isEmpty()) namespaces.add(namespace);
                    }
                }

                final JsonObject floorMappings = root.getAsJsonObject("namespace_tier_floors");
                if (floorMappings != null)
                {
                    for (final Map.Entry<String, JsonElement> entry : floorMappings.entrySet())
                    {
                        final String namespace = entry.getKey().trim();
                        if (!namespace.isEmpty()) namespaceFloors.put(namespace, RareFindTier.of(entry.getValue().getAsInt()));
                    }
                }
            }
            catch (Exception ex)
            {
                MCTradePostMod.LOGGER.warn("Unable to read Rare Finds generation rules from {}", FILE, ex);
            }
        }

        if (!(tier2 > 0 && tier3 > tier2 && tier4 > tier3))
        {
            throw new IllegalArgumentException("Rare Finds value thresholds must be positive and ascending");
        }
        return new RareFindGenerationRules(tier2, tier3, tier4, Map.copyOf(structures), Map.copyOf(items),
            Set.copyOf(namespaces), Map.copyOf(namespaceFloors));
    }

    /**
     * Maps a positive item value to the configured coin-scale tier. Values below
     * the tier-two threshold are search-only tier zero.
     *
     * @param value unscaled configured item value
     * @return matching tier, or {@code null} when the value is not positive
     */
    public RareFindTier tierForValue(final int value)
    {
        if (value >= tier4Value) return RareFindTier.TIER4;
        if (value >= tier3Value) return RareFindTier.TIER3;
        if (value >= tier2Value) return RareFindTier.TIER2;
        return value > 0 ? RareFindTier.TIER0 : null;
    }

    /**
     * Finds the most-specific exact or trailing-wildcard rule for a structure template.
     *
     * @param structure structure template ID
     * @return assigned tier, or {@code null} when no rule matches
     */
    public RareFindTier tierForStructure(final ResourceLocation structure)
    {
        final String id = structure.toString();
        RareFindTier result = null;
        int bestSpecificity = -1;
        for (final Map.Entry<String, RareFindTier> entry : structureTiers.entrySet())
        {
            final String glob = entry.getKey();
            final boolean matches = glob.endsWith("*") ? id.startsWith(glob.substring(0, glob.length() - 1)) : id.equals(glob);
            if (matches && glob.length() > bestSpecificity)
            {
                result = entry.getValue();
                bestSpecificity = glob.length();
            }
        }
        return result;
    }
}
