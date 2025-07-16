package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.PetWolf;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.phys.Vec3;

public class HerdGoal extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private final PetWolf wolf;
    private BlockPos herdTarget;
    private Sheep targetSheep;

    private static final int SEARCH_RADIUS = 100;
    private static final int HERDING_DISTANCE = 5;

    public HerdGoal(PetWolf wolf)
    {
        this.wolf = wolf;

        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    // TODO: Generalize what can be herded.

    /**
     * Searches for sheep that need herding and sets the targetSheep accordingly.
     * 
     * @return true if a sheep is found, false otherwise
     */
    public boolean findSheepThatNeedHerding()
    {
        List<Sheep> sheepNearby = wolf.level()
            .getEntitiesOfClass(Sheep.class,
                wolf.getBoundingBox().inflate(SEARCH_RADIUS),
                sheep -> !sheep.isBaby() && sheep.isAlive());

        if (!sheepNearby.isEmpty())
        {
            for (Sheep sheep : sheepNearby) 
            {
                double targetDistance = BlockPosUtil.getDistance(sheep.getOnPos(), herdTarget);

                if (targetDistance > HERDING_DISTANCE)
                {
                    targetSheep = sheep;
                }

            }
            
            if (targetSheep != null)
            {
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("There's a sheep nearby: {}", targetSheep.getOnPos()));

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
     * Additionally, there must be a target to herd to (i.e. a building) and there must be sheep nearby that need herding.
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

        IBuilding targetBuilding = wolf.getBuilding();
        if (targetBuilding != null)
        {
            this.herdTarget = targetBuilding.getPosition();
        }

        if (BlockPos.ZERO.equals(herdTarget))
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No target to herd to."));
            return false;
        }

        if (findSheepThatNeedHerding())
        {
            return true;
        }

        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No sheep nearby."));
        return false;
    }

    /**
     * The herding goal is a continuous goal, so it can continue to run as long as there is a sheep to herd.
     * If the current target sheep is no longer available, it will search for a replacement.
     * @return false always
     */
    @Override
    public boolean canContinueToUse()
    {
        boolean keepherding = targetSheep != null && targetSheep.isAlive();

        if (!keepherding)
        {
            keepherding = findSheepThatNeedHerding();
        }

        return false;
    }

    /**
     * Stops the herding goal by clearing the current target sheep.
     */
    @Override
    public void stop()
    {
        targetSheep = null;
    }

    /**
     * Executes a single tick of the herding goal. If there is no target sheep, logs the absence
     * and returns. If a target sheep is present, it determines the distance to the sheep. If
     * the wolf is far from the sheep, it navigates towards it. If close, it checks the distance
     * of the sheep from the herd target. If the sheep is too far from the target, it nudges the
     * sheep towards the target position. Once the sheep is close enough to the target position,
     * it clears the target sheep, indicating the herding process for that sheep is complete.
     */
    @Override
    public void tick()
    {
        if (targetSheep == null) 
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("No target sheep on tick()."));
            return;
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Target sheep: {}", targetSheep.getOnPos()));
        }

        double distance = wolf.distanceTo(targetSheep);

        if (distance > 2)
        {
            // Move toward the sheep
            wolf.getNavigation().moveTo(targetSheep, 1.0);
        }
        else
        {
            double targetDistance = BlockPosUtil.getDistance(targetSheep.getOnPos(), herdTarget);

            if (targetDistance > HERDING_DISTANCE)
            {

                if (targetDistance < HERDING_DISTANCE * 2)
                {
                    targetSheep.getNavigation().moveTo(herdTarget.getX() + 0.5, herdTarget.getY(), herdTarget.getZ() + 0.5, 1.0);
                }

                // Nudge sheep toward the target
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Nudging sheep towards target position: {}", herdTarget));

                Vec3 sheepPos = targetSheep.position();
                Vec3 targetVec = new Vec3(herdTarget.getX() + 0.5, sheepPos.y, herdTarget.getZ() + 0.5);
                Vec3 direction = targetVec.subtract(sheepPos).normalize().scale(0.2);

                targetSheep.setDeltaMovement(direction);
                targetSheep.hurtMarked = true;  // Force position sync to client
            }
            else
            {
                targetSheep = null;
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Sheep is close enough to target position: {}", herdTarget));
            }

        }
    }
}
