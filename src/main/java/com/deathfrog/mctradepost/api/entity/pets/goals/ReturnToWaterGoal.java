package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETGOALS;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.PathingUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

public class ReturnToWaterGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private final P pet;
    private final int airThresholdTicks;
    private final int searchRadius;
    private final double speed;
    private final int maxRunTicks;

    // anti-hang / progress tracking
    private int ticksRunning;
    private int stuckTicks;
    private static final int STUCK_TICKS_LIMIT = 40; // ~2s @20tps
    private static final double PROGRESS_EPSILON_SQ = 0.05 * 0.05; // ~0.05 block
    private Vec3 lastPos = Vec3.ZERO;

    // target & path
    private BlockPos targetWaterPos;

    // blacklist failed targets for a short time window (ticks)
    private final Map<BlockPos, Integer> blacklist = new HashMap<>();
    private static final int BLACKLIST_TICKS = 20 * 10; // 10 seconds

    // cooldown between failed attempts
    private int cooldownTicks;
    private static final int COOLDOWN_TICKS_ON_FAIL = 40;

    public ReturnToWaterGoal(final P pet, final int airThresholdSeconds, final int searchRadius, final double speed, final int maxRunTicks)
    {
        this.pet = pet;
        this.airThresholdTicks = airThresholdSeconds * 20;
        this.searchRadius = Math.max(8, searchRadius);
        this.speed = speed;
        this.maxRunTicks = Math.max(60, maxRunTicks);

        // This goal moves, looks, and may jump
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP)));
    }

    /**
     * Determines if the return to water goal can be used.
     * 
     * This goal can be used if no cooldown is active, the pet is not a passenger, the pet is not sleeping, the pet is not already in water or a bubble, and the pet is either running low on air or not on the ground.
     * The goal will also be used if there is a loaded work location that is water, or if there is a reachable water block within the search radius.
     * 
     * @return true if the goal can be used, false otherwise.
     */
    @Override
    public boolean canUse()
    {

        final Level level = pet.level();
        if (level.isClientSide) return false;

        if (cooldownTicks > 0)
        {
            cooldownTicks--;
            return false;
        }
        if (pet.isPassenger() || pet.isSleeping()) return false;

        // If already safe, skip
        if (pet.isInWaterOrBubble()) return false;

        // Trigger when running low on air OR simply out of water (defensive)
        if (pet.getAirSupply() > airThresholdTicks && pet.onGround())
        {
            return false;
        }

        final BlockPos work = pet.getWorkLocation();
        if (work != null && level.isLoaded(work) && PathingUtil.isWater(level, work))
        {
            targetWaterPos = work;
            return true;
        }

        targetWaterPos = findNearestReachableWater(pet.blockPosition(), this.searchRadius);
        return targetWaterPos != null;
    }

    /**
     * Called when the goal is activated. This goal will start the pet navigating to the nearest water block.
     * If the pet is not on land, it will try to find a water block above or below it first.
     * If the pet is on land, it will try to find a water block beside it first, then above or below it.
     * If no path can be found to a water block, the goal will fail and the pet will be stopped.
     */
    @Override
    public void start()
    {

        final Level level = pet.level();
        if (level.isClientSide) return;

        ticksRunning = 0;
        stuckTicks = 0;

        if (!pet.isAlive()) return;

        lastPos = pet.position();

        final BlockPos target = targetWaterPos;
        if (target == null)
        {
            // No target, nothing to do.
            return;
        }

        final boolean onLand = !pet.isInWaterOrBubble();

        // Ordered candidate list:
        // on land: banks -> above -> water
        // in water: water -> above -> banks
        final List<BlockPos> candidates = new ArrayList<>();

        if (onLand)
        {
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                final BlockPos bank = target.relative(NullnessBridge.assumeNonnull(dir));
                if (PathingUtil.isStandableBank(level, bank))
                {
                    candidates.add(bank.immutable());
                }
            }

            final BlockPos above = PathingUtil.findTopOfWaterColumn(level, target);
            if (above != null) // if your helper can return null, keep this; otherwise you can drop the check
            {
                candidates.add(above.immutable());
            }

            if (PathingUtil.isSwimmableWater(level, target))
            {
                candidates.add(target.immutable());
            }
        }
        else
        {
            if (PathingUtil.isSwimmableWater(level, target))
            {
                candidates.add(target.immutable());
            }

            final BlockPos above = target.above();
            if (level.isLoaded(NullnessBridge.assumeNonnull(above)) && 
                (level.getBlockState(NullnessBridge.assumeNonnull(above)).isAir() || level.getBlockState(NullnessBridge.assumeNonnull(above)).getFluidState().isSource()))
            {
                candidates.add(above.immutable());
            }

            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                final BlockPos bank = target.relative(NullnessBridge.assumeNonnull(dir));
                if (PathingUtil.isStandableBank(level, bank))
                {
                    candidates.add(bank.immutable());
                }
            }
        }

        // Try candidates
        for (final BlockPos cand : candidates)
        {
            if (!level.isLoaded(NullnessBridge.assumeNonnull(cand))) continue;

            // 1) implicit moveTo
            final boolean accepted = pet.getNavigation().moveTo(
                cand.getX() + 0.5, cand.getY() + 0.5, cand.getZ() + 0.5, speed
            );
            if (accepted && pet.getNavigation().isInProgress())
            {
                return;
            }

            // 2) explicit path build (accuracy 1–2)
            Path p = pet.getNavigation().createPath(cand, 1);
            if (p == null)
            {
                p = pet.getNavigation().createPath(cand, 2);
            }
            if (p != null)
            {
                pet.getNavigation().moveTo(p, speed);
                return;
            }
        }

        // No path: treat as failure deterministically (avoid start/stop loops)
        TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.debug("ReturnToWater: no path to {}, blacklisting + cooldown.", target));
        blacklistTarget(target);
        cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
        targetWaterPos = null;
        pet.getNavigation().stop();
    }

    /**
     * Determines if the goal can continue to run.
     * This goal can continue to run if the pet has a valid target position, the navigation is not done, and the pet has not
     * arrived at the water location yet.
     * Additionally, if the pet has been running for too long, or if the pet is already in water or a bubble, this goal will
     * not continue to run.
     * @return true if the goal can continue to run, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        final BlockPos target = targetWaterPos;
        if (target == null) return false;

        if (ticksRunning > maxRunTicks) return false; // hard timeout
        if (pet.isInWaterOrBubble()) return false;

        final Level level = pet.level();
        if (!level.isLoaded(target)) return false;                 // prevent unloaded-chunk weirdness
        if (!PathingUtil.isWater(level, target)) return false;

        return !pet.getNavigation().isDone();
    }

    /**
     * Ticks the return to water goal.
     * <p>This method is called every tick that the goal is active. It checks if the target position is null, if the pet is stuck, if the pet is in water or a bubble, if the pet has finished navigating to the target, and if a local reseek is necessary.</p>
     * <p>If the pet is stuck, it attempts to replan towards the target position. If replanning fails, it blacklists the target position and stops the goal.</p>
     * <p>If the pet has finished navigating to the target but is not in water yet, it attempts to find a nearby reachable water position and replans towards it.</p>
     * <p>If the pet is in water or a bubble, it stops the goal.</p>
     */
    @Override
    public void tick()
    {
        final Level level = pet.level();
        if (level.isClientSide) return;

        ticksRunning++;

        final BlockPos target = targetWaterPos;
        if (target == null)
        {
            // Defensive: avoid NPEs if target cleared by scheduler/other state changes.
            stop();
            return;
        }

        // Progress / stuck detection
        final Vec3 now = pet.position();
        if (now.distanceToSqr(NullnessBridge.assumeNonnull(lastPos)) <= PROGRESS_EPSILON_SQ)
        {
            stuckTicks++;
        }
        else
        {
            stuckTicks = 0;
            lastPos = now;
        }

        // If path is finished but we’re not in water yet, try a small local reseek
        if (pet.getNavigation().isDone() && !pet.isInWaterOrBubble())
        {
            final BlockPos nearby = findNearestReachableWater(pet.blockPosition(), 6);
            if (nearby != null && !nearby.equals(target))
            {
                targetWaterPos = nearby;
                replanTowards(nearby);
            }
        }

        if (stuckTicks > STUCK_TICKS_LIMIT)
        {
            if (replanTowards(target))
            {
                stuckTicks = 0;
            }
            else
            {
                blacklistTarget(target);
                cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
                stop();
            }
        }
    }

    /**
     * Stops the return to water goal and resets its state.
     * <p>This method is called when the goal is stopped, either because the target has been reached or because the goal was cancelled.</p>
     * <p>It stops the pet's navigation, resets the target position, the number of ticks running, and the number of stuck ticks. It also decays the blacklist timers.</p>
     */
    @Override
    public void stop()
    {
        pet.getNavigation().stop();
        targetWaterPos = null;
        ticksRunning = 0;
        stuckTicks = 0;

        // Decay blacklist timers
        if (!blacklist.isEmpty())
        {
            blacklist.replaceAll((pos, t) -> t - 5);
            blacklist.entrySet().removeIf(e -> e.getValue() <= 0);
        }
    }

    /**
     * Returns true, indicating that this goal can be interrupted by other goals.
     * <p>This goal can be interrupted by other goals, so it will be stopped if another goal is activated.</p>
     */
    @Override
    public boolean isInterruptable()
    {
        return true;
    }

    // -----------------------
    // Helpers
    // -----------------------

    /**
     * Blacklists the given position for a certain amount of ticks to prevent repeated navigation attempts.
     * 
     * @param pos the position to blacklist
     */
    private void blacklistTarget(final BlockPos pos)
    {
        if (pos == null) return;
        blacklist.put(pos.immutable(), BLACKLIST_TICKS);
    }

    /**
     * Determines if the given position is blacklisted.
     * Blacklisting is used to prevent the pet from attempting to navigate to the same position multiple times in a row.
     * If a position is blacklisted, the pet will not attempt to navigate to that position until the blacklist timer has expired.
     * 
     * @param pos the position to check
     * @return true if the position is blacklisted, false otherwise
     */
    private boolean isBlacklisted(final BlockPos pos)
    {
        final Integer left = blacklist.get(pos);
        return left != null && left > 0;
    }

    /**
     * Attempts to replan the path to the given target position.
     * <p>This method is called when the pet is stuck and the goal is not yet satisfied. It will try to replan the path to the target
     * position by using the pet's navigation system to compute a new path. If the replan is successful, it starts navigating to the
     * target position. If the replan fails, the goal is considered failed and the pet should stop navigating to the target
     * position.</p>
     * 
     * @param to the target position to replan towards
     * @return true if the replan was successful, false otherwise
     */
    private boolean replanTowards(final BlockPos to)
    {
        if (to == null) return false;

        // Using the overload with coordinates; adjust "accuracy" if you have a better one available.
        final Path path = pet.getNavigation().createPath(
            to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, 0
        );
        if (path != null)
        {
            pet.getNavigation().moveTo(path, speed);
            return true;
        }
        return false;
    }

    /**
     * Finds a nearby water position that is both water and pathfindable. Uses a bounded expanding shell scan.
     */
    private BlockPos findNearestReachableWater(final BlockPos origin, final int radius)
    {
        final Level level = pet.level();

        BlockPos best = null;
        double bestDist2 = Double.MAX_VALUE;

        for (int r = 1; r <= radius; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dy = -2; dy <= 2; dy++)
                {
                    for (int dz = -r; dz <= r; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // outer shell only

                        final @Nonnull BlockPos p = NullnessBridge.assumeNonnull(origin.offset(dx, dy, dz));
                        if (!level.isLoaded(p)) continue;
                        if (!PathingUtil.isWater(level, p)) continue;
                        if (isBlacklisted(p)) continue;

                        if (!level.getBlockState(p).isPathfindable(PathComputationType.WATER)) continue;

                        final BlockPos head = p.above();
                        final boolean headIsWater = level.getBlockState(NullnessBridge.assumeNonnull(head)).getFluidState().is(NullnessBridge.assumeNonnull(FluidTags.WATER));
                        final boolean headIsAir = level.getBlockState(NullnessBridge.assumeNonnull(head)).isAir();
                        if (!headIsWater && !headIsAir) continue;

                        final double d2 = p.distSqr(origin);
                        if (d2 < bestDist2)
                        {
                            best = p.immutable();
                            bestDist2 = d2;
                        }
                    }
                }
            }

            if (best != null && bestDist2 <= 9.0) // within 3 blocks
            {
                return best;
            }
        }

        return best;
    }
}
