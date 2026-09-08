package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import com.minecolonies.core.util.ExperienceUtils;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.minecolonies.api.util.constant.CitizenConstants.MAX_CITIZEN_LEVEL;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies pay-cycle incentive accounting against the actual core skill handler. */
class IncentivePlanTest
{
    private static final Skill STRENGTH = Skill.Strength;

    /** Test worker with core skills, a persisted plan, and a finite treasury. */
    private static class Worker
    {
        final CitizenSkillHandler skills = new CitizenSkillHandler();
        IncentivePlan plan = new IncentivePlan(UUID.randomUUID());
        long balance = 100_000;
        int withdrawals;

        /**
         * Creates a worker with the requested normal Strength.
         * @param strength initial Strength level
         */
        Worker(final int strength)
        {
            skills.incrementLevel(STRENGTH, strength - 1);
        }

        /**
         * Processes a colony day against this worker's treasury.
         * @param day colony day to process
         * @param price economic units per skill point
         * @return whether a new eligible day was processed
         */
        boolean day(final int day, final int price)
        {
            return day(day, price, 1);
        }

        /**
         * Processes a colony day using an explicit pay-cycle length.
         * @param day colony day to process
         * @param price economic units per skill point per cycle
         * @param cycleDays colony days covered by one payment
         * @return whether an activation, edit, cancellation, or renewal was processed
         */
        @SuppressWarnings("null")
        boolean day(final int day, final int price, final int cycleDays)
        {
            return plan.processDay(day, price, cycleDays, skills::getLevel, skills::incrementLevel, amount -> {
                withdrawals++;
                if (amount > balance) return false;
                balance -= amount;
                return true;
            });
        }

        /** @return minimal core citizen context with maximum-level housing for XP progression tests */
        ICitizenData citizen()
        {
            final IBuilding home = (IBuilding) Proxy.newProxyInstance(IBuilding.class.getClassLoader(), new Class<?>[] {IBuilding.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBuildingLevelEquivalent", "getMaxBuildingLevel" -> 5;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            return (ICitizenData) Proxy.newProxyInstance(ICitizenData.class.getClassLoader(), new Class<?>[] {ICitizenData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHomeBuilding" -> home;
                    case "getEntity" -> Optional.empty();
                    case "getJob", "markDirty" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }
    }

    /** Verifies next-dawn activation, combined skill pricing, and duplicate-tick protection. */
    @Test
    void nextDawnActivationAndDuplicateTicksDoNotDoubleCharge()
    {
        final Worker joe = new Worker(20);
        joe.plan.schedule(Map.of(STRENGTH, 2, Skill.Dexterity, 2), 10);
        assertFalse(joe.day(10, 1000));
        assertEquals(20, joe.skills.getLevel(STRENGTH));
        assertTrue(joe.day(11, 1000));
        assertEquals(22, joe.skills.getLevel(STRENGTH));
        assertEquals(3, joe.skills.getLevel(Skill.Dexterity));
        assertEquals(96_000, joe.balance);
        assertFalse(joe.day(11, 1000));
        assertEquals(1, joe.withdrawals);
    }

    /** Verifies Joe's 20-to-25-to-26-to-21 example while retaining fractional XP. */
    @Test
    void joeKeepsEarnedLevelAndPartialXpAfterCancellation()
    {
        final Worker joe = new Worker(20);
        joe.plan.schedule(Map.of(STRENGTH, 5), 10);
        joe.day(11, 1000);
        joe.skills.addXpToSkill(STRENGTH, ExperienceUtils.getXPNeededForNextLevel(25) + 7.5, joe.citizen());
        assertEquals(26, joe.skills.getLevel(STRENGTH));
        assertEquals(7.5, joe.skills.getSkills().get(STRENGTH).getExperience());
        joe.plan.schedule(Map.of(), 11);
        assertFalse(joe.day(11, 1000));
        assertEquals(26, joe.skills.getLevel(STRENGTH));
        joe.day(12, 1000);
        assertEquals(21, joe.skills.getLevel(STRENGTH));
        assertEquals(7.5, joe.skills.getSkills().get(STRENGTH).getExperience());
        assertEquals(1, joe.withdrawals);
    }

    /** Verifies skill and plan NBT reload without duplicate bonuses, charges, or lost XP. */
    @Test
    void reloadPreservesAppliedLedgerWithoutApplyingOrBillingAgain()
    {
        final Worker joe = new Worker(20);
        joe.plan.schedule(Map.of(STRENGTH, 5), 0);
        joe.day(1, 1000);
        joe.skills.incrementLevel(STRENGTH, 1);
        joe.skills.getSkills().get(STRENGTH).setExperience(11.25);
        final CompoundTag citizenSave = joe.skills.write();
        final CompoundTag planSave = joe.plan.write();
        joe.skills.read(citizenSave);
        joe.plan = IncentivePlan.read(planSave);
        assertFalse(joe.day(1, 1000));
        joe.plan.schedule(Map.of(), 1);
        joe.day(2, 1000);
        assertEquals(21, joe.skills.getLevel(STRENGTH));
        assertEquals(11.25, joe.skills.getSkills().get(STRENGTH).getExperience());
        assertEquals(1, joe.withdrawals);
    }

    /** Verifies all-or-nothing funding, a zero balance, persisted failure, and explicit reactivation. */
    @Test
    void insufficientFundsEndsWholePackageAndDoesNotRetryAfterDeposit()
    {
        final Worker joe = new Worker(20);
        joe.balance = 4_000;
        joe.plan.schedule(Map.of(STRENGTH, 2, Skill.Dexterity, 2), 0);
        joe.day(1, 1000);
        assertEquals(0, joe.balance);
        joe.day(2, 1000);
        assertEquals(20, joe.skills.getLevel(STRENGTH));
        assertEquals(1, joe.skills.getLevel(Skill.Dexterity));
        assertTrue(joe.plan.endedForFunds());
        assertEquals(0, joe.balance);
        joe.balance = 100_000;
        joe.plan = IncentivePlan.read(joe.plan.write());
        joe.day(3, 1000);
        assertEquals(100_000, joe.balance);
        assertEquals(20, joe.skills.getLevel(STRENGTH));
        joe.plan.schedule(Map.of(STRENGTH, 2, Skill.Dexterity, 2), 3);
        joe.day(4, 1000);
        assertEquals(96_000, joe.balance);
        assertFalse(joe.plan.endedForFunds());
    }

    /** Verifies skipped days accrue no debt and renewal replaces the previous bonus. */
    @Test
    void skippedDaysAreNotBackChargedAndRenewalDoesNotStack()
    {
        final Worker joe = new Worker(20);
        joe.plan.schedule(Map.of(STRENGTH, 5), 0);
        joe.day(1, 1000);
        joe.day(20, 1000);
        assertEquals(90_000, joe.balance);
        assertEquals(25, joe.skills.getLevel(STRENGTH));
        assertEquals(5, joe.plan.applied(STRENGTH));
    }

    /** Verifies natural gains reduce the points available for purchase at the core cap. */
    @SuppressWarnings("null")
    @Test
    void capRecalculatedAtDawnAndOnlyAvailablePointsAreBilled()
    {
        final Worker joe = new Worker(MAX_CITIZEN_LEVEL - 5);
        joe.plan.schedule(Map.of(STRENGTH, 5), 0);
        joe.skills.incrementLevel(STRENGTH, 2);
        joe.day(1, 1000);
        assertEquals(MAX_CITIZEN_LEVEL, joe.skills.getLevel(STRENGTH));
        assertEquals(3, joe.plan.applied(STRENGTH));
        assertEquals(97_000, joe.balance);
        joe.plan.removeApplied(joe.skills::incrementLevel);
        assertEquals(MAX_CITIZEN_LEVEL - 3, joe.skills.getLevel(STRENGTH));
    }

    /** Verifies edited boosts and prices take effect at dawn without disturbing partial XP. */
    @Test
    void editsLeavePaidBoostsAndXpIntactUntilNextDawn()
    {
        final Worker joe = new Worker(20);
        joe.plan.schedule(Map.of(STRENGTH, 5), 0);
        joe.day(1, 1000);
        joe.skills.getSkills().get(STRENGTH).setExperience(12);
        joe.plan.schedule(Map.of(STRENGTH, 2), 1);
        assertEquals(25, joe.skills.getLevel(STRENGTH));
        joe.day(2, 2000);
        assertEquals(22, joe.skills.getLevel(STRENGTH));
        assertEquals(91_000, joe.balance);
        assertEquals(12, joe.skills.getSkills().get(STRENGTH).getExperience());
    }

    /** Verifies repeated cleanup does not remove extra levels or erase permanent skill losses. */
    @SuppressWarnings("null")
    @Test
    void cleanupIsIdempotentAndKeepsPermanentSkillLosses()
    {
        final Worker joe = new Worker(20);
        joe.plan.schedule(Map.of(STRENGTH, 5), 0);
        joe.day(1, 1000);
        joe.skills.incrementLevel(STRENGTH, -2);
        joe.plan.removeApplied(joe.skills::incrementLevel);
        joe.plan.removeApplied(joe.skills::incrementLevel);
        assertEquals(18, joe.skills.getLevel(STRENGTH));
    }

    /** Verifies oversized prices cannot wrap into affordable values and negative bonuses are rejected. */
    @Test
    void packagePriceDoesNotOverflowAndCannotBeNegative()
    {
        final Worker joe = new Worker(20);
        joe.balance = Integer.MAX_VALUE;
        joe.plan.schedule(Map.of(STRENGTH, 2), 0);
        assertEquals(2L * Integer.MAX_VALUE, joe.plan.cycleCost(Integer.MAX_VALUE, joe.skills::getLevel));
        joe.day(1, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, joe.balance);
        assertEquals(20, joe.skills.getLevel(STRENGTH));
        assertTrue(joe.plan.endedForFunds());
        assertThrows(IllegalArgumentException.class, () -> joe.plan.schedule(Map.of(STRENGTH, -1), 2));
    }

    /** Verifies one payment covers the configured cycle and renews on its first uncovered day. */
    @Test
    void paymentCoversConfiguredCycle()
    {
        final Worker joe = new Worker(20);
        joe.plan.schedule(Map.of(STRENGTH, 2), 0);
        assertTrue(joe.day(1, 1000, 5));
        assertEquals(2_000, 100_000 - joe.balance);
        assertEquals(1, joe.plan.cycleStartDay());
        assertEquals(6, joe.plan.nextPaymentDay());
        for (int day = 2; day < 6; day++) assertFalse(joe.day(day, 1000, 5));
        assertEquals(1, joe.withdrawals);
        assertTrue(joe.day(6, 1000, 5));
        assertEquals(2, joe.withdrawals);
        assertEquals(96_000, joe.balance);
    }
}
