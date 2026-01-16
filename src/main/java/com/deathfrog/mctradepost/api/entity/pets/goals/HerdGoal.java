package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETHERDGOALS;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.IHerdingPet;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class  HerdGoal<P extends Animal & ITradePostPet & IHerdingPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();
    private boolean goalLogToggle = false;

    private static final int PICKUP_RADIUS = 2;
    private static final int PICKUP_COOLDOWN_TICKS = 10;
    private int lastPickupTick = -PICKUP_COOLDOWN_TICKS;
    private static final int REPATH_COOLDOWN_TICKS = 10;
    private static final double REPATH_IF_TARGET_MOVED_SQ = 3.0 * 3.0;
    private int lastRepathTick = -REPATH_COOLDOWN_TICKS;
    
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

        this.setFlags(Objects.requireNonNull(EnumSet.of(Goal.Flag.MOVE)));
    }

    /**
     * Searches for an animal that needs herding and sets the target accordingly.
     * 
     * @return true if an animal is found, false otherwise
     */
    public boolean findAnimalsThatNeedHerding()
    {
        clearTarget();
        this.herdTarget = pet.getWorkLocation();
        
        if (herdTarget == null || BlockPos.ZERO.equals(herdTarget)) return false;

        @Nonnull AABB box = NullnessBridge.assumeNonnull(pet.getBoundingBox().inflate(SEARCH_RADIUS));
        List<? extends Animal> targetsNearby = pet.getPetData().searchForCompatibleAnimals(pet.level(), box);

        if (!targetsNearby.isEmpty())
        {
            for (Animal animalTarget : targetsNearby) 
            {
                double targetDistance = BlockPosUtil.getDistance(animalTarget.getOnPos(), herdTarget);

                if (targetDistance > HERDING_DISTANCE)
                {
                    currentTargetAnimal = animalTarget;
                    pet.setTargetPosition(currentTargetAnimal.getOnPos());
                    break;
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
            TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, loggingStatement);
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
            TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, () -> LOGGER.info("Starting towards target: {}", currentTargetAnimal));
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
        final Level level = pet.level();
        
        if (!level.isClientSide)
        {
            tryPickupNearbyDrops(level);
        }

        final Animal localTargetAnimal = currentTargetAnimal;
        if (localTargetAnimal == null) 
        {
            TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, () -> LOGGER.info("No target on tick()."));
            reset();
            return;
        }

        if (herdTarget == null || BlockPos.ZERO.equals(herdTarget)) 
        {
            TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, () -> LOGGER.info("No herding destination on tick()."));
            reset();
            return;
        }

        final BlockPos targetPos = localTargetAnimal.blockPosition();
        pet.setTargetPosition(targetPos);

        if (navigationResult != null && navigationResult.failedToReachDestination()) 
        {
            TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, () -> LOGGER.info("Failed to reach {} at {}", localTargetAnimal, targetPos));

            reset();
            return;
        }
 
        double distance = pet.distanceTo(localTargetAnimal);

        if (distance > 2 && !walkCommandSent)
        {
            TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, () -> LOGGER.info("Restarting towards target. Distance: {}", distance));
            navigationResult = ((MinecoloniesAdvancedPathNavigate)pet.getNavigation()).walkToEntity(localTargetAnimal, 1.0);
            walkCommandSent = true;
            targetStuckSteps = 0;
        }
        else if (distance < 2)
        {
            walkCommandSent = false;
            double targetDistance = BlockPosUtil.getDistance(targetPos, herdTarget);

            if (targetDistance > HERDING_DISTANCE)
            {

                if ((targetDistance < HERDING_DISTANCE * 2 ) || targetStuckSteps > PetData.STUCK_STEPS)
                {
                    final boolean navOk = tryPathAnimalToHerdTarget(localTargetAnimal, herdTarget, 1.0);

                    if (!navOk)
                    {
                        // Navigator couldn't/wouldn't path: stop it so it doesn't fight the nudge
                        localTargetAnimal.getNavigation().stop();

                        // Nudge it.
                        nudgeAnimalToward(localTargetAnimal, herdTarget, 0.2f);
                    }
                }
                else
                {
                    // Nudge herded animal toward the target
                    nudgeAnimalToward(localTargetAnimal, herdTarget, .06f);
                }

                final BlockPos currentTargetPos = localTargetAnimal.blockPosition();
                if (currentTargetPos.equals(targetLastOn))
                {
                    targetStuckSteps++;
                }
                else
                {
                    targetStuckSteps = 0;
                }

                // Boost the animal up if stuck repeatedly
                if (targetStuckSteps > PetData.STUCK_STEPS) {
                    Vec3 movement = localTargetAnimal.getDeltaMovement();
                    localTargetAnimal.setDeltaMovement(movement.x, 0.5, movement.z);
                    localTargetAnimal.hurtMarked = true;
                }

            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, () -> LOGGER.info("Animal is close enough to target position: {}", herdTarget));
                StatsUtil.trackStatByName(pet.getTrainerBuilding(), BuildingPetshop.ANIMALS_HERDED, localTargetAnimal.getName(), 1);
                reset();
            }

        }

        if (petLastOn != null && petLastOn.equals(pet.getOnPos()))
        {
            pet.incrementStuckTicks();
        }
        else
        {
            pet.clearStuckTicks();
        }

        if (pet.getStuckTicks() > PetData.STUCK_STEPS)
        {
            TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, () -> LOGGER.info("Nudging pet towards target position: {}. Stuck steps: {}", targetPos, pet.getStuckTicks()));

            double dx = (pet.getRandom().nextDouble() - 0.5) * 0.5;
            double dz = (pet.getRandom().nextDouble() - 0.5) * 0.5;
            Vec3 current = pet.getDeltaMovement();
            Vec3 nudge = Objects.requireNonNull(current.add(dx, 0.3, dz));
            pet.setDeltaMovement(nudge);
            pet.hurtMarked = true;
            pet.getNavigation().stop();
        }

        repathIfNeeded(localTargetAnimal);

    }

    /**
     * Checks if the pet should re-path to the target animal. Will re-path if the target animal has moved a certain distance, the pet is stuck, the path has failed, or the pet is not within a certain distance of the target animal and a walk command has not been sent yet.
     * Will not re-path if the pet is within the certain distance of the target animal and a walk command has been sent, or if the pet has not been stuck for a certain amount of time.
     * If a re-path is needed, sends a new walk command to the pet and updates the last re-path tick count.
     * Returns true if a re-path was needed, false if not.
     * @param targetAnimal the animal to re-path to
     * @return true if a re-path was needed, false if not
     */
    private boolean repathIfNeeded(@Nonnull Animal targetAnimal)
    {
        boolean didRepath = false;
        final BlockPos targetPos = targetAnimal.blockPosition();
        double distance = pet.distanceTo(targetAnimal);
        final boolean targetMovedEnough = targetLastOn != null && targetPos.distSqr(targetLastOn) >= REPATH_IF_TARGET_MOVED_SQ;
        final boolean petStuck = pet.getStuckTicks() > PetData.STUCK_STEPS;
        final boolean pathFailed = navigationResult != null && navigationResult.failedToReachDestination();
        final boolean cooldownReady = (pet.tickCount - lastRepathTick) >= REPATH_COOLDOWN_TICKS;

        // Repath only if we need to, and not too often
        if (cooldownReady && (pathFailed || petStuck || (!walkCommandSent && distance > 2) || targetMovedEnough))
        {
            navigationResult = ((MinecoloniesAdvancedPathNavigate) pet.getNavigation()).walkToEntity(targetAnimal, 1.0);
            walkCommandSent = true;
            lastRepathTick = pet.tickCount;
            didRepath = true;
        }

        targetLastOn = targetPos;
        petLastOn = pet.getOnPos();
        
        return didRepath;
    }

    /**
     * Attempts to issue a pathing command to an animal to move to the herd target position at the given speed.
     * Returns true if the command was accepted and the animal is attempting to move to the target, false if the command was not accepted or the animal is not making progress towards the target.
     * 
     * @param animal the animal to issue the command to
     * @param herdTarget the target position to move to
     * @param speed the speed to move at
     * @return true if the command was accepted and the animal is attempting to move to the target, false otherwise
     */
    private boolean tryPathAnimalToHerdTarget(final Animal animal, final BlockPos herdTarget, final double speed)
    {
        // Issue move command (may or may not actually take)
        final boolean accepted = animal.getNavigation().moveTo(
            herdTarget.getX() + 0.5,
            herdTarget.getY(),
            herdTarget.getZ() + 0.5,
            speed
        );

        // If it didn't accept the command, treat as failure immediately
        if (!accepted)
        {
            return false;
        }

        return true;
    }

    /**
     * Nudge the given animal towards the herd target position.
     * 
     * Stop the animal's navigation first to prevent it from immediately overriding the given velocity.
     * Then, set the animal's delta movement to the given position, and mark it as hurt.
     * 
     * @param animal the animal to nudge
     * @param herdTarget the position to nudge the animal towards
     */
    private void nudgeAnimalToward(final Animal animal, final BlockPos herdTarget, float nudgeForce)
    {
        final Vec3 animalPos = animal.position();
        final Vec3 targetVec = new Vec3(herdTarget.getX() + 0.5, animalPos.y, herdTarget.getZ() + 0.5);
        final Vec3 direction = targetVec.subtract(animalPos).normalize().scale(nudgeForce);

        // Stop nav first so AI doesn't immediately override your velocity
        animal.getNavigation().stop();

        animal.setDeltaMovement(Objects.requireNonNull(direction));
        animal.hurtMarked = true;
    }


    /**
     * Resets the state of the herding goal, clearing the current target animal and resetting internal counters.
     */
    protected void reset()
    {
        clearTarget();
        lastPickupTick = -PICKUP_COOLDOWN_TICKS;
        lastRepathTick = -REPATH_COOLDOWN_TICKS;
        targetLastOn = BlockPos.ZERO;
    }

    protected void clearTarget()
    {
        currentTargetAnimal = null;
        walkCommandSent = false;
        navigationResult = null;
        targetStuckSteps = 0; 
    }

    /**
     * Attempts to pick up any nearby item entities within a certain radius of the
     * pet. If the pet can fit the item in its inventory, it will be inserted and the
     * item entity will be removed. If the item is only partially inserted, the remainder
     * will be left in the item entity. The pet will play a pickup sound in either case.
     * <p>
     * The method is rate-limited to prevent the pet from spamming pickup sounds if there
     * are a large number of items in a small area.
     * <p>
     * @param level the level in which the pet is searching for items
     */
    private void tryPickupNearbyDrops(final Level level)
    {
        if (pet.tickCount - lastPickupTick < PICKUP_COOLDOWN_TICKS) return;
        lastPickupTick = pet.tickCount;

        final AABB box = pet.getBoundingBox().inflate(PICKUP_RADIUS);

        final ItemStackHandler localInventory = pet.getInventory();
        if (localInventory == null || box == null) return;

        final List<ItemEntity> nearbyDrops =
            level.getEntitiesOfClass(ItemEntity.class, box, ie -> ie.isAlive() && !ie.hasPickUpDelay());

        for (ItemEntity drop : nearbyDrops)
        {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) continue;

            ItemStack remainder = ItemHandlerHelper.insertItem(localInventory, stack, false);

            final @Nonnull SoundEvent sound = Objects.requireNonNull(SoundEvents.ITEM_PICKUP);

            if (remainder.isEmpty())
            {
                drop.discard();
                pet.playSound(sound,
                    0.2F,
                    1.0F + (pet.getRandom().nextFloat() - pet.getRandom().nextFloat()) * 0.7F);
            }
            else if (remainder.getCount() != stack.getCount())
            {
                drop.setItem(remainder);
                pet.playSound(sound,
                    0.2F,
                    1.0F + (pet.getRandom().nextFloat() - pet.getRandom().nextFloat()) * 0.7F);
            }
        }
    }
}
