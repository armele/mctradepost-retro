package com.deathfrog.mctradepost.api.util;

/** Identifies why economic value was removed from a colony treasury. */
public enum EconomicReductionReason
{
    COIN_WITHDRAWAL("coin_withdrawal"),
    RARE_FIND_PURCHASE("rare_find_purchase"),
    SUBSCRIPTION_INITIAL("subscription_initial"),
    SUBSCRIPTION_RENEWAL("subscription_renewal"),
    RARE_FIND_REROLL("rare_find_reroll"),
    ANIMAL_ACQUISITION("animal_acquisition"),
    PET_RESCUE("pet_rescue"),
    CITIZEN_INCENTIVE("citizen_incentive");

    private final String statisticSuffix;

    EconomicReductionReason(final String statisticSuffix)
    {
        this.statisticSuffix = statisticSuffix;
    }

    /**
     * Returns the stable suffix used by economic expense statistics.
     *
     * @return stable statistic suffix
     */
    public String statisticSuffix()
    {
        return statisticSuffix;
    }
}
