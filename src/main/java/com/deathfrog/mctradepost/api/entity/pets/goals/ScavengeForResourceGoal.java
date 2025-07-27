package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ScavengeForResourceGoal<P extends Animal & ITradePostPet> extends Goal {
    public static final Logger LOGGER = LogUtils.getLogger();
    
    private final P pet;
    private final int searchRadius;
    private final double maxLight;
    private final float chanceToFind;
    private final Predicate<BlockPos> locationPredicate;
    private final Consumer<BlockPos> successAction;

    private BlockPos targetPos;
    private boolean hasArrived = false;

    public ScavengeForResourceGoal(
        P pet,
        int searchRadius,
        double maxLight,
        float chanceToFind,
        Predicate<BlockPos> locationPredicate,
        Consumer<BlockPos> successAction
    ) {
        this.pet = pet;
        this.searchRadius = searchRadius;
        this.maxLight = maxLight;
        this.chanceToFind = chanceToFind;
        this.locationPredicate = locationPredicate;
        this.successAction = successAction;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /**
     * Determines if the goal can be started.
     *
     * @return true if the pet is alive, not a passenger, not leashed, and a suitable location is found; otherwise false.
     */
    @Override
    public boolean canUse() {
        if (!pet.isAlive() || pet.isPassenger() || pet.isLeashed()) return false;

        if (pet.getPetData() == null || pet.level() == null)
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No pet data found or pet level not yet set."));
            return false;
        }

        if (!PetRoles.SCAVENGING.equals(pet.getPetData().roleFromWorkLocation(pet.level())))
        {
            return false;
        }

        targetPos = findSuitableLocation();
        return targetPos != null;
    }

    /**
     * Starts the goal.
     *
     * If a suitable location is found, the navigation AI is instructed to move to that location.
     */
    @Override
    public void start() {
        if (targetPos != null) {
            pet.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
        }
    }

    /**
     * Determines if the goal should be continued.
     *
     * @return true if the suitable location is not null, the pet has not yet arrived, and the navigation is not yet done; otherwise false.
     */
    @Override
    public boolean canContinueToUse() {
        return targetPos != null && !pet.getNavigation().isDone() && !hasArrived;
    }

    /**
     * Ticks the scavenge goal.
     *
     * <p>When the pet arrives at the target location, there is a chance to find a resource. If a resource is found, the success action is
     * invoked with the target position as a parameter. The goal is then complete.</p>
     */
    @Override
    public void tick() {
        if (targetPos != null && pet.blockPosition().closerToCenterThan(Vec3.atCenterOf(targetPos), 1.5)) {
            hasArrived = true;
            if (pet.getRandom().nextFloat() < chanceToFind) {
                successAction.accept(targetPos);
            }
        }
    }

    /**
     * Resets the goal to its initial state.
     *
     * <p>Invoked when the goal is no longer active. Resets the target position to null and the "has arrived" flag to false.</p>
     */
    @Override
    public void stop() {
        targetPos = null;
        hasArrived = false;
    }

    /**
     * Finds a suitable location for the pet to scavenge for resources.
     *
     * <p>Starting from the pet's current position, 20 random locations are
     * checked to see if they are dark enough and pass the custom location
     * predicate. The first suitable location is returned. If no locations are
     * found, null is returned.</p>
     *
     * @return a suitable location to scavenge for resources, or null if none
     *         was found.
     */
    private BlockPos findSuitableLocation() {
        final Level level = pet.level();
        BlockPos origin = pet.getWorkLocation();
        for (int tries = 0; tries < 20; tries++) {
            BlockPos candidate = origin.offset(
                pet.getRandom().nextInt(searchRadius * 2) - searchRadius,
                pet.getRandom().nextInt(6) - 3,
                pet.getRandom().nextInt(searchRadius * 2) - searchRadius
            );

            // Only consider if it's dark enough and passes custom conditions
            if (level.getMaxLocalRawBrightness(candidate) <= maxLight && locationPredicate.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
