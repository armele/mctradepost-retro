package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

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
    private BlockPos targetPos = null;
    private BooleanProperty openProperty = null; // DoorBlock.OPEN or FenceGateBlock.OPEN
    private boolean isGate = false;
    private BlockPos passThroughPos = null;

    // State / timers
    private int closeCountdown = 0;
    private boolean openedThisRun = false;
    private int openDelayTicks = 0;

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
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
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

            // Check node, node above, and block in front of the mob
            BlockPos[] candidates = new BlockPos[] { pos, pos.above(), mob.blockPosition().relative(mob.getDirection()) };
            for (BlockPos cPos : candidates)
            {
                BlockState st = level.getBlockState(cPos);
                if (!isPassageBlock(st)) continue;

                // Anti-oscillation: skip if blacklisted
                if (doorBlacklist.getOrDefault(cPos, 0) > 0) continue;

                // Forward-only guard: door must not be far past
                Direction facing = getFacing(st);
                if (!isAheadOfDoorPlane(cPos, facing)) continue;

                // Already far past? don't engage
                if (planeAlongAmount(cPos, facing) > FAR_PAST_PLANE_THRESHOLD) continue;

                // Path/mob proximity guard
                if (!isNearPathOrSelf(cPos, path)) continue;

                // Range guard
                if (Vec3.atCenterOf(cPos).distanceToSqr(mob.position()) > MAX_DOOR_TRIGGER_DIST_SQ) continue;

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
        return targetPos != null && (openedThisRun || (closeAfter && closeCountdown > 0));
    }

    @Override
    public void start()
    {
        if (targetPos != null && openProperty != null)
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
        if (closeAfter && openedThisRun && targetPos != null)
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
        if (now.distanceToSqr(lastPos) < PROGRESS_EPSILON_SQ) stuckTicks++;
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
        if (closeAfter && openedThisRun && targetPos != null && openProperty != null)
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
        if (targetPos != null)
        {
            doorBlacklist.put(targetPos.immutable(), DOOR_BLACKLIST_TICKS);
        }
        globalCooldown = GLOBAL_COOLDOWN_TICKS;

        openedThisRun = false;
        targetPos = null;
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

    private void setTarget(BlockPos pos, BlockState state)
    {
        this.targetPos = pos.immutable();
        Direction facing = getFacing(state);
        // Aim TWO blocks beyond the doorway to avoid path "done" in the threshold
        this.passThroughPos = targetPos.relative(facing, 2).immutable();
    }

    private Direction getFacing(BlockState state)
    {
        if (isGate && state.getBlock() instanceof FenceGateBlock)
        {
            return state.getValue(FenceGateBlock.FACING);
        }
        if (state.getBlock() instanceof DoorBlock)
        {
            return state.getValue(DoorBlock.FACING);
        }
        return mob.getDirection(); // fallback
    }

    private void toggleOpen(boolean open)
    {
        if (targetPos == null || openProperty == null) return;

        Level level = mob.level();
        BlockState state = level.getBlockState(targetPos);
        if (!state.hasProperty(openProperty)) return; // block changed

        if (isGate && state.getBlock() instanceof FenceGateBlock)
        {
            boolean currentlyOpen = state.getValue(FenceGateBlock.OPEN);
            if (currentlyOpen != open)
            {
                level.setBlock(targetPos, state.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_CLIENTS);
            }
            return;
        }

        boolean currentlyOpen = state.getValue(openProperty);
        if (currentlyOpen != open)
        {
            level.setBlock(targetPos, state.setValue(openProperty, open), Block.UPDATE_CLIENTS);
        }
    }

    /** If this is a double door, also open the mate so we don’t shoulder-bump. */
    private void openPairedIfNeeded()
    {
        if (targetPos == null) return;
        Level level = mob.level();
        BlockState s = level.getBlockState(targetPos);

        if (!(s.getBlock() instanceof DoorBlock)) return;

        Direction facing = s.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = s.getValue(DoorBlock.HINGE);
        BlockPos matePos = targetPos.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
        BlockState mate = level.getBlockState(matePos);

        if (mate.getBlock() instanceof DoorBlock && mate.getValue(DoorBlock.FACING) == facing)
        {
            if (!mate.getValue(DoorBlock.OPEN))
            {
                level.setBlock(matePos, mate.setValue(DoorBlock.OPEN, true), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Is the door “ahead” of the mob along the door’s facing? (prevents re-trigger after passing) */
    private boolean isAheadOfDoorPlane(BlockPos door, Direction facing)
    {
        Vec3 doorCenter = Vec3.atCenterOf(door);
        Vec3 toMob = mob.position().subtract(doorCenter);
        double along = toMob.x * facing.getStepX() + toMob.z * facing.getStepZ();
        return along < FAR_PAST_PLANE_THRESHOLD;
    }

    /** How far the mob is along the door facing normal (positive = in front/past). */
    private double planeAlongAmount(BlockPos door, Direction facing)
    {
        Vec3 doorCenter = Vec3.atCenterOf(door);
        Vec3 toMob = mob.position().subtract(doorCenter);
        return toMob.x * facing.getStepX() + toMob.z * facing.getStepZ();
    }

    /** True once the mob is beyond the plane by a threshold. */
    private boolean hasClearedDoorPlane(double threshold)
    {
        if (targetPos == null) return false;
        BlockState state = mob.level().getBlockState(targetPos);
        Direction facing = getFacing(state);
        return planeAlongAmount(targetPos, facing) > threshold;
    }

    /** Consider “inside doorway” as within the 1x2 block column occupied by the door/gate (with a tiny inflation). */
    private boolean isInsideDoorwayAABB()
    {
        if (targetPos == null) return false;
        AABB doorway = new AABB(targetPos).inflate(0.05).expandTowards(0, 1, 0); // 1x2 column
        return doorway.inflate(0.05).contains(mob.position());
    }

    /** Door should be close to the current path or the mob; prevents engaging doors off-route. */
    private boolean isNearPathOrSelf(BlockPos door, Path path)
    {
        // Near mob?
        if (Vec3.atCenterOf(door).distanceToSqr(mob.position()) <= PATH_PROXIMITY_MAX_DIST_SQ) return true;

        // Near any of the next few nodes?
        int next = path.getNextNodeIndex();
        int maxCheck = Math.min(next + 3, path.getNodeCount() - 1);
        for (int i = next; i <= maxCheck; i++)
        {
            Node n = path.getNode(i);
            Vec3 np = new Vec3(n.x + 0.5, n.y + 0.5, n.z + 0.5);
            if (np.distanceToSqr(Vec3.atCenterOf(door)) <= PATH_PROXIMITY_MAX_DIST_SQ) return true;
        }
        return false;
    }
}
