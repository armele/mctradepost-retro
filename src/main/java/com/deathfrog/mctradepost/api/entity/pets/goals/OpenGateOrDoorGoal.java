package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

public class OpenGateOrDoorGoal extends Goal
{
    private final Mob mob;
    private final boolean closeAfter;
    private final int closeDelayTicks;      // how long to wait before closing (after we pass through)
    private BlockPos targetPos = null;      // the door/gate we are interacting with
    private BooleanProperty openProperty = null; // which property to toggle (DoorBlock.OPEN or FenceGateBlock.OPEN)
    private boolean isGate = false;

    private int closeCountdown = 0;
    private boolean openedThisRun = false;

    public OpenGateOrDoorGoal(Mob mob, boolean closeAfter, int closeDelayTicks)
    {
        this.mob = mob;
        this.closeAfter = closeAfter;
        this.closeDelayTicks = closeDelayTicks;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /**
     * Determines if the goal can be used by the given mob.
     * <p>
     * We check if the mob is moving somewhere (i.e. has a path) and isn't currently riding/leashed.
     * </p>
     * <p>
     * We then check the next few nodes of their path to see if any of them are passage blocks
     * (i.e. fences, doors, gates, etc.).
     * </p>
     * <p>
     * If we find such a block, we set the target and return true.
     * </p>
     * <p>
     * Otherwise, we return false.
     * </p>
     */
    @Override
    public boolean canUse()
    {
        // Must be moving somewhere and not riding/leashed state checked by your goal setup
        if (mob.isPassenger() || mob.isDeadOrDying()) return false;

        PathNavigation nav = mob.getNavigation();
        if (nav == null || nav.isDone()) return false;

        Path path = nav.getPath();
        if (path == null) return false;

        // Look at the next few nodes in front; vanilla checks ~2 steps
        int maxCheck = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount() - 1);
        Level level = mob.level();

        for (int i = path.getNextNodeIndex(); i <= maxCheck; i++)
        {
            Node node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);

            // Try direct node, node above (doors can be 2-tall), and in front of mob
            if (isPassageBlock(level.getBlockState(pos)))
            {
                setTarget(pos, level.getBlockState(pos));
                return true;
            }
            BlockPos up = pos.above();
            if (isPassageBlock(level.getBlockState(up)))
            {
                setTarget(up, level.getBlockState(up));
                return true;
            }
            BlockPos front = mob.blockPosition().relative(mob.getDirection());
            if (isPassageBlock(level.getBlockState(front)))
            {
                setTarget(front, level.getBlockState(front));
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the pet can continue using this goal.
     * 
     * This goal can continue to run if there is still a target passage and either
     * (a) we want to keep the passage open, or (b) we are counting down to close
     * the passage.
     * 
     * @return true if the pet can continue using this goal, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        // Continue while we still have a target passage and (a) want to keep it open
        // or (b) are counting down to closing it.
        return targetPos != null && (openedThisRun || (closeAfter && closeCountdown > 0));
    }

    /**
     * Opens the target passage immediately if possible. If the passage
     * is already open, does nothing. If the passage is closed, opens it
     * and sets the countdown to close it again after the given delay.
     */
    @Override
    public void start()
    {
        // Open immediately on start if possible
        if (targetPos != null && openProperty != null)
        {
            toggleOpen(true);
            openedThisRun = true;
            if (closeAfter)
            {
                closeCountdown = closeDelayTicks;
            }
        }
    }

    /**
     * Periodically called to check if the passage should be closed. If the close-after timer has expired, closes the passage.
     * If the passage is already closed, does nothing.
     * @see #canContinueToUse()
     */
    @Override
    public void tick()
    {
        if (closeAfter && openedThisRun && closeCountdown > 0)
        {
            closeCountdown--;
        }
        
        if (closeAfter && openedThisRun && closeCountdown == 0)
        {
            toggleOpen(false);
            // reset so we don’t keep re-closing
            openedThisRun = false;
            targetPos = null;
            openProperty = null;
        }
    }

    /**
     * If we stop early and we wanted to close, do so
     * Resets various state variables to prepare for next use
     */
    @Override
    public void stop()
    {
        // If we stop early and we wanted to close, do so
        if (closeAfter && openedThisRun && targetPos != null && openProperty != null)
        {
            toggleOpen(false);
        }
        openedThisRun = false;
        targetPos = null;
        openProperty = null;
        closeCountdown = 0;
        isGate = false;
    }

    // --- helpers ---

    /**
     * Checks if the given block state represents a passable door or fence gate.
     * If so, stores the property that controls the gate/door being open in {@link #openProperty}.
     * In addition, stores whether the gate/door is a fence gate or not in {@link #isGate}.
     * @param state the block state to check
     * @return true if the block state is a passable door or fence gate, false otherwise
     */
    private boolean isPassageBlock(BlockState state)
    {
        if (state.is(BlockTags.DOORS) && state.getBlock() instanceof DoorBlock)
        {
            openProperty = DoorBlock.OPEN;
            isGate = false;
            return true;
        }
        if (state.is(BlockTags.FENCE_GATES) && state.getBlock() instanceof FenceGateBlock)
        {
            openProperty = FenceGateBlock.OPEN;
            isGate = true;
            return true;
        }
        return false;
    }

    /**
     * Sets the target position and properties for the gate or door that we want to open/close.
     * @param pos the position of the gate or door
     * @param state the block state of the gate or door
     */
    private void setTarget(BlockPos pos, BlockState state)
    {
        this.targetPos = pos.immutable();
        // `openProperty` and `isGate` were set in isPassageBlock(state)
    }

    /**
     * Opens or closes a gate or door at our current target position, assuming it has been set.
     * @param open whether to open or close the gate/door
     */
    private void toggleOpen(boolean open)
    {
        if (targetPos == null || openProperty == null) return;

        Level level = mob.level();
        BlockState state = level.getBlockState(targetPos);
        if (!state.hasProperty(openProperty))
        {
            // Block changed under us; bail out gracefully
            return;
        }

        // For fence gates: only flip OPEN
        if (isGate && state.getBlock() instanceof FenceGateBlock)
        {
            boolean currentlyOpen = state.getValue(FenceGateBlock.OPEN);
            if (currentlyOpen != open)
            {
                BlockState newState = state.setValue(FenceGateBlock.OPEN, open);
                level.setBlock(targetPos, newState, Block.UPDATE_CLIENTS);
            }
            return;
        }

        // Doors (and anything else with an OPEN property): only flip OPEN
        boolean currentlyOpen = state.getValue(openProperty);
        if (currentlyOpen != open)
        {
            level.setBlock(targetPos, state.setValue(openProperty, open), Block.UPDATE_CLIENTS);
        }
    }

}
