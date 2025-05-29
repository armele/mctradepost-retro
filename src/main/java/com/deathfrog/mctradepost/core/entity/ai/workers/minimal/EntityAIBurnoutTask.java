package com.deathfrog.mctradepost.core.entity.ai.workers.minimal;

import java.util.List;
import java.util.Random;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
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

public class EntityAIBurnoutTask  {
    public static final Logger LOGGER = LogUtils.getLogger();

    protected EntityCitizen citizen;
    protected ICitizenData citizenData;
    protected Vacationer vacationTracker = null;
    protected BlockPos seatLocation = null;
    protected boolean resistedAds = true;

    /**
     * Worker status icon
     */
    private final static VisibleCitizenStatus VACATION =
      new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/vacation/default.png"), "com.mctradepost.gui.visiblestatus.needvacation");

    /* What do we consider maximum advertizing saturation? 
    *  This is the point at which a citizen is virtually guaranteed to burn out. 
    */
  
    protected static final int MAX_AD_SATURATION = MCTPConfig.maxAdSaturation.get();                        // Denominator to determine vacation chance.
    protected int VACATION_SUSCEPTIBILITY_THRESHOLD = MCTPConfig.vacationSusceptibilityThreshold.get();     // Under this stat level citizen is more susceptible to advertising.
    protected int VACATION_IMMUNITY_THRESHOLD = MCTPConfig.vacationImmunityThreshold.get();                 // Under this stat level citizen is more susceptible to advertising.
    protected int VACATION_HEALING = MCTPConfig.vacationHealing.get();                                      // The number of skill-ups a single vacation will get you. (Also used to scale XP applied)
    protected double MAX_VACATION_CHANCE = MCTPConfig.vacationMaxChance.get();                              // The maximum chance of a vacation.

    /**
     * Min distance to resort before trying to find a relaxation station.
     */
    private static final int MIN_DIST_TO_RESORT = 10;

    private static final int ENTITY_AI_BURNOUT_TICKRATE = 200;

    protected final static String NO_RESORT = "com.mctradepost.resort.no_resort";
    protected final static String NEED_VACATION = "com.mctradepost.resort.need_vacation";
    protected final static String BURNOUT = "burnout";

    /* How much advertizing has this citizen seen? */
    protected int adSaturationLevel = 0;

    /**
     * Get a random between 0 and 1.
     */
    private static final Random rand = new Random();


    private Skill skillToHeal = null;

    /**
     * The different types of AIStates related to going on vacation.
     */
    public enum VacationState implements IState
    {
        CHECK_FOR_CURE,
        GO_TO_HUT,
        SEARCH_RESORT,
        GO_TO_RESORT,
        WAIT_FOR_CURE,
        FIND_EMPTY_STATION,
        APPLY_CURE,
        WANDER
    }

    static 
    {

    }

    /* Modelled after EntityAISickTask */
    public EntityAIBurnoutTask(EntityCitizen citizen)  {
        // Implement burnout AI and integrate it into citizen states.
        this.citizen = citizen;
        this.citizenData = citizen.getCitizenData();
        final IColony colony = citizenData.getColony();

        this.skillToHeal = determineSkillToHeal();

        if (skillToHeal == null)  {
            LOGGER.warn("Citizen " + citizen.getName() + " has no skill to heal."); 
        }

        ITickRateStateMachine<IState> ai = citizen.getCitizenAI();

        ai.addTransition(new AIEventTarget<IState>(AIBlockingEventType.EVENT,this::isBurntOut, this::searchResort, ENTITY_AI_BURNOUT_TICKRATE));
        ai.addTransition(new TickingTransition<>(VacationState.CHECK_FOR_CURE, () -> true, this::checkForCure, 20));
        ai.addTransition(new TickingTransition<>(VacationState.WANDER, () -> true, this::wander, 200));
        ai.addTransition(new TickingTransition<>(VacationState.SEARCH_RESORT, () -> true, this::searchResort, 20));
        ai.addTransition(new TickingTransition<>(VacationState.GO_TO_RESORT, () -> true, this::goToResort, 20));
        ai.addTransition(new TickingTransition<>(VacationState.FIND_EMPTY_STATION, () -> true, this::findEmptyStation, 20));
        ai.addTransition(new TickingTransition<>(VacationState.WAIT_FOR_CURE, () -> true, this::waitForCure, 20));
        ai.addTransition(new TickingTransition<>(VacationState.APPLY_CURE, () -> true, this::applyCure, 20));
    }

    /**
     * Determines the skill to heal for the burnout AI.
     * determines the adverse skill to the primary skill of the module and sets that as the skill to heal.
     */
    protected Skill determineSkillToHeal() {
        final IColony colony = citizenData.getColony();
        final IJob<?> job = citizen.getCitizenData().getJob();
        
        final IBuilding citizenWorkBuilding = job.getWorkBuilding();
        final WorkerBuildingModule module = citizenWorkBuilding.getModuleMatching(WorkerBuildingModule.class, m -> m.getAssignedCitizen().contains(citizen.getCitizenData()));
        Skill adverse = null;
 
        if (module != null) {
            final Skill primary = module.getPrimarySkill();
            adverse = primary.getAdverse();  
        }
        
        return adverse;
    }

    protected BlockPos getBestResortLocation() {
        return citizenData.getColony().getBuildingManager().getBestBuilding(citizen, BuildingResort.class);
    }

    /**
     * Gets the best resort for the citizen to visit.
     * 
     * @return the best resort for the citizen to visit
     */
    protected BuildingResort getBestResort() {
        BuildingResort resort = null;
        
        // The best resort is the one where the reservation exists.
        if (vacationTracker != null && vacationTracker.getResort() != null) {
            resort = vacationTracker.getResort();
        } else {
            // Otherwise, find the closest.
            BlockPos bestResortLocation = getBestResortLocation();
            resort = (BuildingResort) citizenData.getColony().getBuildingManager().getBuilding(bestResortLocation); 
        }
        
        return (BuildingResort) resort;
    }

    /**
     * Calculates the advertising power for the resort at a given position.
     * The advertising power is determined by the resort's building level
     * and a stat multiplier based on the citizen's skill level.
     * 
     * @param pos the position of the resort
     * @return the calculated advertising power
     */
    protected int calcAdvertisingPower() {
        final IBuilding resort = getBestResort();

        if (resort == null || skillToHeal == null) {
            return 0;
        }

        double statmultiplier = VACATION_SUSCEPTIBILITY_THRESHOLD / citizenData.getCitizenSkillHandler().getLevel(skillToHeal);
        statmultiplier = Math.max(statmultiplier, 1);

        int adPower = (int) Math.max(resort.getBuildingLevel() * statmultiplier, 1);

        return adPower;
    }

    /**
     * Determines whether the citizen is burnt out based on a random chance.
     *
     * @return true if the citizen is burnt out, false otherwise.
     */
    private boolean isBurntOut()
    {   
        if (MAX_AD_SATURATION < 0) {    // This logic is disabled.
            return false;
        }

        // If they are sick, cancel any vacation they may have had.
        if (citizenData.getCitizenDiseaseHandler().isSick()) {
            reset();
            return false;
        }

        if (isResistedAds()) {
            // Don't burn out - but also don't reset their ad saturation level (no reset)
            return false;
        }

        // They already needed a vacation.  Unless they've checked out, continue it.
        if (vacationTracker != null) {
            // They've recently gone on vacation and been checked out.  Clear them out, and signal no new vacation yet!
            if (vacationTracker.getState() == Vacationer.VacationState.CHECKED_OUT) {
                reset();
                return false;
            }
            return true;
        }

        // They didn't have a skill to heal when this AI was initialized - double-check again.
        if (skillToHeal == null) {
            skillToHeal = determineSkillToHeal();
        }

        int currentSkillLevel = 0;

        // This probably means they are unemployed - no need for a vacation!
        if (skillToHeal == null) {
            LOGGER.warn("Citizen " + citizen.getName() + " has no skill to heal."); 
            reset();
            return false;
        } else {
            currentSkillLevel = citizenData.getCitizenSkillHandler().getLevel(skillToHeal);
            if (currentSkillLevel >= VACATION_IMMUNITY_THRESHOLD) {
                // This citizen doesn't need a vacation, they're doing great!
                reset();
                return false;
            }
        }

        final IColony colony = citizenData.getColony();
        double burnoutChance = 0.0;

        adSaturationLevel += calcAdvertisingPower();

        burnoutChance = Math.ceil((double)adSaturationLevel / (double)MAX_AD_SATURATION);
        burnoutChance = Math.min(MAX_VACATION_CHANCE, burnoutChance);

        // The higher the burnout chance the more likely the citizen will burn out.
        double roll = rand.nextDouble();
        if (roll <= burnoutChance)
        {
            BuildingResort resort = getBestResort();
            vacationTracker = new Vacationer(citizen.getCivilianID(), skillToHeal);
            vacationTracker.setTargetLevel(currentSkillLevel + VACATION_HEALING);
            resort.makeReservation(vacationTracker);
    
            LOGGER.trace("Someone needs a vacation! {}. To fix: {} (rolled {} with a chance of {})"
                , citizen.getName(), skillToHeal, roll, burnoutChance);

            citizen.getCitizenData().setVisibleStatus(VACATION);

            return true;
        } else {
            // Set this resistance flag here to prevent burnout checks until another wave of ads from the resort.
            setResistedAds(true);
        }

        // No reset here intentionally - letting adSaturation build up.
        return false;
    }

    /**
     * Checks if the citizen has the cure in the inventory and makes a decision based on that.
     *
     * @return the next state to go to.
     */
    private IState checkForCure()
    {
        if (vacationTracker != null) {
            for (final ItemStorage cure : vacationTracker.getRemedyItems())
            {
                final int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(citizen, Vacationer.hasRemedyItem(cure));
                if (slot == -1)
                {
                    return VacationState.WAIT_FOR_CURE;
                }
            }
        } else {
            // Their burnout has been cleared...
            reset();
            return CitizenAIState.IDLE;
        }
        return VacationState.APPLY_CURE;
    }

    /**
     * Do a bit of wandering.
     *
     * @return start over.
     */
    public VacationState wander()
    {
        EntityNavigationUtils.walkToRandomPos(citizen, 10, 0.6D);
        return VacationState.SEARCH_RESORT;
    }

    /**
     * Search for a placeToPath within the colony of the citizen.
     *
     * @return the next state to go to.
     */
    private IState searchResort()
    {
        final IColony colony = citizenData.getColony();
        BuildingResort resort = getBestResort();

        if (resort == null || resort.getPosition() == null)
        {
            if (vacationTracker == null)
            {
                // Burnout has been cleared somewhere...
                reset();
                return CitizenAIState.IDLE;
            }
            citizenData.triggerInteraction(new StandardInteraction(Component.translatable(NO_RESORT, vacationTracker.name(), vacationTracker.getRemedyString()),
              Component.translatable(NO_RESORT),
              ChatPriority.BLOCKING));

            return VacationState.WANDER;
        }
        else if (vacationTracker != null)
        {
            citizenData.triggerInteraction(new StandardInteraction(Component.translatable(NEED_VACATION, vacationTracker.name(), vacationTracker.getRemedyString()),
              Component.translatable(NEED_VACATION),
              ChatPriority.BLOCKING));
        }

        return VacationState.GO_TO_RESORT;
    }

    /**
     * Go to the previously found placeToPath to get cure.
     *
     * @return the next state to go to.
     */
    private IState goToResort()
    {   
        BlockPos bestResortLocation = getBestResortLocation();

        if (bestResortLocation == null)
        {
            return VacationState.SEARCH_RESORT;
        }

        LOGGER.trace("Vacationer {} is walking to the resort.", citizen.getName());

        if (EntityNavigationUtils.walkToPos(citizen, bestResortLocation, MIN_DIST_TO_RESORT, true))
        {
            return VacationState.FIND_EMPTY_STATION;
        }
        return VacationState.SEARCH_RESORT;
    }

    /**
     * Stay in bed while waiting to be cured.
     *
     * @return the next state to go to.
     */
    private IState waitForCure()
    {
        final IColony colony = citizenData.getColony();
        BlockPos bestResortLocation = getBestResortLocation();

        if (bestResortLocation == null)
        {
            return VacationState.SEARCH_RESORT;
        }

        LOGGER.trace("Vacationer {} is waiting for their remedy.", citizen.getName());

        final IState state = checkForCure();
        if (state == VacationState.APPLY_CURE)
        {
            return VacationState.APPLY_CURE;
        }
        else if (state == CitizenAIState.IDLE)
        {
            reset();
            return CitizenAIState.IDLE;
        }

        if (!citizen.getCitizenSleepHandler().isAsleep() && BlockPosUtil.getDistance2D(bestResortLocation, citizen.blockPosition()) > MIN_DIST_TO_RESORT)
        {
            return VacationState.FIND_EMPTY_STATION;
        }

        return VacationState.WAIT_FOR_CURE;
    }

    /**
     * Actual action of applying the remedy..
     *
     * @return the next state to go to, if successful idle.
     */
    private IState applyCure()
    {
        if (checkForCure() != VacationState.APPLY_CURE)
        {
            return VacationState.CHECK_FOR_CURE;
        }

        if (this.vacationTracker == null)
        {
            reset();
            return CitizenAIState.IDLE;
        }

        BlockPos bestResortLocation = getBestResortLocation();

        if (bestResortLocation == null) {
            // Perhaps the resort was destroyed before healing could occur.
            reset();
            return CitizenAIState.IDLE;
        }


        final List<ItemStorage> list = vacationTracker.getRemedyItems();
        if (!list.isEmpty())
        {
            // citizen.setItemInHand(InteractionHand.MAIN_HAND, list.get(citizen.getRandom().nextInt(list.size())).getItemStack());
            // Select a random item from the list indicated in the remedy.
            int selectedIndex = citizen.getRandom().nextInt(list.size());
            ItemStorage selectedRemedy = list.get(selectedIndex);

            // Set the item in-hand for animation/effect
            citizen.setItemInHand(InteractionHand.MAIN_HAND, selectedRemedy.getItemStack());

            // Consume the item from storage
            if (selectedRemedy.getAmount() > 0)
            {
                selectedRemedy.setAmount(selectedRemedy.getAmount() - 1); 
            }
        }

        // TODO: RESORT [Enhancement] Make research to scale this.
        IBuilding resort = (BuildingResort) citizenData.getColony().getBuildingManager().getBuilding(bestResortLocation);
        int resortHealSpeed = resort.getBuildingLevel();

        citizenData.getCitizenSkillHandler().addXpToSkill(skillToHeal, VACATION_HEALING * 10, citizenData);
        int currentLevel = citizenData.getCitizenSkillHandler().getLevel(skillToHeal);

        citizen.swing(InteractionHand.MAIN_HAND);
        citizen.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), (float) SoundUtils.BASIC_VOLUME, (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(citizen.getRandom()));
        new CircleParticleEffectMessage(citizen.position().add(0, 2, 0), ParticleTypes.HAPPY_VILLAGER, currentLevel)
            .sendToTrackingEntity(citizen);

        LOGGER.trace("Vacationer {} has applied their remedy: Now at level {} of {}"
            , citizen.getName(), currentLevel, vacationTracker.getTargetLevel());

        if (currentLevel < vacationTracker.getTargetLevel())
        {
            return VacationState.APPLY_CURE;
        }

        cure();
        return CitizenAIState.IDLE;
    }

    /**
     * Cure the citizen.
     */
    private void cure()
    {

        LOGGER.trace("Vacationer {} is relaxed!", citizen.getName());

        if (vacationTracker != null)
        {
            for (final ItemStorage cure : vacationTracker.getRemedyItems())
            {
                final int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(citizen, Vacationer.hasRemedyItem(cure));
                if (slot != -1)
                {
                    LOGGER.trace("Remedy item removed.");
                    citizenData.getInventory().extractItem(slot, 1, false);
                }
            }
            // TODO: RESORT Record Healing Stats here.
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
        BlockPos bestResortLocation = getBestResortLocation();

        if (bestResortLocation != null)
        {
            final IBuilding resort = citizen.getCitizenData().getColony().getBuildingManager().getBuilding(bestResortLocation);
            if (resort instanceof BuildingResort)
            {

                if (seatLocation == null) {
                    seatLocation = ((BuildingResort) resort).getNextSittingPosition();
                }

                if (seatLocation != null) {
                    LOGGER.trace("Vacationer {} has a seat assignment, and is walking to their seat.", citizen.getName());

                    if (!EntityNavigationUtils.walkToPos(citizen, seatLocation, 1, true)) {
                        return VacationState.FIND_EMPTY_STATION;
                    } 

                    LOGGER.trace("Vacationer {} takes a seat at {}", citizen.getName(), seatLocation);
                    SittingEntity.sitDown(seatLocation, citizen, Constants.TICKS_SECOND * 60);
                }
            } else {
                LOGGER.warn("Best resort location at {} is not a resort. Maybe it was moved or destroyed?", bestResortLocation);
                reset();
                return CitizenAIState.IDLE;
            }
        }

        return VacationState.WAIT_FOR_CURE;
    }

    /**
     * Retrieves the vacation tracker associated with this citizen.
     *
     * @return the vacation tracker.
     */
    public Vacationer getVacationTracker() {
        return this.vacationTracker;
    }

    /**
     * Sets the vacation tracker for this citizen.  This is used to manage the
     * citizen's burnout state, and the AI which drives the citizen to seek
     * out a resort to cure that burnout.
     *
     * @param vacationTracker the new vacation tracker for this citizen.
     */
    public void setVacationStatus(Vacationer vacationTracker) {
        this.vacationTracker = vacationTracker;
    }

    /**
     * Whether the citizen has resisted advertisements (i.e. ignored the call to go to the resort)
     * @return true if the citizen has resisted ads, false if they have not
     */
    public boolean isResistedAds() {
        return this.resistedAds;
    }

    /**
     * Sets the state indicating whether the citizen has resisted advertisements.
     *
     * @param resistedAds true if the citizen has resisted ads, false otherwise
     * @return the updated state of resistedAds
     */
    public boolean setResistedAds(boolean resistedAds) {
        this.resistedAds = resistedAds;
        return this.resistedAds;
    }

    /**
     * Resets the state of the AI.
     */
    private void reset()
    {
        adSaturationLevel = 0;
        if (vacationTracker != null) {
            vacationTracker.reset();
        }
        vacationTracker = null;
        seatLocation = null;
        resistedAds = true;
        citizen.releaseUsingItem();
        citizen.stopUsingItem();
        citizen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

}
