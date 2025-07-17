package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.PetWolf;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.StatsUtil;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;

public class HerdGoal extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private final PetWolf wolf;
    private BlockPos herdTarget;
    private BlockPos targetLastOn = BlockPos.ZERO;
    private BlockPos wolfLastOn = BlockPos.ZERO;
    private int targetStuckSteps = 0;
    private Animal currentTargetAnimal = null;

    private static final int SEARCH_RADIUS = 100;
    private static final int HERDING_DISTANCE = 5;
    

    public HerdGoal(PetWolf wolf)
    {
        this.wolf = wolf;

        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    // TODO: Generalize what can be herded.

    /**
     * Searches for an animal that needs herding and sets the target accordingly.
     * 
     * @return true if an animal is found, false otherwise
     */
    public boolean findAnimalsThatNeedHerding()
    {
        IBuilding targetBuilding = wolf.getWorkBuilding();
        if (targetBuilding != null)
        {
            this.herdTarget = targetBuilding.getPosition();
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No target building set when evaluating whether there are animals that need herding."));
            return false;
        }

        /*
        List<Sheep> sheepNearby = wolf.level()
            .getEntitiesOfClass(Sheep.class,
                wolf.getBoundingBox().inflate(SEARCH_RADIUS),
                sheep -> !sheep.isBaby() && sheep.isAlive());
        */
        List<? extends Animal> targetsNearby = wolf.getPetData().searchForCompatibleAnimals(wolf.getBoundingBox().inflate(SEARCH_RADIUS));

        if (!targetsNearby.isEmpty())
        {
            for (Animal animalTarget : targetsNearby) 
            {
                double targetDistance = BlockPosUtil.getDistance(animalTarget.getOnPos(), herdTarget);

                if (targetDistance > HERDING_DISTANCE)
                {
                    currentTargetAnimal = animalTarget;
                    wolf.setTargetPosition(currentTargetAnimal.getOnPos());
                }

            }
            
            if (currentTargetAnimal != null)
            {
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("There's a {} nearby: {}", currentTargetAnimal.getName(),currentTargetAnimal.getOnPos()));

                return true;
            }

            return false;
        }

        return false;
    }

    /**
     * Determines if the herding goal can be used.
     * 
     * This goal can be used if the wolf is not leashed, not a passenger, and is alive.
     * Additionally, there must be a target to herd to (i.e. a building) and there must be animals nearby that need herding.
     * 
     * @return true if the herding goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        if (wolf.isLeashed() || wolf.isPassenger() || !wolf.isAlive())
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Cannot use herding goal. Is leashed? {} Is passenger? {} Is alive? {}", wolf.isLeashed(), wolf.isPassenger(), wolf.isAlive()));
            return false;
        }

        IBuilding targetBuilding = wolf.getTrainerBuilding();
        if (targetBuilding != null)
        {
            this.herdTarget = targetBuilding.getPosition();
        }

        if (BlockPos.ZERO.equals(herdTarget))
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No target to herd to."));
            return false;
        }

        if (findAnimalsThatNeedHerding())
        {
            return true;
        }

        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No animals nearby."));
        return false;
    }

    /**
     * The herding goal is a continuous goal, so it can continue to run as long as there are animals to herd.
     * If the current target animal is no longer available, it will search for a replacement.
     * @return false always
     */
    @Override
    public boolean canContinueToUse()
    {
        boolean keepherding = currentTargetAnimal != null && currentTargetAnimal.isAlive();

        if (!keepherding)
        {
            keepherding = findAnimalsThatNeedHerding();
        }

        return false;
    }

    /**
     * Stops the herding goal by clearing the current target.
     */
    @Override
    public void stop()
    {
        currentTargetAnimal = null;
    }

    /**
     * Executes a single tick of the herding goal, moving the target towards the herding destination.
     */
    @Override
    public void tick()
    {
        if (currentTargetAnimal == null) 
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No target on tick()."));
            targetLastOn = BlockPos.ZERO;
            targetStuckSteps = 0;
            return;
        }
        else
        {
            wolf.setTargetPosition(currentTargetAnimal.getOnPos());
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Target {} at {}", currentTargetAnimal.getName(), currentTargetAnimal.getOnPos()));
        }

        double distance = wolf.distanceTo(currentTargetAnimal);

        if (distance > 2)
        {
            // TODO: Research to increase speed
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Moving towards target. Distance: {}", distance));
            wolf.getNavigation().moveTo(currentTargetAnimal, 1.0);

            targetStuckSteps = 0;
        }
        else
        {
            double targetDistance = BlockPosUtil.getDistance(currentTargetAnimal.getOnPos(), herdTarget);

            if (targetDistance > HERDING_DISTANCE)
            {

                if ((targetDistance < HERDING_DISTANCE * 2 ) || targetStuckSteps > PetWolf.STUCK_STEPS)
                {
                    currentTargetAnimal.getNavigation().moveTo(herdTarget.getX() + 0.5, herdTarget.getY(), herdTarget.getZ() + 0.5, 1.0);
                }
                else
                {
                    // Nudge herded animal toward the target
                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Nudging target towards herding destination: {}. Distance to target: {}. Stuck steps: {}", herdTarget, targetDistance, targetStuckSteps));

                    Vec3 animalPos = currentTargetAnimal.position();
                    Vec3 targetVec = new Vec3(herdTarget.getX() + 0.5, animalPos.y, herdTarget.getZ() + 0.5);
                    Vec3 direction = targetVec.subtract(animalPos).normalize().scale(0.2);

                    currentTargetAnimal.setDeltaMovement(direction);
                    currentTargetAnimal.hurtMarked = true;  // Force position sync to client
                }

                if (currentTargetAnimal.getOnPos().equals(targetLastOn))
                {
                    targetStuckSteps++;
                }
                else
                {
                    targetStuckSteps = 0;
                }

                // Boost the animal up if stuck repeatedly
                if (targetStuckSteps > PetWolf.STUCK_STEPS) {
                    Vec3 movement = currentTargetAnimal.getDeltaMovement();
                    currentTargetAnimal.setDeltaMovement(movement.x, 0.5, movement.z);
                    currentTargetAnimal.hurtMarked = true;
                }

            }
            else
            {
                StatsUtil.trackStatByName(wolf.getTrainerBuilding(), BuildingPetshop.ANIMALS_HERDED, currentTargetAnimal.getName(), 1);

                currentTargetAnimal = null;
                targetLastOn = BlockPos.ZERO;
                targetStuckSteps = 0;
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Animal is close enough to target position: {}", herdTarget));

            }

        }

        if (wolfLastOn.equals(wolf.getOnPos()))
        {
            wolf.incrementStuckTicks();;
        }
        else
        {
            wolf.clearStuckTicks();
        }

        if (wolf.getStuckTicks() > PetWolf.STUCK_STEPS)
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Nudging wolf towards target position: {}. Stuck steps: {}", currentTargetAnimal.getOnPos(), wolf.getStuckTicks()));

            double dx = (wolf.getRandom().nextDouble() - 0.5) * 0.5;
            double dz = (wolf.getRandom().nextDouble() - 0.5) * 0.5;
            Vec3 current = wolf.getDeltaMovement();
            wolf.setDeltaMovement(current.add(dx, 0.3, dz));
            wolf.hurtMarked = true;
            wolf.getNavigation().stop();
            wolf.getNavigation().moveTo(currentTargetAnimal, 1.0);
        }

        if (currentTargetAnimal != null)
        {
            targetLastOn = currentTargetAnimal.getOnPos();
        }
        else
        {
            targetLastOn = BlockPos.ZERO;
        }

        wolfLastOn = wolf.getOnPos();
    }
}
