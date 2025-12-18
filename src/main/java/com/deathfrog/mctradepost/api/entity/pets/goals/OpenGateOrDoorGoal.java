package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Open a door/fence gate slightly ahead on the current path and pass through
 * smoothly without oscillation or stalling in the threshold.
 *
 * Anti-oscillation:
 *  - Only triggers if the door is in front of the mob (along door facing) and near the path.
 *  - "Single pass": open once, keep open until pet clears plane, close once, then blacklist door briefly.
 *  - Global short cooldown after finishing to avoid rapid re-triggers from micro path updates.
 *
 * Smooth pass-through:
 *  - Aim 2 blocks beyond the doorway, not the doorway itself.
 *  - Small open delay only if we’re not already at the threshold.
 *  - Temporary "thrust" with MoveControl while inside the doorway to prevent pauses.
 */
public class OpenGateOrDoorGoal extends Goal
{
    private final Mob mob;
    private final boolean closeAfter;
    private final int closeDelayTicks; // counted only after CLEARING the door plane

    // Target passage
    private @Nonnull BlockPos targetPos = Objects.requireNonNull(BlockPos.ZERO);
    private BooleanProperty openProperty = null; // DoorBlock.OPEN or FenceGateBlock.OPEN
    private boolean isGate = false;
    private BlockPos passThroughPos = null;

    // State / timers
    private int closeCountdown = 0;
    private boolean openedThisRun = false;
    private int openDelayTicks = 0;
    private int approachSign = +1; // +1 means we intend to move in +facing, -1 means in -facing

    // Lightweight stuck watchdog (near threshold)
    private int stuckTicks = 0;
    private Vec3 lastPos = Vec3.ZERO;

    // Anti-oscillation
    private static final int DOOR_BLACKLIST_TICKS = 20 * 5; // 5s
    private final Map<BlockPos, Integer> doorBlacklist = new HashMap<>();
    private static final int GLOBAL_COOLDOWN_TICKS = 10; // 0.5s
    private int globalCooldown = 0;

    // Tunables
    private static final int OPEN_DELAY = 5;               // ~0.25s
    private static final int STUCK_LIMIT = 20;             // ~1s without progress
    private static final double PROGRESS_EPSILON_SQ = 0.01;// ~0.1 block^2
    private static final double PATH_PROXIMITY_MAX_DIST_SQ = 9.0; // <=3 blocks to path node
    private static final double PAST_PLANE_THRESHOLD = 0.9;        // must be clearly past before closing
    private static final double FAR_PAST_PLANE_THRESHOLD = 1.5;    // if already far past, do not trigger
    private static final double MAX_DOOR_TRIGGER_DIST_SQ = 16.0;   // <=4 blocks to door center

    // NEW: thrust to push through the threshold even if navigation thinks we're "close enough"
    private static final int THRUST_TICKS_MAX = 10; // ~0.5s of forward push
    private int thrustTicks = 0;

    public OpenGateOrDoorGoal(Mob mob, boolean closeAfter, int closeDelayTicks)
    {
        this.mob = mob;
        this.closeAfter = closeAfter;
        this.closeDelayTicks = Math.max(1, closeDelayTicks);
        this.setFlags(Objects.requireNonNull(EnumSet.of(Goal.Flag.MOVE)));
    }

    @Override
    public boolean canUse()
    {
        // Decay timers/blacklist
        if (globalCooldown > 0) globalCooldown--;
        if (!doorBlacklist.isEmpty()) {
            doorBlacklist.replaceAll((p, t) -> t - 1);
            doorBlacklist.entrySet().removeIf(e -> e.getValue() <= 0);
        }

        if (globalCooldown > 0) return false;
        if (mob.isPassenger() || mob.isDeadOrDying()) return false;

        PathNavigation nav = mob.getNavigation();
        if (nav == null || nav.isDone()) return false;

        Path path = nav.getPath();
        if (path == null) return false;

        // Look slightly ahead on the path (like vanilla)
        int next = path.getNextNodeIndex();
        int maxCheck = Math.min(next + 2, path.getNodeCount() - 1);
        Level level = mob.level();

        for (int i = next; i <= maxCheck; i++)
        {
            Node node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);

            Direction dir = mob.getDirection();

            if (dir == null) continue;

            // Check node, node above, and block in front of the mob
            BlockPos[] candidates = new BlockPos[] { pos, pos.above(), mob.blockPosition().relative(dir) };
            for (BlockPos cPos : candidates)
            {
                if (cPos == null) continue;

                BlockState st = level.getBlockState(cPos);
                if (st == null || !isPassageBlock(st)) continue;

                // Anti-oscillation: skip if blacklisted
                if (doorBlacklist.getOrDefault(cPos, 0) > 0) continue;

                // Forward-only guard: door must not be far past
                Direction facing = getFacing(st);
                if (facing == null || !isAheadOfDoorPlane(cPos, facing)) continue;

                // Already far past? don't engage
                if (planeAlongAmount(cPos, facing) > FAR_PAST_PLANE_THRESHOLD) continue;

                // Path/mob proximity guard
                if (!isNearPathOrSelf(cPos, path)) continue;

                // Range guard
                Vec3 mobPos = mob.position();

                if (mobPos == null) continue;

                if (Vec3.atCenterOf(cPos).distanceToSqr(mobPos) > MAX_DOOR_TRIGGER_DIST_SQ) continue;

                // Accept target
                setTarget(cPos, st);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canContinueToUse()
    {
        // Continue while we have a target and either we've opened it or we're counting down to close.
        return targetPos != BlockPos.ZERO && (openedThisRun || (closeAfter && closeCountdown > 0));
    }

    @Override
    public void start()
    {
        if (targetPos != BlockPos.ZERO && openProperty != null)
        {
            openPairedIfNeeded();
            toggleOpen(true);
            openedThisRun = true;

            // If we are already in/at the doorway threshold, skip most of the open delay to avoid idling there
            openDelayTicks = isInsideDoorwayAABB() ? 0 : OPEN_DELAY;

            lastPos = mob.position();
            stuckTicks = 0;

            // Give a little "thrust" if we start in/at the threshold
            thrustTicks = isInsideDoorwayAABB() ? THRUST_TICKS_MAX : 0;

            if (closeAfter) closeCountdown = closeDelayTicks;
        }
    }

    @Override
    public void tick()
    {
        // Close-after logic only *after* we’ve clearly crossed the plane
        if (closeAfter && openedThisRun && targetPos != BlockPos.ZERO)
        {
            if (hasClearedDoorPlane(PAST_PLANE_THRESHOLD))
            {
                if (closeCountdown > 0) closeCountdown--;
                if (closeCountdown == 0)
                {
                    toggleOpen(false);
                    blacklistAndReset(); // single close, then fully reset/blacklist
                    return;
                }
            }
            else
            {
                // Not past the plane yet: keep door open and reset the countdown
                closeCountdown = closeDelayTicks;
            }
        }

        // Short delay so collision shapes settle before movement (skipped if we're in the doorway)
        if (openDelayTicks > 0)
        {
            openDelayTicks--;
            return;
        }

        // Always keep steering toward the pass-through target (2 blocks beyond)
        if (passThroughPos != null)
        {
            // Issue/refresh nav target periodically; avoids nav thinking "done" too early
            if (mob.getNavigation().isDone() || isInsideDoorwayAABB())
            {
                mob.getNavigation().moveTo(
                    passThroughPos.getX() + 0.5,
                    passThroughPos.getY(),
                    passThroughPos.getZ() + 0.5,
                    1.12
                );
            }

            // While in the doorway or for a few ticks after opening, push with MoveControl too
            if (isInsideDoorwayAABB() || thrustTicks > 0)
            {
                mob.getMoveControl().setWantedPosition(
                    passThroughPos.getX() + 0.5,
                    passThroughPos.getY(),
                    passThroughPos.getZ() + 0.5,
                    1.25
                );
                if (thrustTicks > 0) thrustTicks--;
            }
        }

        // Lightweight stuck detection near threshold; re-issue move if stalled
        Vec3 now = mob.position();

        if (now == null) return;

        if (lastPos == null) lastPos = now;

        if (now.distanceToSqr(Objects.requireNonNull(lastPos)) < PROGRESS_EPSILON_SQ) stuckTicks++;
        else { stuckTicks = 0; lastPos = now; }

        if (stuckTicks > STUCK_LIMIT && passThroughPos != null)
        {
            mob.getNavigation().moveTo(
                passThroughPos.getX() + 0.5,
                passThroughPos.getY(),
                passThroughPos.getZ() + 0.5,
                1.12
            );
            // give a brief thrust kick again
            thrustTicks = Math.max(thrustTicks, 4);
            stuckTicks = 0;
        }
    }

    @Override
    public void stop()
    {
        // If stopping early and we intended to close, only close if we’re past the plane.
        if (closeAfter && openedThisRun && targetPos != BlockPos.ZERO && openProperty != null)
        {
            if (hasClearedDoorPlane(PAST_PLANE_THRESHOLD))
            {
                toggleOpen(false);
            }
            // else: leave open; some other actor can close it, and we’ll blacklist below.
        }
        blacklistAndReset();
    }

    // =========================
    // helpers
    // =========================

    private void blacklistAndReset()
    {
        if (targetPos != BlockPos.ZERO)
        {
            doorBlacklist.put(targetPos.immutable(), DOOR_BLACKLIST_TICKS);
        }
        globalCooldown = GLOBAL_COOLDOWN_TICKS;

        openedThisRun = false;
        targetPos = Objects.requireNonNull(BlockPos.ZERO);
        openProperty = null;
        passThroughPos = null;
        closeCountdown = 0;
        isGate = false;
        openDelayTicks = 0;
        stuckTicks = 0;
        thrustTicks = 0;
    }

    private boolean isPassageBlock(BlockState state)
    {
        if (state.is(Objects.requireNonNull(BlockTags.DOORS)) && state.getBlock() instanceof DoorBlock)
        {
            openProperty = DoorBlock.OPEN;
            isGate = false;
            return true;
        }
        if (state.is(Objects.requireNonNull(BlockTags.FENCE_GATES)) && state.getBlock() instanceof FenceGateBlock)
        {
            openProperty = FenceGateBlock.OPEN;
            isGate = true;
            return true;
        }
        return false;
    }

    private Direction getFacing(BlockState state)
    {
        if (isGate && state.getBlock() instanceof FenceGateBlock)
        {
            return state.getValue(Objects.requireNonNull(FenceGateBlock.FACING));
        }
        if (state.getBlock() instanceof DoorBlock)
        {
            return state.getValue(Objects.requireNonNull(DoorBlock.FACING));
        }
        return mob.getDirection(); // fallback
    }

    /**
     * Toggles the door or gate at the target position to be open or closed.
     * If the target position is null or the open property is null, this method does nothing.
     * If the target block is not a door or fence gate, this method does nothing.
     * If the target block has changed since the AI's last update, this method does nothing.
     * If the target block is currently open and the open parameter is false, this method closes the block.
     * If the target block is currently closed and the open parameter is true, this method opens the block.
     * If the target block is currently open and the open parameter is true, or if the target block is currently closed and the open parameter is false, this method does nothing.
     * @param open whether the target block should be open or closed
     */
    private void toggleOpen(boolean open)
    {
        final BlockPos localTargetPos = this.targetPos;
        final BooleanProperty localOpenProp = this.openProperty;

        if (localTargetPos == BlockPos.ZERO || localOpenProp == null) return;

        Level level = mob.level();
        BlockState state = level.getBlockState(localTargetPos);
        if (!state.hasProperty(localOpenProp)) return; // block changed

        if (isGate && state.getBlock() instanceof FenceGateBlock)
        {
            boolean currentlyOpen = state.getValue(Objects.requireNonNull(FenceGateBlock.OPEN));
            if (currentlyOpen != open)
            {
                final BlockState newState = state.setValue(Objects.requireNonNull(FenceGateBlock.OPEN), open);

                if (newState != null)
                {
                    level.setBlock(localTargetPos, newState, Block.UPDATE_CLIENTS);
                }

            }
            return;
        }

        boolean currentlyOpen = state.getValue(localOpenProp);
        if (currentlyOpen != open)
        {
            final BlockState newState = state.setValue(localOpenProp, open);
            if (newState != null)
            {
                level.setBlock(localTargetPos, newState, Block.UPDATE_CLIENTS);
            }

        }
    }

    /** If this is a double door, also open the mate so we don’t shoulder-bump. */
    private void openPairedIfNeeded()
    {
        final BlockPos localTargetPos = this.targetPos;
        if (localTargetPos == BlockPos.ZERO) return;

        Level level = mob.level();
        BlockState s = level.getBlockState(localTargetPos);

        if (!(s.getBlock() instanceof DoorBlock)) return;

        Direction facing = s.getValue(Objects.requireNonNull(DoorBlock.FACING));
        DoorHingeSide hinge = s.getValue(Objects.requireNonNull(DoorBlock.HINGE));

        if (hinge == null) return;

        Direction dir = hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise();

        if (dir == null) return;

        BlockPos matePos = localTargetPos.relative(dir);

        if (matePos == null) return;

        BlockState mate = level.getBlockState(matePos);

        if (mate.getBlock() instanceof DoorBlock && mate.getValue(Objects.requireNonNull(DoorBlock.FACING)) == facing)
        {
            if (!mate.getValue(Objects.requireNonNull(DoorBlock.OPEN)))
            {
                final BlockState newState = mate.setValue(Objects.requireNonNull(DoorBlock.OPEN), true);
                if (newState != null)
                {
                    level.setBlock(matePos, newState, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** How far the mob is along the door facing normal (positive = in front/past). */
    private double planeAlongAmount(@Nonnull BlockPos door, @Nonnull Direction facing)
    {
        Vec3 doorCenter = Vec3.atCenterOf(door);
        Vec3 toMob = mob.position().subtract(Objects.requireNonNull(doorCenter));
        return toMob.x * facing.getStepX() + toMob.z * facing.getStepZ();
    }

    /**
     * Sets the target door/gate and computes the approach direction, along with the pass-through position.
     * @param pos the BlockPos of the door/gate
     * @param state the BlockState of the door/gate
     */
    private void setTarget(@Nonnull BlockPos pos, @Nonnull BlockState state)
    {
        this.targetPos = Objects.requireNonNull(pos.immutable());
        final Direction facing = Objects.requireNonNull(getFacing(state));

        // Are we on the "negative" (behind) or "positive" (in front) side of the door plane?
        double along = planeAlongAmount(targetPos, facing);
        // If we're behind (along < 0), we want to move in +facing; if we're in front (along >= 0), move in -facing.
        this.approachSign = (along < 0) ? +1 : -1;

        // Aim TWO blocks through the doorway, in the correct direction for our approach.
        this.passThroughPos = targetPos.relative(facing, 2 * approachSign).immutable();
    }

    /**
     * Checks if the mob is past the door plane by the specified threshold.
     * <p>
     * This is used to determine if the mob has cleared the doorway and can close the door/gate.
     * <p>
     * The threshold is given in terms of blocks, and is the minimum distance the mob must be past the
     * door plane in the approach direction. If the mob has gone beyond this threshold, the method
     * returns true; otherwise, it returns false.
     * @param threshold the minimum distance past the door plane the mob must be
     * @return true if the mob has cleared the door plane, false otherwise
     */
    private boolean hasClearedDoorPlane(double threshold)
    {
        BlockState state = mob.level().getBlockState(targetPos);
        Direction facing = getFacing(state);

        if (facing == null) return false;

        double along = planeAlongAmount(targetPos, facing);
        // Consider cleared when we've gone threshold distance past the plane in our intended direction.
        return (along * approachSign) > threshold;
    }

    /**
     * Checks if the mob is already ahead of the door plane by a fair margin.
     * <p>
     * We consider the mob to be "ahead" if it has gone at least {@link #FAR_PAST_PLANE_THRESHOLD} blocks past the
     * door plane in the approach direction. If the mob is already ahead, we can skip the thrust
     * phase and just close the door/gate directly.
     * <p>
     * Using approachSign makes the check symmetric so we don't need to special-case the sign of along.
     * @param door the BlockPos of the door/gate
     * @param facing the direction the door/gate is facing
     * @return true if the mob is ahead of the door plane, false otherwise
     */
    private boolean isAheadOfDoorPlane(@Nonnull BlockPos door, @Nonnull Direction facing)
    {
        // Use approachSign so the “already far past?” check is symmetric.
        double along = planeAlongAmount(door, facing);
        return (along * ((along < 0) ? +1 : -1)) < FAR_PAST_PLANE_THRESHOLD;
        // (Or simply return true here and rely on FAR_PAST check below if you prefer.)
    }


    /** Consider “inside doorway” as within the 1x2 block column occupied by the door/gate (with a tiny inflation). */
    private boolean isInsideDoorwayAABB()
    {
        AABB doorway = new AABB(targetPos).inflate(0.05).expandTowards(0, 1, 0); // 1x2 column
        Vec3 mobPos = Objects.requireNonNull(mob.position());
        return doorway.inflate(0.05).contains(mobPos);
    }

    /** Door should be close to the current path or the mob; prevents engaging doors off-route. */
    private boolean isNearPathOrSelf(@Nonnull BlockPos door, Path path)
    {
        // Near mob?
        Vec3 mobPos = Objects.requireNonNull(mob.position());
        if (Vec3.atCenterOf(door).distanceToSqr(mobPos) <= PATH_PROXIMITY_MAX_DIST_SQ) return true;

        // Near any of the next few nodes?
        int next = path.getNextNodeIndex();
        int maxCheck = Math.min(next + 3, path.getNodeCount() - 1);
        for (int i = next; i <= maxCheck; i++)
        {
            Node n = path.getNode(i);
            Vec3 np = new Vec3(n.x + 0.5, n.y + 0.5, n.z + 0.5);
            Vec3 doorCenter = Objects.requireNonNull(Vec3.atCenterOf(door));
            if (np.distanceToSqr(doorCenter) <= PATH_PROXIMITY_MAX_DIST_SQ) return true;
        }
        return false;
    }
}
