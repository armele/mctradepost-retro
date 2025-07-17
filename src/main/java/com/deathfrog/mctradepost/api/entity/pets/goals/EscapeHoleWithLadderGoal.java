package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.PetWolf;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;

public class EscapeHoleWithLadderGoal extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private final PetWolf wolf;
    private BlockPos targetLadderTop;

    public EscapeHoleWithLadderGoal(PetWolf wolf)
    {
        this.wolf = wolf;
    }

    /**
     * Determines if the escape hole with ladder goal can be used.
     * 
     * This goal can be used if the wolf is on the ground, is in a hole, and has been stuck for at least 40 ticks.
     * Additionally, there must be a ladder exit nearby that the wolf can use to escape the hole.
     * 
     * @return true if the escape hole with ladder goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Do we need to escape? OnGround: {} InHole: {} StuckTicks: {}", wolf.onGround(), isInHole(), wolf.getStuckTicks()));

        // Only activate if stuck in a hole for a while
        if (wolf.onGround() && isInHole() && wolf.getStuckTicks() > PetWolf.STUCK_STEPS)
        {
            targetLadderTop = findNearbyLadderExit();

            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Nearby ladder exit: {}", targetLadderTop));

            return targetLadderTop != null;
        }

        return false;
    }

    /**
     * Checks if the wolf is done escaping the hole with the ladder. This is true if the wolf has reached its target
     * ladder top position or if there is no target position set.
     * @return true if the wolf is done escaping the hole with the ladder, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        return targetLadderTop != null && !wolf.getNavigation().isDone();
    }

    /**
     * Moves the wolf to the target ladder top if it has been set. This is done by calling the wolf's navigation
     * to move to the target position with a speed of 1.0.
     */
    @Override
    public void start()
    {
        if (targetLadderTop != null)
        {
            wolf.getNavigation().moveTo(targetLadderTop.getX() + 0.5, targetLadderTop.getY(), targetLadderTop.getZ() + 0.5, 1.0);
        }
    }

    /**
     * Resets the target ladder top and stuck ticks when the goal is stopped.
     */
    @Override
    public void stop()
    {
        targetLadderTop = null;
    }

    /**
     * Returns true if the wolf is in a hole by checking if the wolf is more than two blocks below its target position.
     * This is a simple heuristic to determine if the wolf is stuck in a hole.
     * @return true if in a hole, false otherwise
     */
    private boolean isInHole()
    {
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, 
            () -> LOGGER.info("Checking if wolf is in a hole. Wolf Pos: {} Target Pos: {}", wolf.getOnPos(), wolf.getTargetPosition()));

        if (wolf.getTargetPosition().equals(BlockPos.ZERO))
        {
             return false;
        }
        
        return wolf.getOnPos().getY() < wolf.getTargetPosition().getY() - 2;
    }

    /**
     * Finds the nearest ladder exit above the wolf. This is done by looping up 10 blocks and
     * checking if the block is a ladder and there is air above it. If so, it returns the block
     * above the ladder as the nearest ladder exit. If no ladder exit is found, it returns null.
     * @return nearest ladder exit above the wolf, or null if no ladder exit is found
     */
    private BlockPos findNearbyLadderExit()
    {
        BlockPos.MutableBlockPos pos = wolf.blockPosition().mutable();
        for (int y = 0; y < 10; y++)
        {
            pos.move(0, 1, 0);
            if (!wolf.level().getBlockState(pos).is(BlockTags.CLIMBABLE)) continue;
            BlockPos airAbove = pos.above();
            if (wolf.level().isEmptyBlock(airAbove))
            {
                return airAbove;
            }
        }
        return null;
    }
}
