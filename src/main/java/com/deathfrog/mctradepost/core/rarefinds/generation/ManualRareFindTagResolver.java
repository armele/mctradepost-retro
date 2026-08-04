package com.deathfrog.mctradepost.core.rarefinds.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves manually authored item tag JSON while deliberately refusing to cross
 * into generator-owned companion tags. Runtime tag membership cannot preserve
 * that provenance after a reload.
 */
public final class ManualRareFindTagResolver
{
    private static final Gson GSON = new Gson();
    private static final String GENERATED_PREFIX = "rarefinds_generated_";
    private final ResourceManager resources;
    private final Map<ResourceLocation, Set<ResourceLocation>> cache = new HashMap<>();

    /**
     * Creates a resolver over the server's effective datapack resources.
     *
     * @param resources resolved resource manager
     */
    public ManualRareFindTagResolver(final ResourceManager resources)
    {
        this.resources = resources;
    }

    /**
     * Resolves manual membership for an item tag without crossing into generator-owned tags.
     *
     * @param tag item tag ID without the {@code #} prefix
     * @return immutable set of resolved item IDs
     */
    public Set<ResourceLocation> resolve(final ResourceLocation tag)
    {
        return resolve(tag, new HashSet<>());
    }

    /**
     * Resolves one tag recursively while detecting cycles and caching completed tags.
     *
     * @param tag tag currently being resolved
     * @param visiting recursion stack used for cycle detection
     * @return resolved manual item IDs
     */
    private Set<ResourceLocation> resolve(final ResourceLocation tag, final Set<ResourceLocation> visiting)
    {
        if (tag.getNamespace().equals(MCTradePostMod.MODID) && tag.getPath().startsWith(GENERATED_PREFIX)) return Set.of();
        if (cache.containsKey(tag)) return cache.get(tag);
        if (!visiting.add(tag))
        {
            MCTradePostMod.LOGGER.warn("Cycle while resolving definitive Rare Finds tag #{}", tag);
            return Set.of();
        }

        @SuppressWarnings("null")
        final ResourceLocation file = ResourceLocation.fromNamespaceAndPath(tag.getNamespace(), "tags/item/" + tag.getPath() + ".json");
        final LinkedHashSet<ResourceLocation> values = new LinkedHashSet<>();

        if (file == null) return values;

        for (final Resource resource : resources.getResourceStack(file))
        {
            try (Reader reader = resource.openAsReader())
            {
                final JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) continue;
                if (root.has("replace") && root.get("replace").getAsBoolean()) values.clear();
                apply(root.getAsJsonArray("values"), values, visiting, true);
                apply(root.getAsJsonArray("remove"), values, visiting, false);
            }
            catch (Exception ex)
            {
                MCTradePostMod.LOGGER.warn("Unable to resolve item tag #{} from {}", tag, file, ex);
            }
        }

        visiting.remove(tag);
        final Set<ResourceLocation> result = Set.copyOf(values);
        cache.put(tag, result);
        return result;
    }

    /**
     * Applies a tag JSON values or remove array to accumulated membership.
     *
     * @param entries tag entries to expand
     * @param target accumulated item IDs
     * @param visiting recursion stack
     * @param add {@code true} to add entries; {@code false} to remove them
     */
    @SuppressWarnings("null")
    private void apply(final JsonArray entries, final Set<ResourceLocation> target,
                       final Set<ResourceLocation> visiting, final boolean add)
    {
        if (entries == null) return;
        for (final JsonElement element : entries)
        {
            final String raw = entryId(element);
            if (raw == null) continue;
            if (raw.startsWith("#"))
            {
                final ResourceLocation nested = ResourceLocation.tryParse(raw.substring(1));
                if (nested == null) continue;
                final Set<ResourceLocation> resolved = resolve(nested, visiting);
                if (add) target.addAll(resolved); else target.removeAll(resolved);
            }
            else
            {
                final ResourceLocation item = ResourceLocation.tryParse(raw);
                if (item == null) continue;
                if (add) target.add(item); else target.remove(item);
            }
        }
    }

    /**
     * Reads an ID from either compact string syntax or the object form supporting {@code required}.
     *
     * @param element tag entry JSON
     * @return raw item or tag ID, or {@code null} for unsupported entries
     */
    private static String entryId(final JsonElement element)
    {
        if (element == null) return null;
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonObject() && element.getAsJsonObject().has("id"))
            return element.getAsJsonObject().get("id").getAsString();
        return null;
    }

    /**
     * Resolves the five definitive classification tags and retains multiple assignments for diagnostics.
     *
     * @return item IDs mapped to all manually assigned tiers
     */
    public Map<ResourceLocation, List<RareFindTier>> resolveDefinitiveTiers()
    {
        final Map<ResourceLocation, List<RareFindTier>> result = new HashMap<>();
        for (final RareFindTier tier : RareFindTier.values())
        {
            final ResourceLocation tag = ResourceLocation.fromNamespaceAndPath(
                MCTradePostMod.MODID, "rarefinds_tier" + tier.level());
            for (final ResourceLocation item : resolve(tag))
            {
                result.computeIfAbsent(item, ignored -> new ArrayList<>()).add(tier);
            }
        }
        return result;
    }
}
