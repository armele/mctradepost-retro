package com.deathfrog.mctradepost.core.entity.ai.workers.minimal;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.IStateAI;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.datalistener.model.Disease;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class EntityAIBurnoutTask implements IStateAI {

    protected EntityCitizen citizen;
    protected ICitizenData citizenData;

    /* What do we consider maximum advertizing saturation? 
    *  This is the point at which a citizen is virtually guaranteed to burn out. 
    */
    // TODO: Make these configurable.  Negative value to disable.
    protected int MAX_AD_SATURATION = 12000;
    protected int VACATION_STAT_THRESHOLD = 10;

    protected final static String NO_RESORT = "com.mctradepost.resort.no_resort";
    protected final static String NEED_VACATION = "com.mctradepost.resort.need_vacation";
    protected final static String BURNOUT_DISEASE = "burnout_disease";
    protected final static String BURNOUT_DISEASE_NAME = "com.mctradepost.resort." + "burnout_disease";

    /* How much advertizing has this citizen seen? */
    protected int adSaturationLevel = 0;

    /**
     * Get a random between 1 and 100.
     */
    private static final int ONE_HUNDRED = 100;
    private static final Random rand = new Random();

    /**
     * Resort to which the citizen should path.
     */
    private BlockPos bestResortLocation;

    /**
     * The different types of AIStates related to eating.
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

    /* Modelled after EntityAISickTask */
    public EntityAIBurnoutTask(EntityCitizen citizen) {
        // Implement burnout AI and integrate it into citizen states.
        this.citizen = citizen;
        this.citizenData = citizen.getCitizenData();

        ITickRateStateMachine<IState> ai = citizen.getCitizenAI();

        ai.addTransition(new TickingTransition<>(CitizenAIState.WORKING, this::isBurntOut, () -> VacationState.CHECK_FOR_CURE, 20));
        citizen.getCitizenAI().addTransition(new TickingTransition<>(VacationState.CHECK_FOR_CURE, () -> true, this::checkForCure, 20));
        citizen.getCitizenAI().addTransition(new TickingTransition<>(VacationState.WANDER, () -> true, this::wander, 200));

        // citizen.getCitizenAI().addTransition(new TickingTransition<>(CHECK_FOR_CURE, () -> true, this::checkForCure, 20));
        // citizen.getCitizenAI().addTransition(new TickingTransition<>(GO_TO_HUT, () -> true, this::goToHut, 20));
        citizen.getCitizenAI().addTransition(new TickingTransition<>(VacationState.SEARCH_RESORT, () -> true, this::searchResort, 20));
        // citizen.getCitizenAI().addTransition(new TickingTransition<>(GO_TO_HOSPITAL, () -> true, this::goToHospital, 20));
        // citizen.getCitizenAI().addTransition(new TickingTransition<>(WAIT_FOR_CURE, () -> true, this::waitForCure, 20));
        // citizen.getCitizenAI().addTransition(new TickingTransition<>(APPLY_CURE, () -> true, this::applyCure, 20));
        // citizen.getCitizenAI().addTransition(new TickingTransition<>(FIND_EMPTY_BED, () -> true, this::findEmptyBed, 20));
    }

    protected int calcAdvertisingPower(BlockPos pos) {
        final IColony colony = citizenData.getColony();
        final IBuilding resort = colony.getBuildingManager().getBuilding(pos);

        if (!(resort instanceof BuildingResort)) {
            return 0;
        }
        
        final IJob<?> job = citizen.getCitizenData().getJob();
        final WorkerBuildingModule module = resort.getModuleMatching(WorkerBuildingModule.class, m -> m.getAssignedCitizen().contains(citizen.getCitizenData()));
        final Skill primary = module.getPrimarySkill();
        final Skill adverse = primary.getAdverse();

        double statmultiplier = VACATION_STAT_THRESHOLD / citizen.getCitizenData().getCitizenSkillHandler().getLevel(adverse);
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
        final IColony colony = citizenData.getColony();
        int burnoutChance = 0;

        bestResortLocation = colony.getBuildingManager().getBestBuilding(citizen, BuildingResort.class);
        adSaturationLevel += calcAdvertisingPower(bestResortLocation);

        if (MAX_AD_SATURATION < 0) {
            return false;
        } else {
            burnoutChance = (int) Math.ceil(adSaturationLevel / MAX_AD_SATURATION);
        }

        // The higher the burnout chance the more likely the citizen will burn out.
        if ((rand.nextDouble() * ONE_HUNDRED) < burnoutChance)
        {
            // TODO: Establish a "cure" associated with each of the statistics we're going to be remedying, as determined by the citizen's job, and the 

            MCTradePostMod.LOGGER.info("Burning out citizen with chance {}", burnoutChance);

            List<ItemStorage> cureItems = new ArrayList<>();
            cureItems.add(new ItemStorage(new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get(), 1)));

            Disease burnout = new Disease(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, BURNOUT_DISEASE), Component.translatable(BURNOUT_DISEASE_NAME), 0, cureItems);
            citizen.getCitizenData().getCitizenDiseaseHandler().setDisease(burnout);

            // Do we need reset logic?
            return true;
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
        // TODO: Implement this (possibly based on EntityAISickTask).  Right now, only wandering is implemented.
        return VacationState.WANDER;
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
        final Disease disease = citizen.getCitizenData().getCitizenDiseaseHandler().getDisease();
        bestResortLocation = colony.getBuildingManager().getBestBuilding(citizen, BuildingResort.class);

        if (bestResortLocation == null)
        {
            if (disease == null)
            {
                return CitizenAIState.IDLE;
            }
            citizenData.triggerInteraction(new StandardInteraction(Component.translatable(NO_RESORT, disease.name(), disease.getCureString()),
              Component.translatable(NO_RESORT),
              ChatPriority.BLOCKING));
            return VacationState.WANDER;
        }
        else if (disease != null)
        {
            citizenData.triggerInteraction(new StandardInteraction(Component.translatable(NEED_VACATION, disease.name(), disease.getCureString()),
              Component.translatable(NEED_VACATION),
              ChatPriority.BLOCKING));
        }

        return VacationState.GO_TO_RESORT;
    }

}
