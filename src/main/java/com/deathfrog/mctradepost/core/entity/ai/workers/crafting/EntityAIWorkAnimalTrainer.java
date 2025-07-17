package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.CRAFT;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.PetWolf;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.deathfrog.mctradepost.core.colony.jobs.JobAnimalTrainer;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;

public class EntityAIWorkAnimalTrainer extends AbstractEntityAICrafting<JobAnimalTrainer, BuildingPetshop>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public enum AnimalTrainerStates implements IAIState
    {
        RAISE_PET;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    /**
     * Crafting icon
     */
    private final static VisibleCitizenStatus CRAFTING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/animaltrainer.png"),
            "com.mctradepost.gui.visiblestatus.animaltrainer");

    public EntityAIWorkAnimalTrainer(@NotNull JobAnimalTrainer job)
    {
        super(job);
        super.registerTargets(new AITarget<IAIState>(IDLE, START_WORKING, 2),
            new AITarget<IAIState>(START_WORKING, DECIDE, 2),
            new AITarget<IAIState>(DECIDE, this::decide, 2),
            new AITarget<IAIState>(AnimalTrainerStates.RAISE_PET, this::raisePet, 2),
            new AITarget<IAIState>(CRAFT, this::craft, 50));
        worker.setCanPickUpLoot(true);
    }

    /**
     * The building class this AI is intended to be used with.
     * 
     * @return the building class
     */
    @Override
    public Class<BuildingPetshop> getExpectedBuildingClass()
    {
        return BuildingPetshop.class;
    }

    @Override
    public IAIState decide()
    {
        if (building.getPets().isEmpty())
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No pets. I should raise one!"));
            return AnimalTrainerStates.RAISE_PET;
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("I have {} pets.", building.getPets().size()));
        }

        return super.decide();
    }

    /**
     * Decides whether to raise a pet wolf or not.
     * 
     * @return the next AI state to transition to.
     */
    public IAIState raisePet()
    {

        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }
        
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Raising a pet."));

        PetWolf pet = MCTradePostMod.PET_WOLF.get().create(world);

        if (pet == null)
        {
            LOGGER.warn("Failed to raise a pet.");
            return DECIDE;
        }

        pet.setTrainerBuilding(building);
        pet.setPos(worker.getX(), worker.getY(), worker.getZ());
        world.addFreshEntity(pet);
        PetRegistryUtil.register(pet);
        building.markPetsDirty();

        return DECIDE;
    }


    /**
     * Perform the crafting operation for the animal trainer. This method is called by the {@link AbstractEntityAICrafting} class 
     * when the AI is in the CRAFT state. This method is responsible for setting the visible status of the citizen to indicate 
     * crafting activity and then calling the superclass's {@link #craft()} method to perform the actual crafting operation. 
     * <p>The superclass's method will update the building's statistics accordingly and handle the crafting process. This method 
     * will be called recursively until all items have been crafted or the AI is interrupted.
     * 
     * @return the next AI state to transition to.
     */
    @Override
    public IAIState craft()
    {
        worker.getCitizenData().setVisibleStatus(CRAFTING);
        return super.craft();
    }

}
