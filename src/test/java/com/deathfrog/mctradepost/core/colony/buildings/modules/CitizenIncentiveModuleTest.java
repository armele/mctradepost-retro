package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.managers.interfaces.ICitizenManager;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.managers.StatisticsManager;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies colony treasury integration, payment ordering, validation, and cleanup. */
class CitizenIncentiveModuleTest
{
    private static final Skill STRENGTH = Skill.Strength;

    /**
     * Creates a minimal API test double without loading a running game world.
     * @param type API interface to implement
     * @param handler implementation of the operations used by the test
     * @param <T> API interface type
     * @return test implementation of the interface
     */
    private static <T> T proxy(final Class<T> type, final InvocationHandler handler)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    /** Colony fixture containing a treasury, citizens, and a Town Hall incentive module. */
    private static final class Town
    {
        int day;
        final StatisticsManager stats = new StatisticsManager();
        final Map<Integer, ICitizenData> citizens = new LinkedHashMap<>();
        final ICitizenManager citizenManager = proxy(ICitizenManager.class, (p, method, args) -> switch (method.getName()) {
            case "getCivilian" -> citizens.get((Integer) args[0]);
            case "getCitizens" -> citizens.values();
            default -> throw new UnsupportedOperationException(method.getName());
        });
        final IColony colony = proxy(IColony.class, (p, method, args) -> switch (method.getName()) {
            case "getCitizenManager" -> citizenManager;
            case "getStatisticsManager" -> stats;
            case "getDay" -> day;
            case "markDirty" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        final IBuilding building = proxy(IBuilding.class, (p, method, args) -> switch (method.getName()) {
            case "getColony" -> colony;
            case "markDirty" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        CitizenIncentiveModule module = newModule();

        /** @return a fresh module bound to the test Town Hall with the default unit price */
        CitizenIncentiveModule newModule()
        {
            final CitizenIncentiveModule result = new CitizenIncentiveModule()
            {
                /** {@inheritDoc} */
                @Override
                protected int pointPrice() { return 1000; }
            };
            result.setBuilding(building);
            return result;
        }

        /**
         * Registers a citizen with a new UUID and normal Strength of twenty.
         * @param id colony-local ID to register or replace
         * @return registered citizen
         */
        ICitizenData addCitizen(final int id)
        {
            final UUID uuid = UUID.randomUUID();
            final CitizenSkillHandler skills = new CitizenSkillHandler();
            skills.incrementLevel(STRENGTH, 19);
            final ICitizenData citizen = proxy(ICitizenData.class, (p, method, args) -> switch (method.getName()) {
                case "getId" -> id;
                case "getUUID" -> uuid;
                case "getCitizenSkillHandler" -> skills;
                case "getColony" -> colony;
                case "markDirty", "getJob" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
            citizens.put(id, citizen);
            return citizen;
        }

        /**
         * Schedules a Strength bonus and asserts that validation succeeds.
         * @param citizen citizen to enroll
         * @param amount requested Strength bonus
         */
        void schedule(final ICitizenData citizen, final int amount)
        {
            assertTrue(module.schedule(citizen.getId(), citizen.getUUID(), Map.of(STRENGTH, amount)));
        }

        /** Runs payroll for the fixture's current colony day. */
        void tick() { module.onColonyTick(colony); }
        /** @return current colony treasury balance */
        int balance() { return stats.getStatTotal("current_balance"); }
    }

    /** Verifies payment priority and paid-day state survive module reloads under limited funds. */
    @Test
    void playerPriorityChoosesWhoGetsPaidAndSurvivesReload()
    {
        final Town town = new Town();
        final var joe = town.addCitizen(1);
        final var jane = town.addCitizen(2);
        town.stats.incrementBy("current_balance", 3000, 0);
        town.schedule(joe, 2);
        town.schedule(jane, 2);
        assertTrue(town.module.movePriority(2, -1));
        final CompoundTag saved = new CompoundTag();
        town.module.serializeNBT(null, saved);
        town.module = town.newModule();
        town.module.deserializeNBT(null, saved);
        town.tick();
        assertEquals(3000, town.balance());
        town.day = 1;
        town.tick();
        assertEquals(22, jane.getCitizenSkillHandler().getLevel(STRENGTH));
        assertEquals(20, joe.getCitizenSkillHandler().getLevel(STRENGTH));
        assertEquals(1000, town.balance());
        assertEquals(2000, town.stats.getStatTotal(CitizenIncentiveModule.EXPENSE_STAT));
        town.module.serializeNBT(null, saved);
        town.module = town.newModule();
        town.module.deserializeNBT(null, saved);
        town.tick();
        assertEquals(1000, town.balance());
        assertEquals(22, jane.getCitizenSkillHandler().getLevel(STRENGTH));
        town.day = 2;
        town.tick();
        assertEquals(20, jane.getCitizenSkillHandler().getLevel(STRENGTH));
        assertEquals(1000, town.balance());
    }

    /** Verifies Town Hall removal preserves earned levels and cannot subtract bonuses twice. */
    @Test
    void destroyingTownHallRemovesAdjustmentsOnceWithoutOverwritingGains()
    {
        final Town town = new Town();
        final var joe = town.addCitizen(1);
        town.stats.incrementBy("current_balance", 5000, 0);
        town.schedule(joe, 5);
        town.day = 1;
        town.tick();
        joe.getCitizenSkillHandler().incrementLevel(STRENGTH, 1);
        town.module.onDestroyed();
        town.module.onDestroyed();
        assertEquals(21, joe.getCitizenSkillHandler().getLevel(STRENGTH));
        assertEquals(0, town.balance());
        town.day = 2;
        town.tick();
        assertEquals(21, joe.getCitizenSkillHandler().getLevel(STRENGTH));
    }

    /** Verifies permanent-departure cleanup removes the boost and stops subsequent payments. */
    @Test
    void departureCleanupIsImmediateAndIdempotent()
    {
        final Town town = new Town();
        final var joe = town.addCitizen(1);
        town.stats.incrementBy("current_balance", 10000, 0);
        town.schedule(joe, 5);
        town.day = 1;
        town.tick();
        town.module.releaseCitizen(joe);
        town.module.releaseCitizen(joe);
        assertEquals(20, joe.getCitizenSkillHandler().getLevel(STRENGTH));
        town.day = 2;
        town.tick();
        assertEquals(5000, town.balance());
    }

    /** Verifies invalid amounts and stale citizen identities cannot mutate payroll state. */
    @Test
    void serverRejectsNegativeOverCapAndWrongCitizenRequests()
    {
        final Town town = new Town();
        final var joe = town.addCitizen(1);
        assertFalse(town.module.schedule(1, joe.getUUID(), Map.of(STRENGTH, -1)));
        assertFalse(town.module.schedule(1, joe.getUUID(), Map.of(STRENGTH, 80)));
        assertFalse(town.module.schedule(1, UUID.randomUUID(), Map.of(STRENGTH, 1)));
        assertFalse(town.module.schedule(9, joe.getUUID(), Map.of(STRENGTH, 1)));
        assertFalse(town.module.movePriority(1, Integer.MIN_VALUE));
        assertEquals(0, town.module.revision());
    }

    /** Verifies a replacement citizen cannot inherit adjustments from a reused colony-local ID. */
    @Test
    void citizenIdReuseDoesNotApplyOrSubtractAnOldCitizensBoosts()
    {
        final Town town = new Town();
        final var joe = town.addCitizen(1);
        town.stats.incrementBy("current_balance", 10000, 0);
        town.schedule(joe, 5);
        town.day = 1;
        town.tick();
        final var replacement = town.addCitizen(1);
        town.module.releaseCitizen(replacement);
        town.day = 2;
        town.tick();
        assertEquals(20, replacement.getCitizenSkillHandler().getLevel(STRENGTH));
        assertEquals(5000, town.balance());
    }
}
