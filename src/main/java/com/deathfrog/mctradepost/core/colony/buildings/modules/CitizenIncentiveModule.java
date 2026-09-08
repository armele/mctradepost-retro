package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.event.CitizenIncentivesPaidEvent;
import com.deathfrog.mctradepost.api.util.EconomicValueFormatter;
import com.deathfrog.mctradepost.api.util.EconomicConstants;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.eventbus.EventBus;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingRemovedModEvent;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenRemovedModEvent;
import com.minecolonies.api.util.MessageUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Town Hall payroll. Map order is the player-controlled payment priority. */
public class CitizenIncentiveModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule, IBuildingEventsModule
{
    public static final String EXPENSE_STAT = "incentive_expenses";
    public static final String TAG_INCENTIVES = "incentives";
    public static final String TAG_CITIZEN_ID = "citizenId";
    private static final String TAG_REVISION = "revision";
    private static final String TAG_SCHEMA_VERSION = "schemaVersion";
    private static final int VIEW_SYNC_INTERVAL = 5;
    private final Map<Integer, IncentivePlan> plans = new LinkedHashMap<>();
    private long revision;
    private int syncTicks;

    /** @return revision used to reject edits based on outdated payroll state */
    public long revision() { return revision; }

    /** @return the configured number of economic units charged per skill point */
    protected int pointPrice() { return MCTPConfig.incentiveCostPerSkillPoint.get(); }

    /** @return configured number of colony days covered by one incentive payment */
    protected int payCycleDays() { return MCTPConfig.incentivePayCycleDays.get(); }

    /** @return colony-wide per-cycle incentive cap supplied by the Incentive Plans research effect */
    protected long incentiveCap()
    {
        return Math.max(0L, (long) building.getColony().getResearchManager().getResearchEffects()
            .getEffectStrength(MCTPResearchConstants.INCENTIVE_PLANS));
    }

    /** Advances the edit revision and marks the module and colony for persistence and synchronization. */
    private void changed()
    {
        revision++;
        markDirty();
        building.getColony().markDirty();
    }

    /**
     * Validates and saves the citizen's next-dawn instructions.
     * @param id colony-local citizen ID
     * @param uuid persistent citizen identity to guard against ID reuse
     * @param boosts requested nonnegative skill adjustments
     * @return whether the citizen and all requested adjustments were valid
     */
    public boolean schedule(final int id, final UUID uuid, final Map<Skill, Integer> boosts)
    {
        final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(id);
        if (citizen == null || !citizen.getUUID().equals(uuid)) return false;
        final IncentivePlan existing = plans.get(id);
        if (existing != null && !existing.citizenUuid().equals(uuid)) return false;
        for (final Skill skill : Skill.values())
        {
            final int normal = IncentivePlan.normalLevel(citizen.getCitizenSkillHandler().getLevel(skill), existing == null ? 0 : existing.applied(skill));
            final int amount = boosts.getOrDefault(skill, 0);
            if (amount < 0 || amount != IncentivePlan.availableBoost(normal, amount)) return false;
        }
        if (!fitsSalaryCap(id, boosts)) return false;
        final IncentivePlan plan = plans.computeIfAbsent(id, ignored -> new IncentivePlan(uuid));
        plan.schedule(boosts, building.getColony().getDay());
        changed();
        return true;
    }

    /**
     * Checks a replacement package against the current aggregate research cap.
     * Zero-cost cancellation remains valid while the feature is locked.
     * @param id colony-local citizen ID being replaced
     * @param boosts proposed adjustments
     * @return whether the resulting scheduled payroll fits the current cap
     */
    boolean fitsSalaryCap(final int id, final Map<Skill, Integer> boosts)
    {
        final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(id);
        if (citizen == null) return false;
        final IncentivePlan existing = plans.get(id);
        final long proposedCost = requestedCost(citizen, existing, boosts);
        return proposedCost == 0 || proposedCost + scheduledCostExcluding(id) <= incentiveCap();
    }

    /**
     * Calculates a proposed citizen package from normal levels without mutating its current plan.
     * @param citizen citizen whose package is being evaluated
     * @param existing existing plan whose applied adjustments establish normal levels, or null
     * @param boosts requested adjustments
     * @return proposed per-cycle cost calculated as a long
     */
    private long requestedCost(final ICitizenData citizen, final IncentivePlan existing, final Map<Skill, Integer> boosts)
    {
        long points = 0;
        for (final Skill skill : Skill.values())
        {
            final int applied = existing == null ? 0 : existing.applied(skill);
            final int normal = IncentivePlan.normalLevel(citizen.getCitizenSkillHandler().getLevel(skill), applied);
            points += IncentivePlan.availableBoost(normal, boosts.getOrDefault(skill, 0));
        }
        return points * pointPrice();
    }

    /**
     * Totals active scheduled commitments other than the citizen currently being edited.
     * @param excludedId colony-local citizen ID to omit
     * @return aggregate per-cycle cost calculated as a long
     */
    private long scheduledCostExcluding(final int excludedId)
    {
        long total = 0;
        for (final Map.Entry<Integer, IncentivePlan> entry : plans.entrySet())
        {
            if (entry.getKey() == excludedId) continue;
            final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(entry.getKey());
            if (citizen != null && citizen.getUUID().equals(entry.getValue().citizenUuid()))
                total += entry.getValue().cycleCost(pointPrice(), citizen.getCitizenSkillHandler()::getLevel);
        }
        return total;
    }

    /**
     * Moves a saved plan one position in the payment priority order.
     * @param id citizen whose payment priority changes
     * @param direction minus one for earlier payment or plus one for later payment
     * @return whether the plan moved
     */
    public boolean movePriority(final int id, final int direction)
    {
        final List<Integer> ids = new ArrayList<>(plans.keySet());
        final int index = ids.indexOf(id);
        final int target = index + direction;
        if (Math.abs(direction) != 1 || index < 0 || target < 0 || target >= ids.size()) return false;
        java.util.Collections.swap(ids, index, target);
        final Map<Integer, IncentivePlan> reordered = new LinkedHashMap<>();
        ids.forEach(key -> reordered.put(key, plans.get(key)));
        plans.clear();
        plans.putAll(reordered);
        changed();
        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    public void onColonyTick(final IColony colony)
    {
        boolean dirty = false;
        final List<CitizenIncentivesPaidEvent.Payment> payments = new ArrayList<>();
        long remainingCapacity = incentiveCap();
        boolean incentiveCapReached = remainingCapacity <= 0;
        final Iterator<Map.Entry<Integer, IncentivePlan>> iterator = plans.entrySet().iterator();
        while (iterator.hasNext())
        {
            final Map.Entry<Integer, IncentivePlan> entry = iterator.next();
            final ICitizenData citizen = colony.getCitizenManager().getCivilian(entry.getKey());
            final IncentivePlan plan = entry.getValue();
            if (citizen == null || !citizen.getUUID().equals(plan.citizenUuid()))
            {
                iterator.remove();
                dirty = true;
                continue;
            }
            final long packageCost = plan.cycleCost(pointPrice(), citizen.getCitizenSkillHandler()::getLevel);
            if (incentiveCapReached || packageCost > remainingCapacity)
            {
                if (plan.isScheduled() && plan.limitForDay(colony.getDay(), citizen.getCitizenSkillHandler()::incrementLevel))
                {
                    refreshCitizen(citizen);
                    dirty = true;
                    incentiveCapReached = plan.isScheduled();
                }
            }
            else
            {
                final long[] charged = {0};
                remainingCapacity -= packageCost;
                if (plan.processDay(colony.getDay(), pointPrice(), payCycleDays(),
                    citizen.getCitizenSkillHandler()::getLevel, citizen.getCitizenSkillHandler()::incrementLevel,
                    amount -> {
                        final boolean withdrawn = withdraw(colony, amount);
                        if (withdrawn) charged[0] = amount;
                        return withdrawn;
                    }))
                {
                    if (charged[0] > 0) payments.add(new CitizenIncentivesPaidEvent.Payment(citizen, charged[0]));
                    refreshCitizen(citizen);
                    dirty = true;
                }
            }
            if (!plan.isScheduled() && !plan.hasApplied() && !plan.endedForFunds())
            {
                iterator.remove();
                dirty = true;
            }
        }
        if (!payments.isEmpty()) announcePayments(colony, payments);
        if (dirty) changed();
        // Refresh treasury, newly arrived citizens and naturally changing levels while a player has the tab open.
        if (++syncTicks >= VIEW_SYNC_INTERVAL)
        {
            syncTicks = 0;
            markDirty();
        }
    }

    /**
     * Posts the public payroll event and sends one aggregate MineColonies message to colony message recipients.
     * @param colony colony whose payroll completed
     * @param payments successful payments in priority order
     */
    private static void announcePayments(final IColony colony, final List<CitizenIncentivesPaidEvent.Payment> payments)
    {
        final CitizenIncentivesPaidEvent event = new CitizenIncentivesPaidEvent(colony, payments);
        NeoForge.EVENT_BUS.post(event);
        MessageUtils.format("mctradepost.incentives.paid", EconomicValueFormatter.compact(event.getTotalAmount()))
            .sendTo(colony).forAllPlayers();
    }

    /**
     * Debits one complete package and records its expense without allowing a negative treasury.
     * @param colony colony funding the package
     * @param amount full package price, calculated as a long to avoid overflow
     * @return whether the entire amount was withdrawn
     */
    private boolean withdraw(final IColony colony, final long amount)
    {
        // The existing treasury is an int-valued colony statistic. Calculate prices as longs first.
        final IStatisticsManager  stats = colony.getStatisticsManager();
        if (amount <= 0 || amount > Integer.MAX_VALUE || stats.getStatTotal(EconomicConstants.CURRENT_BALANCE) < amount) return false;
        stats.incrementBy(EconomicConstants.CURRENT_BALANCE, -(int) amount, colony.getDay());
        stats.incrementBy(EXPENSE_STAT, (int) amount, colony.getDay());
        colony.markDirty();
        return true;
    }

    /**
     * Synchronizes changed skills and refreshes job attributes derived from those skills.
     * @param citizen citizen whose stored levels changed
     */
    private static void refreshCitizen(final ICitizenData citizen)
    {
        citizen.markDirty(0);
        // Refresh cached job attributes (guard health, courier movement, etc.), without level-up particles.
        if (citizen.getJob() != null) citizen.getJob().onLevelUp();
    }

    /**
     * Removes paid adjustments and the citizen's plan immediately on permanent departure.
     * Integrations transferring citizens should call this before copying their data.
     * @param citizen departing citizen
     */
    @SuppressWarnings("null")
    public void releaseCitizen(final ICitizenData citizen)
    {
        final IncentivePlan plan = plans.get(citizen.getId());
        if (plan == null || !plan.citizenUuid().equals(citizen.getUUID())) return;
        plan.removeApplied(citizen.getCitizenSkillHandler()::incrementLevel);
        plans.remove(citizen.getId());
        refreshCitizen(citizen);
        changed();
    }

    /**
     * Gets a skill level with this feature's recorded adjustment removed.
     * @param citizen citizen whose skill is being evaluated
     * @param skill skill to inspect
     * @return current normal level, with a minimum of one
     */
    public static int getNormalLevel(final ICitizenData citizen, final Skill skill)
    {
        final IBuilding townHall = citizen.getColony().getServerBuildingManager().getTownHall();
        int applied = 0;
        if (townHall != null && townHall.hasModule(CitizenIncentiveModule.class))
        {
            final IncentivePlan plan = townHall.getModule(CitizenIncentiveModule.class).plans.get(citizen.getId());
            if (plan != null && plan.citizenUuid().equals(citizen.getUUID())) applied = plan.applied(skill);
        }
        return IncentivePlan.normalLevel(citizen.getCitizenSkillHandler().getLevel(skill), applied);
    }

    /** {@inheritDoc} */
    @Override
    public void onDestroyed()
    {
        for (final int id : new ArrayList<>(plans.keySet()))
        {
            final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(id);
            if (citizen != null) releaseCitizen(citizen);
        }
        plans.clear();
        changed();
    }

    /** Registers core event handlers for pre-grave death cleanup and Town Hall removal. Call once during mod setup. */
    public static void registerLifecycleHooks()
    {
        final EventBus bus = IMinecoloniesAPI.getInstance().getEventBus();
        bus.subscribe(CitizenRemovedModEvent.class, event -> {
            // KILLED is emitted before core grave creation. Unloading/discarding an entity is not a payroll cancellation.
            if (event.getRemovalReason() != Entity.RemovalReason.KILLED || event.getColony() == null) return;
            final IBuilding townHall = event.getColony().getServerBuildingManager().getTownHall();
            final ICitizenData citizen = event.getColony().getCitizenManager().getCivilian(event.getCitizenId());
            if (citizen != null && townHall != null && townHall.hasModule(CitizenIncentiveModule.class))
                townHall.getModule(CitizenIncentiveModule.class).releaseCitizen(citizen);
        });
        bus.subscribe(BuildingRemovedModEvent.class, event -> {
            if (event.getBuilding().hasModule(CitizenIncentiveModule.class))
                event.getBuilding().getModule(CitizenIncentiveModule.class).onDestroyed();
        });
    }

    /** {@inheritDoc} */
    @Override
    public void serializeNBT(final HolderLookup.Provider provider, final CompoundTag tag)
    {
        final ListTag list = new ListTag();
        plans.forEach((id, plan) -> {
            final CompoundTag entry = plan.write();
            entry.putInt(TAG_CITIZEN_ID, id);
            list.add(entry);
        });
        tag.put(TAG_INCENTIVES, list);
        tag.putLong(TAG_REVISION, revision);
        tag.putInt(TAG_SCHEMA_VERSION, 2);
    }

    /** {@inheritDoc} */
    @Override
    public void deserializeNBT(final HolderLookup.Provider provider, final CompoundTag tag)
    {
        plans.clear();
        final ListTag list = tag.getList(TAG_INCENTIVES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID(IncentivePlan.TAG_CITIZEN_UUID)) plans.put(entry.getInt(TAG_CITIZEN_ID), IncentivePlan.read(entry));
        }
        revision = tag.getLong(TAG_REVISION);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf)
    {
        buf.writeLong(revision);
        buf.writeInt(pointPrice());
        buf.writeInt(payCycleDays());
        buf.writeInt(building.getColony().getDay());
        buf.writeInt(building.getColony().getStatisticsManager().getStatTotal(EconomicConstants.CURRENT_BALANCE));
        buf.writeLong(incentiveCap());
        final CompoundTag tag = new CompoundTag();
        serializeNBT(buf.registryAccess(), tag);
        buf.writeNbt(tag);
        final List<ICitizenData> citizens = building.getColony().getCitizenManager().getCitizens();
        buf.writeInt(citizens.size());
        for (final ICitizenData citizen : citizens)
        {
            buf.writeInt(citizen.getId());
            buf.writeUUID(citizen.getUUID());
            for (final Skill skill : Skill.values()) buf.writeInt(citizen.getCitizenSkillHandler().getLevel(skill));
        }
    }
}
