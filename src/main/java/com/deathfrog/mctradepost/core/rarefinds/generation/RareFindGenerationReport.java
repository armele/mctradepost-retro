package com.deathfrog.mctradepost.core.rarefinds.generation;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete deterministic result of one pack-level Rare Finds analysis.
 *
 * @param items classified items and their evidence
 * @param generatedTiers non-definitive items to write into companion tags
 * @param blacklistedNamespaces configured registry namespaces used for generated exclusions
 * @param namespaceTierFloors configured minimum generated tiers by registry namespace
 * @param generatedBlacklist items excluded by configured namespace rules
 * @param spawnEggs loaded spawn egg items excluded and written to the generated spawn-eggs tag
 * @param tierWithoutValue classified items without a positive configured value
 * @param undefinedStructures scanned templates not covered by a structure-tier rule
 * @param structureLootReferences reverse-map records discovered in structure NBT
 * @param manualConflicts items manually assigned to more than one definitive tier
 */
public record RareFindGenerationReport(
    Map<ResourceLocation, ItemResult> items,
    Map<RareFindTier, Set<ResourceLocation>> generatedTiers,
    Set<String> blacklistedNamespaces,
    Map<String, RareFindTier> namespaceTierFloors,
    Set<ResourceLocation> generatedBlacklist,
    Set<ResourceLocation> spawnEggs,
    Set<ResourceLocation> tierWithoutValue,
    Set<ResourceLocation> undefinedStructures,
    List<StructureLootScanner.Reference> structureLootReferences,
    Map<ResourceLocation, List<RareFindTier>> manualConflicts)
{
    /**
     * The classification and provenance retained for one item.
     *
     * @param finalTier effective tier after precedence rules
     * @param derivedTier tier proposed by automated evidence, if any
     * @param definitive whether a manually authored tier determined the result
     * @param resolution diagnostic resolution name
     * @param namespaceFloor applicable configured namespace floor, if any
     * @param value configured item value, or {@code null} when unknown
     * @param evidence all collected automated evidence
     */
    public record ItemResult(RareFindTier finalTier, RareFindTier derivedTier, boolean definitive,
                             String resolution, RareFindTier namespaceFloor, Integer value, List<TierEvidence> evidence) { }
}
