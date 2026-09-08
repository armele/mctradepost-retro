package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.minecolonies.api.entity.citizen.Skill;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.ToIntFunction;

import static com.minecolonies.api.util.constant.CitizenConstants.MAX_CITIZEN_LEVEL;

/** A citizen's next-day instructions and the exact adjustments already present in their saved skills. */
public final class IncentivePlan
{
    public static final String TAG_CITIZEN_UUID = "citizenUuid";
    private static final String TAG_LAST_PROCESSED_DAY = "lastProcessedDay";
    private static final String TAG_STARTS_ON_DAY = "startsOnDay";
    private static final String TAG_CYCLE_START_DAY = "cycleStartDay";
    private static final String TAG_NEXT_PAYMENT_DAY = "nextPaymentDay";
    private static final String TAG_ENDED_FOR_FUNDS = "endedForFunds";
    private static final String TAG_LIMITED_BY_CAP = "limitedByCap";
    private static final String TAG_REQUESTED = "requested";
    private static final String TAG_APPLIED = "applied";

    private final UUID citizenUuid;
    private final Map<Skill, Integer> requested = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> applied = new EnumMap<>(Skill.class);
    private int lastProcessedDay = -1;
    private int startsOnDay;
    private int cycleStartDay = -1;
    private int nextPaymentDay;
    private boolean endedForFunds;
    private boolean limitedByCap;

    /**
     * Creates an empty plan bound to a citizen's persistent identity.
     * @param citizenUuid citizen identity, used to detect reused colony-local IDs
     */
    public IncentivePlan(final UUID citizenUuid)
    {
        this.citizenUuid = citizenUuid;
    }

    /** @return the persistent identity of the citizen owning this plan */
    public UUID citizenUuid() { return citizenUuid; }

    /**
     * Gets the requested next-day adjustment, including amounts retained after a funding failure.
     * @param skill skill to inspect
     * @return requested bonus, or zero
     */
    public int requested(final Skill skill) { return requested.getOrDefault(skill, 0); }

    /**
     * Gets the adjustment already included in the citizen's stored level.
     * @param skill skill to inspect
     * @return recorded applied bonus, or zero
     */
    public int applied(final Skill skill) { return applied.getOrDefault(skill, 0); }

    /** @return whether insufficient funds stopped this plan until the player saves it again */
    public boolean endedForFunds() { return endedForFunds; }

    /** @return whether the colony per-cycle incentive cap prevented this plan's most recent due payment */
    public boolean limitedByCap() { return limitedByCap; }

    /** @return whether any paid adjustments still need to be removed */
    public boolean hasApplied() { return !applied.isEmpty(); }

    /** @return whether this plan should attempt another cycle purchase */
    public boolean isScheduled() { return !endedForFunds && !requested.isEmpty(); }

    /** @return first colony day covered by the current paid cycle, or minus one before the first payment */
    public int cycleStartDay() { return cycleStartDay; }

    /** @return colony day on which the next cycle payment is due */
    public int nextPaymentDay() { return nextPaymentDay; }

    /**
     * Infers the unboosted level while respecting the core minimum level.
     * @param current current stored level, including any applied adjustment
     * @param adjustment recorded incentive adjustment
     * @return level after removing that adjustment, with a minimum of one
     */
    public static int normalLevel(final int current, final int adjustment)
    {
        return Math.max(1, current - adjustment);
    }

    /**
     * Limits a requested bonus to the available space below the core skill cap.
     * @param normal unboosted level
     * @param desired requested bonus
     * @return nonnegative bonus that fits within the cap
     */
    public static int availableBoost(final int normal, final int desired)
    {
        return Math.max(0, Math.min(desired, MAX_CITIZEN_LEVEL - normal));
    }

    /**
     * Estimates the next purchase using current levels and the supplied unit price.
     * @param price economic units per skill point
     * @param currentLevel lookup of stored levels, including applied bonuses
     * @return next purchase cost, or zero for a stopped plan
     */
    public long cycleCost(final int price, final ToIntFunction<Skill> currentLevel)
    {
        if (!isScheduled()) return 0;
        long points = 0;
        for (final Skill skill : Skill.values())
        {
            points += availableBoost(normalLevel(currentLevel.applyAsInt(skill), applied(skill)), requested(skill));
        }
        return points * price;
    }

    /**
     * Replaces next-day instructions without changing today's paid adjustment. Zero points schedules cancellation.
     * @param boosts requested bonuses keyed by skill; omitted skills have no bonus
     * @param currentDay current colony day
     * @throws IllegalArgumentException if a bonus is negative or exceeds the maximum possible adjustment
     */
    public void schedule(final Map<Skill, Integer> boosts, final int currentDay)
    {
        for (final Skill skill : Skill.values())
        {
            final int amount = boosts.getOrDefault(skill, 0);
            if (amount < 0 || amount >= MAX_CITIZEN_LEVEL) throw new IllegalArgumentException("Invalid incentive adjustment");
        }
        requested.clear();
        for (final Skill skill : Skill.values())
        {
            final int amount = boosts.getOrDefault(skill, 0);
            if (amount > 0) requested.put(skill, amount);
        }
        startsOnDay = currentDay + 1;
        endedForFunds = false;
        limitedByCap = false;
    }

    /**
     * Reports whether activation, a requested edit, or renewal is due.
     * @param day current colony day
     * @return whether this plan should be processed once on this day
     */
    public boolean isDue(final int day)
    {
        return day > lastProcessedDay && (limitedByCap || day >= startsOnDay && startsOnDay > cycleStartDay ||
            hasApplied() && day >= nextPaymentDay);
    }

    /**
     * Removes the current adjustment and records that a due payment was blocked by the per-cycle incentive cap.
     * The requested plan remains scheduled for reconsideration at the next dawn.
     * @param day current colony day
     * @param adjust core API operation that adjusts a level without replacing its XP
     * @return whether this plan was due and processed for the supplied day
     */
    public boolean limitForDay(final int day, final BiConsumer<Skill, Integer> adjust)
    {
        if (!isDue(day) && !(day > lastProcessedDay && hasApplied())) return false;
        removeApplied(adjust);
        lastProcessedDay = day;
        limitedByCap = isScheduled();
        return true;
    }

    /**
     * Expires the previous adjustments and purchases at most one pay cycle on the current day.
     * Call on the server thread; skipped days never accumulate debt.
     * @param day current colony day
     * @param price economic units per skill point
     * @param payCycleDays colony days covered by a successful payment
     * @param level lookup of current stored skill levels
     * @param adjust core API operation that adjusts a level without replacing its XP
     * @param withdraw all-or-nothing treasury withdrawal, returning whether payment succeeded
     * @return whether the plan processed a new eligible day
     */
    public boolean processDay(final int day, final int price, final int payCycleDays, final ToIntFunction<Skill> level,
        final BiConsumer<Skill, Integer> adjust, final LongPredicate withdraw)
    {
        if (!isDue(day)) return false;
        removeApplied(adjust);
        lastProcessedDay = day;
        limitedByCap = false;
        if (!isScheduled()) return true;

        final Map<Skill, Integer> next = new EnumMap<>(Skill.class);
        long points = 0;
        for (final Skill skill : Skill.values())
        {
            final int amount = availableBoost(level.applyAsInt(skill), requested(skill));
            if (amount > 0) next.put(skill, amount);
            points += amount;
        }
        if (points == 0) return true;
        if (!withdraw.test(points * price))
        {
            endedForFunds = true;
            return true;
        }
        for (final Map.Entry<Skill, Integer> entry : next.entrySet())
        {
            final int before = level.applyAsInt(entry.getKey());
            adjust.accept(entry.getKey(), entry.getValue());
            final int actual = level.applyAsInt(entry.getKey()) - before;
            if (actual > 0) applied.put(entry.getKey(), actual);
        }
        cycleStartDay = day;
        nextPaymentDay = day + payCycleDays;
        startsOnDay = 0;
        return true;
    }

    /**
     * Removes the recorded bonuses once, preserving permanent level changes and partial XP.
     * @param adjust core API operation for signed level adjustments
     */
    public void removeApplied(final BiConsumer<Skill, Integer> adjust)
    {
        applied.forEach((skill, amount) -> adjust.accept(skill, -amount));
        applied.clear();
    }

    /** @return a new NBT compound containing instructions, applied bonuses, and payment state */
    @SuppressWarnings("null")
    public CompoundTag write()
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_CITIZEN_UUID, citizenUuid);
        tag.putInt(TAG_LAST_PROCESSED_DAY, lastProcessedDay);
        tag.putInt(TAG_STARTS_ON_DAY, startsOnDay);
        tag.putInt(TAG_CYCLE_START_DAY, cycleStartDay);
        tag.putInt(TAG_NEXT_PAYMENT_DAY, nextPaymentDay);
        tag.putBoolean(TAG_ENDED_FOR_FUNDS, endedForFunds);
        tag.putBoolean(TAG_LIMITED_BY_CAP, limitedByCap);
        final CompoundTag desiredTag = new CompoundTag();
        final CompoundTag appliedTag = new CompoundTag();
        requested.forEach((skill, amount) -> desiredTag.putInt(skill.name(), amount));
        applied.forEach((skill, amount) -> appliedTag.putInt(skill.name(), amount));
        tag.put(TAG_REQUESTED, desiredTag);
        tag.put(TAG_APPLIED, appliedTag);
        return tag;
    }

    /**
     * Restores a saved plan without reapplying its bonuses or making a payment.
     * @param tag saved plan containing a citizen UUID
     * @return the restored plan, with adjustments constrained to valid ranges
     */
    @SuppressWarnings("null")
    public static IncentivePlan read(final CompoundTag tag)
    {
        final IncentivePlan plan = new IncentivePlan(tag.getUUID(TAG_CITIZEN_UUID));
        plan.lastProcessedDay = tag.getInt(TAG_LAST_PROCESSED_DAY);
        plan.startsOnDay = tag.getInt(TAG_STARTS_ON_DAY);
        plan.cycleStartDay = tag.contains(TAG_CYCLE_START_DAY) ? tag.getInt(TAG_CYCLE_START_DAY) : -1;
        plan.nextPaymentDay = tag.contains(TAG_NEXT_PAYMENT_DAY) ? tag.getInt(TAG_NEXT_PAYMENT_DAY) : plan.lastProcessedDay + 1;
        plan.endedForFunds = tag.getBoolean(TAG_ENDED_FOR_FUNDS);
        plan.limitedByCap = tag.getBoolean(TAG_LIMITED_BY_CAP);
        for (final Skill skill : Skill.values())
        {
            final int desired = Math.max(0, Math.min(MAX_CITIZEN_LEVEL - 1, tag.getCompound(TAG_REQUESTED).getInt(skill.name())));
            final int applied = Math.max(0, Math.min(MAX_CITIZEN_LEVEL - 1, tag.getCompound(TAG_APPLIED).getInt(skill.name())));
            if (desired > 0) plan.requested.put(skill, desired);
            if (applied > 0) plan.applied.put(skill, applied);
        }
        return plan;
    }
}
