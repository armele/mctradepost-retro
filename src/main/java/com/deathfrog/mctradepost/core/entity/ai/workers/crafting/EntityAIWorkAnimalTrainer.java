package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.CRAFT;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetHelper;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.deathfrog.mctradepost.core.colony.jobs.JobAnimalTrainer;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EntityAIWorkAnimalTrainer extends AbstractEntityAICrafting<JobAnimalTrainer, BuildingPetshop>
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String PETS_TRAINED = "pets_trained";

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
        if (building.getPets().size() < MCTPConfig.petsPerLevel.get())
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Room for pets. I should raise one!"));
            return AnimalTrainerStates.RAISE_PET;
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("I have {} pets.", building.getPets().size()));
        }

        return super.decide();
    }

    /**
     * Decides whether to raise a pet or not.
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

        for (PetTypes type : PetTypes.values())
        {
            ItemStack trainingStack = type.getTrainingItem();

            int count = MCTPInventoryUtils.combinedInventoryCount(building, new ItemStorage(trainingStack));
            if (count < trainingStack.getCount() || building.getBuildingLevel() < type.getLevelRequirement())
            {
                continue;
            }

            MCTPInventoryUtils.combinedInventoryRemoval(building, new ItemStorage(trainingStack), trainingStack.getCount());
            
            try
            {
                // Instantiate the pet
                Animal pet = type.getPetClass()
                    .getConstructor(EntityType.class, Level.class)
                    .newInstance(type.getEntityType(), building.getColony().getWorld());

                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER,
                    () -> LOGGER.info("Raising a pet: {}", type.getPetClass().getSimpleName()));

                // If you want to assign additional interfaces or data:
                if (pet instanceof ITradePostPet tradePostPet)
                {
                    @SuppressWarnings("rawtypes")
                    PetHelper helper = new PetHelper(pet);
                    helper.doRegistration(building);
                    break;
                }

                StatsUtil.trackStat(building, PETS_TRAINED, 1);
                worker.getCitizenExperienceHandler().addExperience(2.0);

            }
            catch (ReflectiveOperationException e)
            {
                MCTradePostMod.LOGGER.error("Failed to instantiate pet of type: " + type.name(), e);
            }
        }

        return DECIDE;
    }

    /**
     * Perform the crafting operation for the animal trainer. This method is called by the {@link AbstractEntityAICrafting} class when
     * the AI is in the CRAFT state. This method is responsible for setting the visible status of the citizen to indicate crafting
     * activity and then calling the superclass's {@link #craft()} method to perform the actual crafting operation.
     * <p>The superclass's method will update the building's statistics accordingly and handle the crafting process. This method will
     * be called recursively until all items have been crafted or the AI is interrupted.
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
