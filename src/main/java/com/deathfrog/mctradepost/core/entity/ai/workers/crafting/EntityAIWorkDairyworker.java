package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer;
import com.deathfrog.mctradepost.core.colony.jobs.JobDairyworker;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCowboy;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Baker AI class.
 */
public class EntityAIWorkDairyworker extends AbstractEntityAICrafting<JobDairyworker, BuildingCowboy>
{

    public EntityAIWorkDairyworker(@NotNull final JobDairyworker job)
    {
        super(job);
        worker.setCanPickUpLoot(true);
    }


    /** 
     Returns the class of the expected building for this AI.
     *
     * @return The class of the expected building.
     */
    @Override
    public Class<BuildingCowboy> getExpectedBuildingClass()
    {
        return BuildingCowboy.class;
    }

    /**
     * Returns the bakery's worker instance. Called from outside this class.
     *
     * @return citizen object.
     */
    @Nullable
    public AbstractEntityCitizen getCitizen()
    {
        return worker;
    }

    @Override
    public boolean isAfterDumpPickupAllowed()
    {
        return true;
    }

    @Override
    protected IAIState craft()
    {
        if (building != null && !searchForAnimals(a -> a instanceof Cow && !(a instanceof MushroomCow) && !a.isBaby()).isEmpty())
        {
            job.resetCounter();
            return super.craft();
        }
        else
        {
            job.tickNoCows();

            if (job.checkForCowInteraction())
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_COWS), ChatPriority.BLOCKING));
            }

            return IDLE;
        }
    }

    /**
     * Find animals in area.
     *
     * @param predicate true if the animal is interesting.
     * @return a {@link Stream} of animals in the area.
     */
    public List<? extends Animal> searchForAnimals(final Predicate<Animal> predicate)
    {
        return WorldUtil.getEntitiesWithinBuilding(world, Animal.class, building, predicate);
    }

}