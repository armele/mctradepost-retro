package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.IHerdingPet;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;

public class  HerdGoal<P extends Animal & ITradePostPet & IHerdingPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();
    private boolean goalLogToggle = false;

    private final P pet;
    private BlockPos herdTarget;
    private BlockPos targetLastOn = BlockPos.ZERO;
    private BlockPos petLastOn = BlockPos.ZERO;
    private int targetStuckSteps = 0;
    private Animal currentTargetAnimal = null;
    private boolean walkCommandSent = false;
    private static final int SEARCH_RADIUS = 100;
    private static final int HERDING_DISTANCE = 8;
    private PathResult<?> navigationResult = null;

    public HerdGoal(P herdingPet)
    {
        this.pet = herdingPet;

        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /**
     * Searches for an animal that needs herding and sets the target accordingly.
     * 
     * @return true if an animal is found, false otherwise
     */
    public boolean findAnimalsThatNeedHerding()
    {
        this.herdTarget = pet.getWorkLocation();
        List<? extends Animal> targetsNearby = pet.getPetData().searchForCompatibleAnimals(pet.level(), pet.getBoundingBox().inflate(SEARCH_RADIUS));

        if (!targetsNearby.isEmpty())
        {
            for (Animal animalTarget : targetsNearby) 
            {
                double targetDistance = BlockPosUtil.getDistance(animalTarget.getOnPos(), herdTarget);

                if (targetDistance > HERDING_DISTANCE)
                {
                    currentTargetAnimal = animalTarget;
                    pet.setTargetPosition(currentTargetAnimal.getOnPos());
                }

            }
            
            if (currentTargetAnimal != null)
            {
                return true;
            }

            return false;
        }

        return false;
    }

    protected void logCanUse(boolean resultingState, Runnable loggingStatement)
    {
        if (goalLogToggle != resultingState)
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, loggingStatement);
            goalLogToggle = resultingState;
        }
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
        if (pet.isLeashed() || pet.isPassenger() || !pet.isAlive())
        {
            logCanUse(false, () -> LOGGER.info("Cannot use herding goal. Is leashed? {} Is passenger? {} Is alive? {}", pet.isLeashed(), pet.isPassenger(), pet.isAlive()));
            return false;
        }

        this.herdTarget = pet.getWorkLocation();

        if (herdTarget == null || BlockPos.ZERO.equals(herdTarget))
        {
            logCanUse(false, () -> LOGGER.info("No target to herd to."));
            return false;
        }
        
        if (pet.getPetData() == null || pet.level() == null)
        {
            logCanUse(false, () -> LOGGER.info("No pet data found or pet level not yet set."));
            return false;
        }

        if (PetRoles.HERDING.equals(pet.getPetData().roleFromWorkLocation(pet.level())) && findAnimalsThatNeedHerding())
        {
            logCanUse(true, () -> LOGGER.info("Found straying animals within range of pet at {}", pet.getOnPos()));
            return true;
        }

        logCanUse(false, () -> LOGGER.info("No straying animals within range of pet at {}", pet.getOnPos()));
        
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

        return keepherding;
    }

    /**
     * Starts the herding goal by finding an animal to herd.
     */
    @Override
    public void start()
    {

        if (currentTargetAnimal != null)
        {
            // TODO: Research to increase speed
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Starting towards target: {}", currentTargetAnimal));
            navigationResult = ((MinecoloniesAdvancedPathNavigate)pet.getNavigation()).walkToEntity(currentTargetAnimal, 1.0);
            walkCommandSent = true;
        }   
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
            reset();
            return;
        }
        else
        {
            pet.setTargetPosition(currentTargetAnimal.getOnPos());

            if (navigationResult != null && navigationResult.failedToReachDestination()) 
            {
                final BlockPos targetAnimalPos = currentTargetAnimal.getOnPos();
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Failed to reach {} at {}", currentTargetAnimal, targetAnimalPos));

                reset();
                return;
            }

        }
 
        double distance = pet.distanceTo(currentTargetAnimal);

        if (distance > 2 && !walkCommandSent)
        {
            // TODO: Research to increase speed
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Restarting towards target. Distance: {}", distance));
            navigationResult = ((MinecoloniesAdvancedPathNavigate)pet.getNavigation()).walkToEntity(currentTargetAnimal, 1.0);
            walkCommandSent = true;
            targetStuckSteps = 0;
        }
        else if (distance < 2)
        {
            walkCommandSent = false;
            double targetDistance = BlockPosUtil.getDistance(currentTargetAnimal.getOnPos(), herdTarget);

            if (targetDistance > HERDING_DISTANCE)
            {

                if ((targetDistance < HERDING_DISTANCE * 2 ) || targetStuckSteps > PetData.STUCK_STEPS)
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
                if (targetStuckSteps > PetData.STUCK_STEPS) {
                    Vec3 movement = currentTargetAnimal.getDeltaMovement();
                    currentTargetAnimal.setDeltaMovement(movement.x, 0.5, movement.z);
                    currentTargetAnimal.hurtMarked = true;
                }

            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Animal is close enough to target position: {}", herdTarget));
                StatsUtil.trackStatByName(pet.getTrainerBuilding(), BuildingPetshop.ANIMALS_HERDED, currentTargetAnimal.getName(), 1);
                reset();
            }

        }

        if (petLastOn.equals(pet.getOnPos()))
        {
            pet.incrementStuckTicks();
        }
        else
        {
            pet.clearStuckTicks();
        }

        if (pet.getStuckTicks() > PetData.STUCK_STEPS)
        {
            if (currentTargetAnimal != null)
            {
                final BlockPos targetAnimalPos = currentTargetAnimal.getOnPos();
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Nudging pet towards target position: {}. Stuck steps: {}", targetAnimalPos, pet.getStuckTicks()));
            }

            double dx = (pet.getRandom().nextDouble() - 0.5) * 0.5;
            double dz = (pet.getRandom().nextDouble() - 0.5) * 0.5;
            Vec3 current = pet.getDeltaMovement();
            pet.setDeltaMovement(current.add(dx, 0.3, dz));
            pet.hurtMarked = true;
            pet.getNavigation().stop();
        }

        if (currentTargetAnimal != null)
        {
            targetLastOn = currentTargetAnimal.getOnPos();
            navigationResult = ((MinecoloniesAdvancedPathNavigate)pet.getNavigation()).walkToEntity(currentTargetAnimal, 1.0);
            walkCommandSent = true;
        }
        else
        {
            targetLastOn = BlockPos.ZERO;
        }

        petLastOn = pet.getOnPos();
    }

    /**
     * Resets the state of the herding goal, clearing the current target animal and resetting internal counters.
     */
    protected void reset()
    {
        currentTargetAnimal = null;
        navigationResult = null;
        walkCommandSent = false;
        targetLastOn = BlockPos.ZERO;
        targetStuckSteps = 0;
    }
}
