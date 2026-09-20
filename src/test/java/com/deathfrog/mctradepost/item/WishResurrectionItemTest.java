package com.deathfrog.mctradepost.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WishResurrectionItemTest
{
    @Test
    void sameDayCostsOneCoin()
    {
        assertEquals(1, WishResurrectionItem.burialCost(20, 20));
    }

    @Test
    void lastEligibleDayCostsOneStack()
    {
        assertEquals(64, WishResurrectionItem.burialCost(83, 20));
    }

    @Test
    void burialExpiresAtAgeSixtyFour()
    {
        assertEquals(-1, WishResurrectionItem.burialCost(84, 20));
    }

    @Test
    void futureBurialDayIsRejected()
    {
        assertEquals(-1, WishResurrectionItem.burialCost(19, 20));
    }
}
