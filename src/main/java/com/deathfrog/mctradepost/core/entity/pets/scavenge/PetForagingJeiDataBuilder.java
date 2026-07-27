package com.deathfrog.mctradepost.core.entity.pets.scavenge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.entity.pets.goals.scavenge.ScavengeBlockStateHelper;
import com.deathfrog.mctradepost.core.ModTags;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Builds display-only JEI entries for pet foraging from the server's active datapack state.
 * <p>
 * The builder intentionally mirrors the path conventions used by the runtime scavenge profiles while staying display-oriented:
 * expand each source block tag, derive the matching loot-table id, then flatten simple item entries from the loot-table JSON into
 * possible JEI outputs. When a custom forage table does not exist, the source block's normal loot table is inspected instead.
 * </p>
 */
public final class PetForagingJeiDataBuilder
{
    private static final String NOTE_VEGETATION_FRUIT = "jei.mctradepost.pet_foraging.note.vegetation_fruit";
    private static final String NOTE_VEGETATION_LEAVES = "jei.mctradepost.pet_foraging.note.vegetation_leaves";
    private static final String NOTE_VEGETATION_GROUNDCOVER = "jei.mctradepost.pet_foraging.note.vegetation_groundcover";
    private static final String NOTE_WATER = "jei.mctradepost.pet_foraging.note.water";
    private static final String NOTE_MUSHROOM = "jei.mctradepost.pet_foraging.note.mushroom";

    private PetForagingJeiDataBuilder()
    {
    }

    /**
     * Builds all currently known pet foraging JEI entries.
     *
     * @param server server that owns the active tag and resource-manager state
     * @return sorted immutable list of display entries
     */
    @SuppressWarnings("null")
    public static List<PetForagingJeiEntry> build(final MinecraftServer server)
    {
        if (server == null) return List.of();

        final List<PetForagingJeiEntry> entries = new ArrayList<>();

        addTaggedEntries(server, entries,
            new SourceDefinition(
                PetRoles.SCAVENGE_VEGETATION,
                blockId(MCTradePostMod.FEEDER.get()),
                ModTags.BLOCKS.TAG_FRUIT,
                "pet/vegetation_scavenge/fruit",
                NOTE_VEGETATION_FRUIT));

        addTaggedEntries(server, entries,
            new SourceDefinition(
                PetRoles.SCAVENGE_VEGETATION,
                blockId(MCTradePostMod.FEEDER.get()),
                ModTags.BLOCKS.TAG_SCAVENGE_LEAVES,
                "pet/vegetation_scavenge/leaves",
                NOTE_VEGETATION_LEAVES));

        addTaggedEntries(server, entries,
            new SourceDefinition(
                PetRoles.SCAVENGE_VEGETATION,
                blockId(MCTradePostMod.FEEDER.get()),
                ModTags.BLOCKS.TAG_GROUNDCOVER,
                "pet/vegetation_scavenge/groundcover",
                NOTE_VEGETATION_GROUNDCOVER));

        addTaggedEntries(server, entries,
            new SourceDefinition(
                PetRoles.SCAVENGE_WATER,
                blockId(MCTradePostMod.DREDGER.get()),
                ModTags.BLOCKS.WATER_SCAVENGE_BLOCK_TAG,
                "pet/amphibious_scavenge",
                NOTE_WATER));

        addTaggedEntries(server, entries,
            new SourceDefinition(
                PetRoles.SCAVENGE_LAND,
                blockId(MCTradePostMod.SCAVENGE.get()),
                ModTags.BLOCKS.MUSHROOM_SCAVENGE_BLOCK_TAG,
                "pet/mushroom_scavenge",
                NOTE_MUSHROOM));

        return entries.stream()
            .sorted(Comparator.comparing(e -> e.id().toString()))
            .toList();
    }

    /**
     * Expands one source definition into JEI entries, one per block in the configured source tag.
     */
    private static void addTaggedEntries(final MinecraftServer server, final List<PetForagingJeiEntry> entries, final SourceDefinition definition)
    {
        @SuppressWarnings("null")
        final Optional<HolderSet.Named<Block>> tag = BuiltInRegistries.BLOCK.getTag(definition.sourceTag());
        if (tag.isEmpty()) return;

        for (Holder<Block> holder : tag.get())
        {
            final Block sourceBlock = holder.value();

            if (sourceBlock == null) continue;

            final ResourceLocation sourceBlockId = blockId(sourceBlock);
            if (sourceBlockId == null) continue;

            final ResourceLocation customLootTableId =
                ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, definition.lootPathPrefix() + "/" + sourceBlockId.getPath());
            final BlockState representativeState = ScavengeBlockStateHelper.representativeState(sourceBlock);

            ResourceLocation effectiveLootTableId = customLootTableId;
            List<ItemStack> outputs = readLootOutputs(server, customLootTableId, representativeState);
            if (!lootTableExists(server, customLootTableId))
            {
                effectiveLootTableId = normalBlockLootTableId(sourceBlockId);
                outputs = readLootOutputs(server, effectiveLootTableId, representativeState);
            }
            final List<ItemStack> displayOutputs = outputs.isEmpty() ? fallbackOutput(sourceBlock) : outputs;
            if (displayOutputs.isEmpty()) continue;

            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID,
                "pet_foraging/" + definition.sourceTag().location().getPath() + "/" + sourceBlockId.getPath());

            entries.add(new PetForagingJeiEntry(
                id,
                definition.role(),
                definition.workLocationBlock(),
                sourceBlockId,
                definition.sourceTag().location(),
                effectiveLootTableId,
                displayOutputs,
                definition.noteKey()));
        }
    }

    /**
     * Returns the conventional normal loot-table id for a registered block.
     *
     * @param sourceBlockId registry id of the source block
     * @return the block's normal loot-table id
     */
    @SuppressWarnings("null")
    private static ResourceLocation normalBlockLootTableId(final ResourceLocation sourceBlockId)
    {
        return ResourceLocation.fromNamespaceAndPath(sourceBlockId.getNamespace(), "blocks/" + sourceBlockId.getPath());
    }

    /**
     * Determines whether a loot-table JSON resource exists in the active server data.
     *
     * @param server server that owns the active resource manager
     * @param lootTableId logical loot-table id
     * @return {@code true} when the corresponding JSON resource exists
     */
    @SuppressWarnings("null")
    private static boolean lootTableExists(final MinecraftServer server, final ResourceLocation lootTableId)
    {
        return server.getResourceManager().getResource(lootTableResourceId(lootTableId)).isPresent();
    }

    /**
     * Converts a logical loot-table id to its datapack JSON resource id.
     *
     * @param lootTableId logical loot-table id
     * @return datapack resource id for the loot-table JSON
     */
    @SuppressWarnings("null")
    private static ResourceLocation lootTableResourceId(final ResourceLocation lootTableId)
    {
        return ResourceLocation.fromNamespaceAndPath(
            lootTableId.getNamespace(),
            "loot_table/" + lootTableId.getPath() + ".json");
    }

    /**
     * Reads possible item outputs from a simple loot-table JSON resource.
     * <p>
     * This is not a full loot-table evaluator. It is a deterministic display flattener for the item-entry structure used by the pet
     * scavenge tables, with light support for {@code minecraft:set_count}.
     * </p>
     */
    @SuppressWarnings("null")
    private static List<ItemStack> readLootOutputs(
        final MinecraftServer server,
        final ResourceLocation lootTableId,
        final BlockState sourceState)
    {
        final ResourceLocation resourceId = lootTableResourceId(lootTableId);

        final Optional<Resource> resource = server.getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) return List.of();

        final List<ItemStack> outputs = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))
        {
            final JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return List.of();

            final JsonArray pools = root.getAsJsonObject().getAsJsonArray("pools");
            if (pools == null) return List.of();

            for (JsonElement poolElement : pools)
            {
                if (!poolElement.isJsonObject()) continue;
                final JsonObject pool = poolElement.getAsJsonObject();
                if (!conditionsMatch(pool, sourceState)) continue;
                collectItemOutputs(pool.getAsJsonArray("entries"), outputs, sourceState);
            }
        }
        catch (Exception e)
        {
            MCTradePostMod.LOGGER.warn("Unable to read pet foraging JEI loot table {}", lootTableId, e);
            return List.of();
        }

        return outputs;
    }

    /**
     * Recursively collects direct item entries from loot-table entry arrays.
     */
    private static void collectItemOutputs(
        final JsonArray entries,
        final List<ItemStack> outputs,
        final BlockState sourceState)
    {
        if (entries == null) return;

        for (JsonElement entryElement : entries)
        {
            if (!entryElement.isJsonObject()) continue;

            final JsonObject entry = entryElement.getAsJsonObject();
            if (!conditionsMatch(entry, sourceState)) continue;
            final String type = stringValue(entry, "type");

            if ("item".equals(type) || "minecraft:item".equals(type))
            {
                final ResourceLocation itemId = ResourceLocation.parse(stringValue(entry, "name"));
                final Item item = BuiltInRegistries.ITEM.get(itemId);

                outputs.add(new ItemStack(item, outputCount(entry)));
                continue;
            }

            collectItemOutputs(entry.getAsJsonArray("children"), outputs, sourceState);
            collectItemOutputs(entry.getAsJsonArray("entries"), outputs, sourceState);
        }
    }

    /**
     * Evaluates loot conditions that can be determined from the pet's known
     * harvest context. Unknown and random conditions remain eligible so JEI
     * continues to show all genuinely possible results.
     *
     * @param owner loot pool or entry containing an optional conditions array
     * @param sourceState representative state harvested by the pet
     * @return {@code true} when the owner can apply to the pet harvest context
     */
    private static boolean conditionsMatch(final JsonObject owner, final BlockState sourceState)
    {
        final JsonArray conditions = owner.getAsJsonArray("conditions");
        if (conditions == null) return true;

        for (JsonElement condition : conditions)
        {
            if (condition.isJsonObject() && !conditionMatches(condition.getAsJsonObject(), sourceState))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates one supported loot condition against the pet's empty-tool,
     * mature-source harvest context.
     *
     * @param condition loot condition JSON
     * @param sourceState representative state harvested by the pet
     * @return whether the condition permits the output
     */
    @SuppressWarnings("null")
    private static boolean conditionMatches(final JsonObject condition, final BlockState sourceState)
    {
        final String type = stringValue(condition, "condition");
        if ("minecraft:match_tool".equals(type) || "match_tool".equals(type))
        {
            return false;
        }
        if ("minecraft:block_state_property".equals(type) || "block_state_property".equals(type))
        {
            final String expectedBlock = stringValue(condition, "block");
            if (!expectedBlock.isEmpty() && !blockId(sourceState.getBlock()).toString().equals(expectedBlock))
            {
                return false;
            }

            final JsonObject properties = condition.getAsJsonObject("properties");
            if (properties == null) return true;
            for (String propertyName : properties.keySet())
            {
                final Property<?> property = sourceState.getProperties().stream()
                    .filter(candidate -> propertyName.equals(candidate.getName()))
                    .findFirst()
                    .orElse(null);
                if (property == null
                    || !propertyValueName(sourceState, property).equals(properties.get(propertyName).getAsString()))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets the serialized name of a block-state property's current value.
     *
     * @param state state containing the property
     * @param property property whose value should be serialized
     * @param <T> comparable property value type
     * @return serialized property value
     */
    @SuppressWarnings("null")
    private static <T extends Comparable<T>> String propertyValueName(
        final BlockState state,
        final Property<T> property)
    {
        return property.getName(state.getValue(property));
    }

    /**
     * Chooses the count shown in JEI for an item entry.
     * <p>
     * When the loot entry uses a min/max set-count range, JEI shows the max count so the largest possible visible stack is displayed.
     * </p>
     */
    private static int outputCount(final JsonObject entry)
    {
        final JsonArray functions = entry.getAsJsonArray("functions");
        if (functions == null) return 1;

        for (JsonElement functionElement : functions)
        {
            if (!functionElement.isJsonObject()) continue;

            final JsonObject function = functionElement.getAsJsonObject();
            final String functionType = stringValue(function, "function");
            if (!"minecraft:set_count".equals(functionType) && !"set_count".equals(functionType)) continue;

            final JsonElement count = function.get("count");
            if (count == null) return 1;

            if (count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber())
            {
                return Math.max(1, count.getAsInt());
            }

            if (count.isJsonObject())
            {
                final JsonObject countObject = count.getAsJsonObject();
                if (countObject.has("max")) return Math.max(1, countObject.get("max").getAsInt());
                if (countObject.has("min")) return Math.max(1, countObject.get("min").getAsInt());
            }
        }

        return 1;
    }

    /**
     * Fallback display output used when a source block has no custom loot-table JSON.
     */
    private static List<ItemStack> fallbackOutput(final Block sourceBlock)
    {
        final Item item = sourceBlock.asItem();
        if (item == null) return List.of();
        return List.of(new ItemStack(item));
    }

    /**
     * Reads a string property from a JSON object, returning an empty string when absent.
     */
    private static @Nonnull String stringValue(final JsonObject object, final String key)
    {
        final JsonElement value = object.get(key);
        return value == null ? "" : value.getAsString() + "";
    }

    /**
     * Resolves a block's registry id.
     */
    private static ResourceLocation blockId(@Nonnull final Block block)
    {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    /**
     * Declarative mapping between a pet foraging role, its source tag, and the loot-table path convention used for display.
     */
    private record SourceDefinition(
        PetRoles role,
        ResourceLocation workLocationBlock,
        TagKey<Block> sourceTag,
        String lootPathPrefix,
        String noteKey)
    {
    }
}
