package com.deathfrog.mctradepost.core.rarefinds.generation;

/**
 * One independently discovered reason for assigning an item to a tier.
 *
 * @param type stable evidence-source identifier used by reports and decision rules
 * @param tier tier proposed by the source
 * @param weight relative authority of the evidence
 * @param detail human-readable provenance for diagnostics
 */
public record TierEvidence(String type, RareFindTier tier, int weight, String detail)
{
}
