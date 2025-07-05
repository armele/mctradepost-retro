package com.deathfrog.mctradepost.core.entity.ai.workers.minimal;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer.VacationState;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.SittingEntity;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.network.messages.client.CircleParticleEffectMessage;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_BURNOUT;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

public class EntityAIBurnoutTask
{
    public static final Logger LOGGER = LogUtils.getLogger();

    protected EntityCitizen citizen;
    protected ICitizenData citizenData;
    protected Vacationer vacationTracker = null;
    protected BlockPos seatLocation = null;
    protected int dayLastResisted = 0;

    /**
     * Worker status icon
     */
    private final static VisibleCitizenStatus VACATION =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/vacation/default.png"),
            "com.mctradepost.gui.visiblestatus.needvacation");

    /* What do we consider maximum advertizing saturation? 
    *  This is the point at which a citizen is virtually guaranteed to burn out. 
    */

    protected static final int MAX_AD_SATURATION = MCTPConfig.maxAdSaturation.get();                        // Denominator to determine
                                                                                                            // vacation chance.
    protected int VACATION_SUSCEPTIBILITY_THRESHOLD = MCTPConfig.vacationSusceptibilityThreshold.get();     // Under this stat level
                                                                                                            // citizen is more
                                                                                                            // susceptible to
                                                                                                            // advertising.
    protected int VACATION_IMMUNITY_THRESHOLD = MCTPConfig.vacationImmunityThreshold.get();                 // Under this stat level
                                                                                                            // citizen is more
                                                                                                            // susceptible to
                                                                                                            // advertising.
    protected int VACATION_HEALING = MCTPConfig.vacationHealing.get();                                      // The number of skill-ups
                                                                                                            // a single vacation will
                                                                                                            // get you. (Also used to
                                                                                                            // scale XP applied)
    protected double MAX_VACATION_CHANCE = MCTPConfig.vacationMaxChance.get();                              // The maximum chance of a
                                                                                                            // vacation.

    /**
     * Min distance to resort before trying to find a relaxation station.
     */
    private static final int MIN_DIST_TO_RESORT = 10;

    private static final int ENTITY_AI_BURNOUT_TICKRATE = 1000;

    public final static String NO_RESORT = "com.mctradepost.resort.no_resort";
    public final static String GREAT_VACATION = "com.mctradepost.resort.great_vacation";
    public final static String NEED_VACATION = "com.mctradepost.resort.need_vacation";
    public final static String BURNOUT = "burnout";

    /* How much advertizing has this citizen seen? */
    protected int adSaturationLevel = 0;

    private Skill skillToHeal = null;

    /**
     * The different types of AIStates related to going on vacation.
     */
    public enum VacationAIState implements IAIState
    {
        CHECK_FOR_CURE, GO_TO_HUT, SEARCH_RESORT, GO_TO_RESORT, WAIT_FOR_CURE, FIND_EMPTY_STATION, WANDER, APPLY_CURE;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    static
    {
    }

    /* Modelled after EntityAISickTask */
    public EntityAIBurnoutTask(EntityCitizen citizen)
    {
        // Implement burnout AI and integrate it into citizen states.
        this.citizen = citizen;
        this.citizenData = citizen.getCitizenData();
        final IColony colony = citizenData.getColony();

        this.skillToHeal = determineSkillToHeal();

        if (skillToHeal == null)
        {
            LOGGER.warn("Citizen " + citizen.getName() + " has no skill to heal.");
        }

        ITickRateStateMachine<IState> ai = citizen.getCitizenAI();

        ai.addTransition(
            new AIEventTarget<IState>(AIBlockingEventType.EVENT, this::isBurntOut, this::goOnVacation, ENTITY_AI_BURNOUT_TICKRATE));
        ai.addTransition(new TickingTransition<>(VacationAIState.CHECK_FOR_CURE, () -> true, this::checkForCure, 20));
        ai.addTransition(new TickingTransition<>(VacationAIState.SEARCH_RESORT, () -> true, this::searchResort, 20));
        ai.addTransition(new TickingTransition<>(VacationAIState.GO_TO_RESORT, () -> true, this::goToResort, 20));
        ai.addTransition(new TickingTransition<>(VacationAIState.FIND_EMPTY_STATION, () -> true, this::findEmptyStation, 20));
        ai.addTransition(new TickingTransition<>(VacationAIState.WAIT_FOR_CURE, () -> true, this::waitForCure, 20));
        ai.addTransition(new TickingTransition<>(VacationAIState.APPLY_CURE, () -> true, this::applyCure, 20));
        ai.addTransition(new TickingTransition<>(VacationAIState.WANDER, () -> true, this::wander, 20));
    }

    /**
     * Determines the skill to heal for the burnout AI. determines the adverse skill to the primary skill of the module and sets that
     * as the skill to heal.
     */
    protected Skill determineSkillToHeal()
    {
        final IColony colony = citizenData.getColony();
        final IJob<?> job = citizen.getCitizenData().getJob();

        final IBuilding citizenWorkBuilding = job.getWorkBuilding();
        final WorkerBuildingModule module = citizenWorkBuilding.getModuleMatching(WorkerBuildingModule.class,
            m -> m.getAssignedCitizen().contains(citizen.getCitizenData()));
        Skill adverse = null;

        if (module != null)
        {
            final Skill primary = module.getPrimarySkill();
            adverse = primary.getAdverse();
        }

        return adverse;
    }

    /**
     * Gets the best resort for the citizen to visit. If all resorts are full, this may be null.
     * 
     * @return the best resort for the citizen to visit
     */
    protected BuildingResort getBestResort()
    {
        BuildingResort resort = null;

        // The best resort is the one where the reservation exists.
        if (vacationTracker != null && vacationTracker.getResort() != null)
        {
            resort = vacationTracker.getResort();
        }
        else
        {
            // Otherwise, find the closest with space. Start with the closest.
            BlockPos bestResortLocation = citizenData.getColony().getBuildingManager().getBestBuilding(citizen, BuildingResort.class);
            resort = (BuildingResort) citizenData.getColony().getBuildingManager().getBuilding(bestResortLocation);

            // Fall back to searching all resorts if the closest is full.
            if (resort == null || resort.isFull())
            {
                resort = null;

                for (IBuilding building : citizenData.getColony().getBuildingManager().getBuildings().values())
                {
                    if (!(building instanceof BuildingResort))
                    {
                        continue;
                    }

                    resort = (BuildingResort) building;
                    if (resort != null && !resort.isFull())
                    {
                        break;
                    }
                }
            }
        }

        return (BuildingResort) resort;
    }

    /**
     * Retrieves the location of the best resort for the citizen to visit.
     *
     * @return the position of the best resort, or null if no suitable resort is found.
     */
    protected BlockPos getBestResortPosition()
    {
        BuildingResort resort = getBestResort();
        return resort == null ? null : resort.getPosition();
    }

    /**
     * Calculates the advertising power for the resort at a given position. The advertising power is determined by the resort's
     * building level and a stat multiplier based on the citizen's skill level.
     * 
     * @param pos the position of the resort
     * @return the calculated advertising power
     */
    protected int calcAdvertisingPower()
    {
        final IBuilding resort = getBestResort();

        if (resort == null || skillToHeal == null)
        {
            return 0;
        }

        double statmultiplier = VACATION_SUSCEPTIBILITY_THRESHOLD / citizenData.getCitizenSkillHandler().getLevel(skillToHeal);

        int adPower = (int) Math.max(resort.getBuildingLevel() * statmultiplier, 1);

        return adPower;
    }

    /**
     * Handler for any situation which prevents a citizen from going on vacation when they would like to.
     *
     * @return the AI state to transition to, either START_WORKING or VACATIONING.
     */
    public IState cannotVacation()
    {
        // TODO: impact citizen happiness
        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Could not find a vacation location for {}.", citizen.getName()));
        reset();
        return START_WORKING;
    }

    /**
     * If the citizen is not already on vacation, this method sets up a new vacation for them and returns the appropriate AI state. If
     * the citizen is already on vacation, this method returns their current AI state.
     * 
     * @return the AI state to go to
     */
    private IState goOnVacation()
    {
        // If there is an existing vacation tracker, make sure it is still valid.
        if (vacationTracker != null && vacationTracker.getResort() != null)
        {
            // If the vacation tracker is no longer valid, reset it. This could happen if resorts are deconstructed, etc.
            if (vacationTracker.getResort().getGuestFile(citizen.getCivilianID()) == null)
            {
                TraceUtils.dynamicTrace(TRACE_BURNOUT,
                    () -> LOGGER.info("Removing out of date vacation tracker for {} (state {}).", citizen.getName()));
                vacationTracker.reset();
                vacationTracker = null;
            }
        }

        // If they are not already on vacation, set up their vacation. Otherwise, return their current state.
        if (vacationTracker == null || vacationTracker.getState() == Vacationer.VacationState.CHECKED_OUT)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Setting up a new vacation for {}.", citizen.getName()));
            BuildingResort resort = getBestResort();

            if (resort == null)
            {
                return cannotVacation();
            }

            int currentSkillLevel = citizenData.getCitizenSkillHandler().getLevel(skillToHeal);

            vacationTracker = new Vacationer(citizen.getCivilianID(), skillToHeal);
            vacationTracker.setTargetLevel(currentSkillLevel + VACATION_HEALING);
            vacationTracker.setResort(resort);

            if (resort.makeReservation(vacationTracker))
            {
                citizen.getCitizenData().setVisibleStatus(VACATION);
                adSaturationLevel = 0;
                return VacationAIState.SEARCH_RESORT;
            }
            else
            {
                return cannotVacation();
            }
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.info("Continuing the vacation for {} (state {}).", citizen.getName(), citizen.getCitizenAI().getState()));
        }

        return VacationAIState.SEARCH_RESORT;
    }

    /**
     * Determines whether the citizen is burnt out based on a random chance.
     *
     * @return true if the citizen is burnt out, false otherwise.
     */
    private boolean isBurntOut()
    {
        if (MAX_AD_SATURATION < 0)
        {    // This logic is disabled.
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Burnout is disabled in the MAX_AD_SATURATION config."));
            reset();
            return false;
        }

        if (citizenData.getCitizenDiseaseHandler().isSick())
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("No vacations while sick."));
            reset();
            return false;
        }

        if (citizenData.getWorkBuilding() instanceof BuildingResort)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Resort workers don't take vacations."));
            reset();
            return false;
        }

        if (vacationTracker != null && vacationTracker.getState() != Vacationer.VacationState.CHECKED_OUT)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Continuing an interrupted vacation..."));
            return true;
        }

        if (isResistedAds(citizenData.getColony().getDay()))
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("{} resisted ads {}. Today is {}...", 
                citizen.getName(), dayLastResisted, citizenData.getColony().getDay()));
            return false;
        }

        // They didn't have a skill to heal when this AI was initialized - double-check again.
        if (skillToHeal == null)
        {
            skillToHeal = determineSkillToHeal();
        }

        int currentSkillLevel = 0;

        // This probably means they are unemployed - no need for a vacation!
        if (skillToHeal == null)
        {
            LOGGER.warn("Citizen " + citizen.getName() + " has no skill to heal.");
            reset();
            return false;
        }
        else
        {
            currentSkillLevel = citizenData.getCitizenSkillHandler().getLevel(skillToHeal);
            if (currentSkillLevel >= VACATION_IMMUNITY_THRESHOLD)
            {
                TraceUtils.dynamicTrace(TRACE_BURNOUT,
                    () -> LOGGER.info("This citizen doesn't need a vacation - they're doing great: {}", citizen.getName()));

                reset();
                return false;
            }
        }

        adSaturationLevel += calcAdvertisingPower();

        final IColony colony = citizenData.getColony();
        final double burnoutChance = Math.min(MAX_VACATION_CHANCE, Math.ceil((double) adSaturationLevel / (double) MAX_AD_SATURATION));

        // The higher the burnout chance the more likely the citizen will burn out.
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll <= burnoutChance)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.info("Someone needs a vacation! {}. To fix: {} (rolled {} with a chance of {})",
                    citizen.getName(),
                    skillToHeal,
                    roll,
                    burnoutChance));

            return true;
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.info("Someone is happy under their capitalist yoke! {}. (rolled {} with a chance of {})",
                    citizen.getName(),
                    roll,
                    burnoutChance));

            // Set this resistance flag here to prevent burnout checks until another day.
            setResistedAds(citizenData.getColony().getDay());
        }

        // Let ad saturation build up (reset tracker but not reset the ad saturation level)
        if (vacationTracker != null) 
        {
            vacationTracker.reset();
            vacationTracker = null;
        }
        return false;
    }

    /**
     * Checks if the citizen has the cure in the inventory and makes a decision based on that.
     *
     * @return the next state to go to.
     */
    private IState checkForCure()
    {
        if (vacationTracker != null)
        {
            for (final ItemStorage cure : vacationTracker.getRemedyItems())
            {
                TraceUtils.dynamicTrace(TRACE_BURNOUT,
                    () -> LOGGER.info("Vacationer {} is checking for cures - looking for item {}.", citizen, cure.getItem()));

                final int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(citizen, Vacationer.hasRemedyItem(cure));
                if (slot == -1)
                {
                    TraceUtils.dynamicTrace(TRACE_BURNOUT,
                        () -> LOGGER.info("Vacationer {} item {} not found.  Keep waiting.", citizen, cure.getItem()));
                    return VacationAIState.WAIT_FOR_CURE;
                }
            }
        }
        else
        {
            // Their burnout has been cleared...
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} tracker has been cleared.", citizen));
            reset();
            return AIWorkerState.START_WORKING;
        }

        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} has everything they need.  Apply it next!", citizen));

        return VacationAIState.APPLY_CURE;
    }

    /**
     * Do a bit of wandering.
     *
     * @return start over.
     */
    public VacationAIState wander()
    {
        EntityNavigationUtils.walkToRandomPos(citizen, MIN_DIST_TO_RESORT * 2, 0.6D);
        seatLocation = null;
        return VacationAIState.WAIT_FOR_CURE;
    }

    /**
     * Search for a resort where the citizen can relax.
     *
     * @return the next state to go to.
     */
    private IState searchResort()
    {
        // If our vacation has been cancelled (for example, with the vacationclear command) go back to work!
        if (vacationTracker == null || vacationTracker.getState().equals(VacationState.CHECKED_OUT))
        {
            reset();
            return AIWorkerState.START_WORKING;
        }
        
        final IColony colony = citizenData.getColony();
        BuildingResort resort = getBestResort();

        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} is searching for a resort.", citizen));

        if (resort == null || resort.getPosition() == null)
        {
            citizenData.triggerInteraction(
                new StandardInteraction(Component.translatable(NO_RESORT, vacationTracker.name(), vacationTracker.getRemedyString()),
                    Component.translatable(NO_RESORT),
                    ChatPriority.BLOCKING));

            return VacationAIState.WANDER;
        }
        else if (vacationTracker != null)
        {
            citizenData.triggerInteraction(new StandardInteraction(
                Component.translatable(NEED_VACATION, vacationTracker.name(), vacationTracker.getRemedyString()),
                Component.translatable(NEED_VACATION),
                ChatPriority.BLOCKING));
        }

        vacationTracker.setResort(resort);

        return VacationAIState.GO_TO_RESORT;
    }

    /**
     * Go to the previously found placeToPath to get cure.
     *
     * @return the next state to go to.
     */
    private IState goToResort()
    {
        // If our vacation has been cancelled (for example, with the vacationclear command) go back to work!
        if (vacationTracker == null || vacationTracker.getState().equals(VacationState.CHECKED_OUT))
        {
            reset();
            return AIWorkerState.START_WORKING;
        }

        BlockPos bestResortLocation = getBestResortPosition();

        if (bestResortLocation == null)
        {
            LOGGER.warn("Vacationer {} has lost track of their resort.", citizen);
            return VacationAIState.SEARCH_RESORT;
        }

        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} is walking to the resort.", citizen.getName()));

        if (EntityNavigationUtils.walkToPos(citizen, bestResortLocation, MIN_DIST_TO_RESORT, true))
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.info("Vacationer {} has arrived at the resort and will be receiving a seat assignment.",
                    citizen.getName()));
            return VacationAIState.FIND_EMPTY_STATION;
        }
        return VacationAIState.SEARCH_RESORT;
    }

    /**
     * Stay in bed while waiting to be cured.
     *
     * @return the next state to go to.
     */
    private IState waitForCure()
    {

        // If our vacation has been cancelled (for example, with the vacationclear command) go back to work!
        if (vacationTracker == null || vacationTracker.getState().equals(VacationState.CHECKED_OUT))
        {
            reset();
            return AIWorkerState.START_WORKING;
        }

        citizenData.triggerInteraction(
            new StandardInteraction(Component.translatable(GREAT_VACATION, vacationTracker.name(), vacationTracker.getRemedyString()),
                Component.translatable(GREAT_VACATION),
                ChatPriority.BLOCKING));

        TraceUtils.dynamicTrace(TRACE_BURNOUT,
            () -> LOGGER.info("Vacationer {} is waiting for their remedy to repair {} with {} at vacation state {}.",
                citizen.getName(),
                vacationTracker.name(),
                vacationTracker.getRemedyString(),
                vacationTracker.getState()));

        final IState state = checkForCure();
        if (state == VacationAIState.APPLY_CURE)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.info("Vacationer {} has what they need while waiting and should apply the cure next.",
                    citizen.getName()));
            return VacationAIState.APPLY_CURE;
        }
        else if (state == CitizenAIState.IDLE)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.info("Vacationer {} became idle while checking for cures.", citizen.getName()));
            reset();
            return CitizenAIState.IDLE;
        }

        BlockPos bestResortPosition = getBestResortPosition();

        if (bestResortPosition == null)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.warn("Vacationer {} has lost track of their resort.", citizen.getName()));
            return VacationAIState.SEARCH_RESORT;
        }

        // TODO: RESORT - if it is night time let them go home (set to IDLE)
        if (!citizen.getCitizenSleepHandler().isAsleep() &&
            BlockPosUtil.getDistance2D(bestResortPosition, citizen.blockPosition()) > MIN_DIST_TO_RESORT)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.info("Vacationer {} is away from the resort and needs to go back.", citizen.getName()));
            return VacationAIState.FIND_EMPTY_STATION;
        }

        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} needs to keep waiting.", citizen.getName()));
        return VacationAIState.WAIT_FOR_CURE;
    }

    /**
     * Actual action of applying the remedy..
     *
     * @return the next state to go to, if successful idle.
     */
    private IState applyCure()
    {
        // If our vacation has been cancelled (for example, with the vacationclear command) go back to work!
        if (vacationTracker == null || vacationTracker.getState().equals(VacationState.CHECKED_OUT))
        {
            reset();
            return AIWorkerState.START_WORKING;
        }

        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} attempting to apply cure.", citizen));

        if (checkForCure() != VacationAIState.APPLY_CURE)
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} lost their cure at applyCure.", citizen));
            return VacationAIState.CHECK_FOR_CURE;
        }

        if (this.vacationTracker == null)
        {
            LOGGER.warn("Vacationer {} has lost track of their vacation state.", citizen);
            reset();
            return CitizenAIState.IDLE;
        }

        BlockPos bestResortLocation = getBestResortPosition();

        if (bestResortLocation == null)
        {
            // Perhaps the resort was destroyed before healing could occur.
            LOGGER.warn("Vacationer {} has lost track of their resort.", citizen);
            reset();
            return CitizenAIState.IDLE;
        }

        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} is ready to apply their cure.", citizen));

        final List<ItemStorage> list = vacationTracker.getRemedyItems();
        if (!list.isEmpty())
        {
            // Select a random item from the list indicated in the remedy.
            int selectedIndex = citizen.getRandom().nextInt(list.size());
            ItemStorage selectedRemedy = list.get(selectedIndex);

            // Set the item in-hand for animation/effect
            citizen.setItemInHand(InteractionHand.MAIN_HAND, selectedRemedy.getItemStack());

            // Consume the item from storage
            if (selectedRemedy.getAmount() > 0)
            {
                TraceUtils.dynamicTrace(TRACE_BURNOUT,
                    () -> LOGGER.info("Vacationer {} moved remedy {} to main hand.", citizen, selectedRemedy));
                selectedRemedy.setAmount(selectedRemedy.getAmount() - 1);
            }
        }
        else
        {
            LOGGER.warn("Vacationer {} has no remedy items associated with their tracker (this should not happen).", citizen);
        }

        // TODO: RESORT [Enhancement] Make research to scale healing speed?
        IBuilding resort = (BuildingResort) citizenData.getColony().getBuildingManager().getBuilding(bestResortLocation);
        int resortHealSpeed = resort.getBuildingLevel();

        citizenData.getCitizenSkillHandler().addXpToSkill(skillToHeal, VACATION_HEALING * 10, citizenData);
        int currentLevel = citizenData.getCitizenSkillHandler().getLevel(skillToHeal);

        citizen.swing(InteractionHand.MAIN_HAND);
        citizen.playSound(SoundEvents.NOTE_BLOCK_HARP.value(),
            (float) SoundUtils.BASIC_VOLUME,
            (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(citizen.getRandom()));
        new CircleParticleEffectMessage(citizen.position().add(0, 2, 0), ParticleTypes.HAPPY_VILLAGER, currentLevel)
            .sendToTrackingEntity(citizen);

        TraceUtils.dynamicTrace(TRACE_BURNOUT,
            () -> LOGGER.info("Vacationer {} has applied their remedy: Now at level {} of {}",
                citizen.getName(),
                currentLevel,
                vacationTracker.getTargetLevel()));

        if (currentLevel < vacationTracker.getTargetLevel())
        {
            return VacationAIState.APPLY_CURE;
        }

        cure();
        return CitizenAIState.IDLE;
    }

    /**
     * Cure the citizen.
     */
    private void cure()
    {
        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Vacationer {} is relaxed!", citizen.getName()));

        if (vacationTracker != null)
        {
            for (final ItemStorage cure : vacationTracker.getRemedyItems())
            {
                final int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(citizen, Vacationer.hasRemedyItem(cure));
                if (slot != -1)
                {
                    TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Remedy item removed."));
                    citizenData.getInventory().extractItem(slot, 1, false);
                }
            }
            StatsUtil.trackStat(vacationTracker.getResort(), BuildingResort.VACATIONS_COMPLETED, 1);

            int incomeGenerated = MCTPConfig.vacationIncome.get() * MCTPConfig.tradeCoinValue.get();
            citizenData.getColony()
                .getStatisticsManager()
                .incrementBy(WindowEconModule.CURRENT_BALANCE, incomeGenerated, citizenData.getColony().getDay());

            vacationTracker.reset();
        }

        citizen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        citizen.setHealth(citizen.getMaxHealth());

        reset();
    }

    /**
     * Find a good place within the resort to relax.
     *
     * @return the next state to go to.
     */
    private IState findEmptyStation()
    {
        // If our vacation has been cancelled (for example, with the vacationclear command) go back to work!
        if (vacationTracker == null || vacationTracker.getState().equals(VacationState.CHECKED_OUT))
        {
            reset();
            return AIWorkerState.START_WORKING;
        }

        BlockPos bestResortLocation = getBestResortPosition();

        if (bestResortLocation != null)
        {
            final IBuilding resort = citizen.getCitizenData().getColony().getBuildingManager().getBuilding(bestResortLocation);
            if (resort instanceof BuildingResort)
            {
                if (seatLocation == null)
                {
                    seatLocation = ((BuildingResort) resort).getNextSittingPosition();
                }

                if (seatLocation != null)
                {
                    TraceUtils.dynamicTrace(TRACE_BURNOUT,
                        () -> LOGGER.info("Vacationer {} has a seat assignment, and is walking to their seat.", citizen.getName()));

                    if (!EntityNavigationUtils.walkToPos(citizen, seatLocation, 1, true))
                    {
                        return VacationAIState.FIND_EMPTY_STATION;
                    }

                    TraceUtils.dynamicTrace(TRACE_BURNOUT,
                        () -> LOGGER.info("Vacationer {} takes a seat at {}", citizen.getName(), seatLocation));
                    SittingEntity.sitDown(seatLocation, citizen, Constants.TICKS_SECOND * 60);
                }
            }
            else
            {
                LOGGER.warn("Best resort location at {} is not a resort. Maybe it was moved or destroyed?", bestResortLocation);
                reset();
                return CitizenAIState.IDLE;
            }
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_BURNOUT,
                () -> LOGGER.info("Vacationer {} has lost track of their resort while looking for an empty station.", citizen));
            return VacationAIState.SEARCH_RESORT;
        }

        return VacationAIState.WAIT_FOR_CURE;
    }

    /**
     * Sets the vacation tracker for this citizen. This is used to manage the citizen's burnout state, and the AI which drives the
     * citizen to seek out a resort to cure that burnout.
     *
     * @param vacationTracker the new vacation tracker for this citizen.
     */
    public void setVacationTracker(Vacationer vacationTracker)
    {
        this.vacationTracker = vacationTracker;
    }

    /**
     * Retrieves the vacation tracker associated with this citizen.
     *
     * @return the vacation tracker.
     */
    public Vacationer getVacationTracker()
    {
        return this.vacationTracker;
    }

    /**
     * Sets the vacation tracker for this citizen. This is used to manage the citizen's burnout state, and the AI which drives the
     * citizen to seek out a resort to cure that burnout.
     *
     * @param vacationTracker the new vacation tracker for this citizen.
     */
    public void setVacationStatus(Vacationer vacationTracker)
    {
        this.vacationTracker = vacationTracker;
    }

    /**
     * Whether the citizen has resisted advertisements (i.e. ignored the call to go to the resort)
     * 
     * @return true if the citizen has resisted ads, false if they have not
     */
    public boolean isResistedAds(int today)
    {
        return this.dayLastResisted >= today;
    }

    public int getDayLastResisted()
    {
        return this.dayLastResisted;
    }

    /**
     * Sets the state indicating whether the citizen has resisted advertisements.
     *
     * @param resistedAds true if the citizen has resisted ads, false otherwise
     * @return the updated state of resistedAds
     */
    public void setResistedAds(int dayLastResisted)
    {
        this.dayLastResisted = dayLastResisted;
    }

    /**
     * Resets the state of the AI.
     */
    private void reset()
    {
        TraceUtils.dynamicTrace(TRACE_BURNOUT, () -> LOGGER.info("Resetting the burnout task for {}", citizen));

        adSaturationLevel = 0;
        if (vacationTracker != null)
        {
            vacationTracker.reset();
        }
        vacationTracker = null;
        seatLocation = null;
        dayLastResisted = -1;
        citizen.releaseUsingItem();
        citizen.stopUsingItem();
        citizen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
}
