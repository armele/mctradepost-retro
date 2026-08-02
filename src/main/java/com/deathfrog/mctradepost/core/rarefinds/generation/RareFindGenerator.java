package com.deathfrog.mctradepost.core.rarefinds.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AirItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Coordinates pack-only evidence collection and conservative tier decisions. */
public final class RareFindGenerator
{
    /** Prevents instantiation of this orchestration utility. */
    private RareFindGenerator()
    {}

    /**
     * Collects all enabled evidence and produces final and generated classifications. Blacklisting wins first, followed by definitive
     * manual tiers, explicit rules, and derived evidence.
     *
     * @param server source of registries, recipes, tags, and resolved datapack resources
     * @return complete classification and diagnostic report
     */
    @SuppressWarnings("null")
    public static RareFindGenerationReport generate(final MinecraftServer server)
    {
        final RareFindGenerationRules rules = RareFindGenerationRules.load(server);
        final ManualRareFindTagResolver tags = new ManualRareFindTagResolver(server.getResourceManager());
        final Map<ResourceLocation, List<RareFindTier>> manual = tags.resolveDefinitiveTiers();
        final Set<ResourceLocation> blacklist =
            tags.resolve(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "rarefinds_blacklist"));
        final Set<ResourceLocation> generatedBlacklist = new TreeSet<>();
        final Set<ResourceLocation> spawnEggs = new TreeSet<>();
        for (final Item item : BuiltInRegistries.ITEM)
        {
            if (item == null) continue;

            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (rules.blacklistedNamespaces().contains(id.getNamespace())) generatedBlacklist.add(id);
            if (item instanceof SpawnEggItem) spawnEggs.add(id);
        }
        final Set<ResourceLocation> effectiveBlacklist = new HashSet<>(blacklist);
        effectiveBlacklist.addAll(generatedBlacklist);
        effectiveBlacklist.addAll(spawnEggs);
        final Map<ResourceLocation, Integer> values = GenerationDataReader.itemValues(server);
        final Map<ResourceLocation, List<TierEvidence>> evidence = new HashMap<>();

        collectBaseEvidence(rules, values, evidence);

        final StructureLootScanner.Result structures = StructureLootScanner.scan(server.getResourceManager());
        collectStructureEvidence(server, rules, structures, evidence);

        Map<ResourceLocation, RareFindTier> provisional = decideAll(evidence);
        collectRecipeEvidence(server, provisional, evidence);
        provisional = decideAll(evidence);

        final Map<ResourceLocation, RareFindGenerationReport.ItemResult> results = new TreeMap<>();
        final Map<RareFindTier, Set<ResourceLocation>> generated = new EnumMap<>(RareFindTier.class);
        for (final RareFindTier tier : RareFindTier.values()) generated.put(tier, new TreeSet<>());
        final Set<ResourceLocation> tierWithoutValue = new TreeSet<>();
        final Map<ResourceLocation, List<RareFindTier>> conflicts = new TreeMap<>();

        for (final Item item : BuiltInRegistries.ITEM)
        {
            if (item == null) continue;

            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

            if (item instanceof AirItem || effectiveBlacklist.contains(id)) continue;

            final List<RareFindTier> manualTiers = manual.getOrDefault(id, List.of());

            if (manualTiers.size() > 1) conflicts.put(id, List.copyOf(manualTiers));

            final RareFindTier definitive = manualTiers.stream().max(Comparator.comparingInt(RareFindTier::level)).orElse(null);
            final RareFindTier derived = provisional.get(id);
            final RareFindTier namespaceFloor = rules.namespaceTierFloors().get(id.getNamespace());
            final boolean food = new ItemStack(item).getFoodProperties(null) != null;
            RareFindTier finalTier = definitive != null ? definitive : food ? RareFindTier.TIER0 : derived;

            if (definitive == null && !food &&
                namespaceFloor != null &&
                (finalTier == null || finalTier.level() < namespaceFloor.level()))
            {
                finalTier = namespaceFloor;
            }

            if (finalTier == null) continue;

            final String resolution;
            if (definitive != null)
            {
                resolution = derived != null && derived != definitive ? "MANUAL_OVERRIDE" : "DEFINITIVE";
            }
            else if (food)
            {
                resolution = "FOOD_TIER0";
            }
            else if (namespaceFloor != null && (derived == null || derived.level() < namespaceFloor.level()))
            {
                resolution = "NAMESPACE_FLOOR";
            }
            else
            {
                resolution = "DERIVED";
            }
            final Integer value = values.get(id);
            final List<TierEvidence> itemEvidence = List.copyOf(evidence.getOrDefault(id, List.of()));

            results.put(id,
                new RareFindGenerationReport.ItemResult(finalTier,
                    derived,
                    definitive != null,
                    resolution,
                    namespaceFloor,
                    value,
                    itemEvidence));

            if (definitive == null) generated.get(finalTier).add(id);

            if (value == null || value <= 0) tierWithoutValue.add(id);
        }

        final Set<ResourceLocation> undefined = new TreeSet<>(structures.allStructures());
        undefined.removeIf(id -> rules.tierForStructure(id) != null);
        return new RareFindGenerationReport(Map.copyOf(results),
            immutableTierMap(generated),
            rules.blacklistedNamespaces(),
            rules.namespaceTierFloors(),
            Set.copyOf(generatedBlacklist),
            Set.copyOf(spawnEggs),
            Set.copyOf(tierWithoutValue),
            Set.copyOf(undefined),
            structures.references(),
            Map.copyOf(conflicts));
    }

    /**
     * Collects explicit rules, configured values, rarity, and MineColonies block tiers.
     *
     * @param rules    merged generator rules
     * @param values   configured item values
     * @param evidence evidence accumulator by item ID
     */
    @SuppressWarnings("null")
    private static void collectBaseEvidence(final RareFindGenerationRules rules,
        final Map<ResourceLocation, Integer> values,
        final Map<ResourceLocation, List<TierEvidence>> evidence)
    {
        for (final Item item : BuiltInRegistries.ITEM)
        {
            if (item == null) continue;

            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            final ItemStack stack = new ItemStack(item);
            final RareFindTier explicit = rules.itemTiers().get(id);
            if (explicit != null) add(evidence, id, new TierEvidence("generator_rule", explicit, 10, "Explicit item rule"));

            if (stack.getFoodProperties(null) != null)
                add(evidence, id, new TierEvidence("food", RareFindTier.TIER0, 10, "Edible item; retained-search only"));

            final Integer value = values.get(id);
            if (value != null && value > 0)
            {
                final RareFindTier tier = rules.tierForValue(value);
                add(evidence, id, new TierEvidence("item_value", tier, tier == RareFindTier.TIER4 ? 5 : 4, "Coin value " + value));
            }

            final RareFindTier rarity = RareFindTier.fromRarity(stack.getRarity());
            if (rarity != null) add(evidence, id, new TierEvidence("rarity", rarity, 2, "Item rarity"));

            if (item instanceof BlockItem blockItem)
            {
                final RareFindTier blockTier = mineColoniesTier(blockItem.getBlock());
                if (blockTier != null) add(evidence,
                    id,
                    new TierEvidence("minecolonies_block_tier",
                        blockTier,
                        blockTier == RareFindTier.TIER4 ? 5 : 4,
                        "MineColonies building-material tier"));
            }
        }
    }

    /**
     * Converts classified structure-template loot references into easiest-source item evidence.
     *
     * @param server   resource source used to inspect loot tables
     * @param rules    structure tier assignments
     * @param scan     structure NBT scan results
     * @param evidence evidence accumulator by item ID
     */
    private static void collectStructureEvidence(final MinecraftServer server,
        final RareFindGenerationRules rules,
        final StructureLootScanner.Result scan,
        final Map<ResourceLocation, List<TierEvidence>> evidence)
    {
        final Map<ResourceLocation, RareFindTier> easiestTableTier = new HashMap<>();
        final Map<ResourceLocation, Set<ResourceLocation>> tableStructures = new HashMap<>();
        for (final StructureLootScanner.Reference reference : scan.references())
        {
            final RareFindTier tier = rules.tierForStructure(reference.structure());
            if (tier == null) continue;
            easiestTableTier.merge(reference.lootTable(), tier, (left, right) -> left.level() <= right.level() ? left : right);
            tableStructures.computeIfAbsent(reference.lootTable(), ignored -> new HashSet<>()).add(reference.structure());
        }

        final LootTableOutputScanner loot = new LootTableOutputScanner(server.getResourceManager());
        final Map<ResourceLocation, RareFindTier> easiestItemTier = new HashMap<>();
        final Map<ResourceLocation, String> itemDetail = new HashMap<>();
        for (final Map.Entry<ResourceLocation, RareFindTier> entry : easiestTableTier.entrySet())
        {
            final String detail = "Loot table " + entry.getKey() +
                " in " +
                tableStructures.get(entry.getKey()).size() +
                " classified structure template(s)";
            for (final ResourceLocation item : loot.outputs(entry.getKey()))
            {
                final RareFindTier previous = easiestItemTier.get(item);
                if (previous == null || entry.getValue().level() < previous.level())
                {
                    easiestItemTier.put(item, entry.getValue());
                    itemDetail.put(item, detail);
                }
            }
        }
        easiestItemTier
            .forEach((item, tier) -> add(evidence, item, new TierEvidence("structure_loot", tier, 3, itemDetail.get(item))));
    }

    /**
     * Adds one conservative recipe signal per output using its easiest fully-known recipe. Recipes containing an unknown alternative
     * are not used as evidence.
     *
     * @param server   source of loaded recipes and registries
     * @param known    provisional item tiers
     * @param evidence evidence accumulator by item ID
     */
    @SuppressWarnings("null")
    private static void collectRecipeEvidence(final MinecraftServer server,
        final Map<ResourceLocation, RareFindTier> known,
        final Map<ResourceLocation, List<TierEvidence>> evidence)
    {
        final Map<ResourceLocation, RareFindTier> easiestRecipes = new HashMap<>();
        final Map<ResourceLocation, String> recipeDetails = new HashMap<>();
        for (final RecipeHolder<?> holder : server.getRecipeManager().getRecipes())
        {
            final ItemStack result = holder.value().getResultItem(server.registryAccess());
            if (result.isEmpty()) continue;
            RareFindTier recipeTier = null;
            boolean useful = false;
            boolean completelyKnown = true;
            for (final Ingredient ingredient : holder.value().getIngredients())
            {
                if (ingredient.isEmpty()) continue;
                RareFindTier easiestChoice = null;
                for (final ItemStack choice : ingredient.getItems())
                {
                    final RareFindTier choiceTier = known.get(BuiltInRegistries.ITEM.getKey(choice.getItem()));
                    if (choiceTier == null)
                    {
                        completelyKnown = false;
                        break;
                    }
                    if (choiceTier != null && (easiestChoice == null || choiceTier.level() < easiestChoice.level()))
                        easiestChoice = choiceTier;
                }
                if (!completelyKnown) break;
                if (easiestChoice != null)
                {
                    useful = true;
                    if (recipeTier == null || easiestChoice.level() > recipeTier.level()) recipeTier = easiestChoice;
                }
            }
            if (completelyKnown && useful && recipeTier != null)
            {
                final ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
                final RareFindTier previous = easiestRecipes.get(resultId);
                if (previous == null || recipeTier.level() < previous.level())
                {
                    easiestRecipes.put(resultId, recipeTier);
                    recipeDetails.put(resultId, "Ingredient progression from recipe " + holder.id());
                }
            }
        }
        easiestRecipes.forEach((item, tier) -> add(evidence, item, new TierEvidence("recipe", tier, 2, recipeDetails.get(item))));
    }

    /**
     * Resolves all accumulated evidence independently.
     *
     * @param evidence evidence grouped by item ID
     * @return derived tiers for items with usable evidence
     */
    private static Map<ResourceLocation, RareFindTier> decideAll(final Map<ResourceLocation, List<TierEvidence>> evidence)
    {
        final Map<ResourceLocation, RareFindTier> result = new HashMap<>();
        evidence.forEach((id, list) -> {
            final RareFindTier tier = decide(list);
            if (tier != null) result.put(id, tier);
        });
        return result;
    }

    /**
     * Resolves one item's evidence using explicit-rule precedence and a weighted median. Tier four additionally requires authoritative
     * or corroborating evidence.
     *
     * @param evidence evidence collected for one item
     * @return derived tier, or {@code null} when there is no evidence
     */
    @SuppressWarnings("null")
    private static RareFindTier decide(final List<TierEvidence> evidence)
    {
        if (evidence.isEmpty()) return null;
        final TierEvidence explicit = evidence.stream().filter(e -> "generator_rule".equals(e.type())).findFirst().orElse(null);
        if (explicit != null) return explicit.tier();
        final List<TierEvidence> ordered = evidence.stream().sorted(Comparator.comparingInt(e -> e.tier().level())).toList();
        final int total = ordered.stream().mapToInt(TierEvidence::weight).sum();
        int accumulated = 0;
        RareFindTier result = null;
        for (final TierEvidence entry : ordered)
        {
            accumulated += entry.weight();
            if (accumulated * 2 >= total)
            {
                result = entry.tier();
                break;
            }
        }
        if (result == RareFindTier.TIER4)
        {
            final long distinct =
                evidence.stream().filter(e -> e.tier() == RareFindTier.TIER4).map(TierEvidence::type).distinct().count();
            final boolean authoritative = evidence.stream().anyMatch(e -> e.tier() == RareFindTier.TIER4 && e.weight() >= 5);
            if (!authoritative && distinct < 2) result = RareFindTier.TIER3;
        }
        return result;
    }

    /**
     * Maps the highest matching MineColonies block tier onto Rare Finds tiers.
     *
     * @param block block represented by an item
     * @return mapped tier, or {@code null} when the block is unclassified
     */
    @SuppressWarnings("null")
    private static RareFindTier mineColoniesTier(final Block block)
    {
        int highest = 0;
        for (int tier = 1; tier <= 5; tier++)
        {
            final TagKey<Block> tag =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("minecolonies", "tier" + tier + "blocks"));
            if (block.defaultBlockState().is(tag)) highest = tier;
        }
        return switch (highest)
        {
            case 1 -> RareFindTier.TIER1;
            case 2 -> RareFindTier.TIER2;
            case 3, 4 -> RareFindTier.TIER3;
            case 5 -> RareFindTier.TIER4;
            default -> null;
        };
    }

    /**
     * Appends one evidence record to an item's accumulator.
     *
     * @param evidence evidence grouped by item ID
     * @param item     target item ID
     * @param entry    evidence to append
     */
    private static void add(final Map<ResourceLocation, List<TierEvidence>> evidence,
        final ResourceLocation item,
        final TierEvidence entry)
    {
        evidence.computeIfAbsent(item, ignored -> new ArrayList<>()).add(entry);
    }

    /**
     * Freezes generated tier sets before exposing them in a report.
     *
     * @param source mutable generated-tier map
     * @return immutable map containing immutable sets
     */
    private static Map<RareFindTier, Set<ResourceLocation>> immutableTierMap(final Map<RareFindTier, Set<ResourceLocation>> source)
    {
        final Map<RareFindTier, Set<ResourceLocation>> copy = new EnumMap<>(RareFindTier.class);
        source.forEach((tier, ids) -> copy.put(tier, Set.copyOf(ids)));
        return Map.copyOf(copy);
    }
}
