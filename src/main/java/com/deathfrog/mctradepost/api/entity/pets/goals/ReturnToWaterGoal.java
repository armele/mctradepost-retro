package com.deathfrog.mctradepost.api.entity.pets.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.util.PathingUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.mojang.logging.LogUtils;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETGOALS;

public class ReturnToWaterGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private final P pet;
    private final Level level;
    private final int airThresholdTicks;
    private final int searchRadius;
    private final double speed;
    private final int maxRunTicks;

    // anti-hang / progress tracking
    private int ticksRunning;
    private int stuckTicks;
    private static final int STUCK_TICKS_LIMIT = 40; // ~2s @20tps
    private static final double PROGRESS_EPSILON_SQ = 0.05 * 0.05; // ~0.05 block
    private Vec3 lastPos;

    // target & path
    private BlockPos targetWaterPos;

    // blacklist failed targets for a short time window (ticks)
    private final Map<BlockPos, Integer> blacklist = new HashMap<>();
    private static final int BLACKLIST_TICKS = 20 * 10; // 10 seconds

    // cooldown between failed attempts
    private int cooldownTicks;
    private static final int COOLDOWN_TICKS_ON_FAIL = 40;

    /**
     * @param pet                 the actor
     * @param airThresholdSeconds trigger when remaining air is below this (seconds)
     * @param searchRadius        how far to look for water
     * @param speed               path speed
     * @param maxRunTicks         hard timeout to avoid hangs
     */
    public ReturnToWaterGoal(P pet, int airThresholdSeconds, int searchRadius, double speed, int maxRunTicks)
    {
        this.pet = pet;
        this.level = pet.level();
        this.airThresholdTicks = airThresholdSeconds * 20;
        this.searchRadius = Math.max(8, searchRadius);
        this.speed = speed;
        this.maxRunTicks = Math.max(60, maxRunTicks);

        // This goal moves, looks, and may jump
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse()
    {
        if (cooldownTicks > 0)
        {
            cooldownTicks--;
            return false;
        }
        if (pet.isPassenger() || pet.isSleeping()) return false;

        // If already safe, skip
        if (isInWaterOrBubble(pet)) return false;

        // Trigger when running low on air OR simply out of water (defensive)
        if (pet.getAirSupply() > airThresholdTicks && pet.onGround())
        {
            return false;
        }

        if ((pet.getWorkLocation() != null) && PathingUtil.isWater(pet.level(), pet.getWorkLocation()))
        {
            targetWaterPos = pet.getWorkLocation();
            return true;
        }

        // Find a candidate water target
        targetWaterPos = findNearestReachableWater(pet.blockPosition(), searchRadius);
        return targetWaterPos != null;
    }

    @Override
    public void start()
    {
        ticksRunning = 0;
        stuckTicks = 0;
        lastPos = pet.position();

        if (targetWaterPos == null) return;

        final boolean onLand = !pet.isInWaterOrBubble();
        final Level level = pet.level();

        // Build a short, ordered candidate list:
        // - If on land, try standable "bank" squares first (adjacent to water),
        // then air above water, then the water cell itself.
        // - If already in water, try the water cell first (then above).
        List<BlockPos> candidates = new ArrayList<>();

        if (onLand)
        {
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                BlockPos bank = targetWaterPos.relative(dir);
                if (PathingUtil.isStandableBank(level, bank))
                {
                    candidates.add(bank.immutable());
                }
            }
            // Step onto the air above water (walk to edge, drop in)
            BlockPos above = PathingUtil.findTopOfWaterColumn(pet.level(), targetWaterPos);
            candidates.add(above.immutable());

            // Finally, the water itself (may still work sometimes from land)
            if (PathingUtil.isSwimmableWater(level, targetWaterPos))
            {
                candidates.add(targetWaterPos.immutable());
            }
        }
        else
        {
            // In water: go straight for the water cell (or above, if you want to surface entry)
            if (PathingUtil.isSwimmableWater(level, targetWaterPos))
            {
                candidates.add(targetWaterPos.immutable());
            }
            BlockPos above = targetWaterPos.above();
            if (level.getBlockState(above).isAir() || level.getBlockState(above).getFluidState().isSource())
            {
                candidates.add(above.immutable());
            }
            // A nearby bank can also be acceptable if we need to exit-reenter
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                BlockPos bank = targetWaterPos.relative(dir);
                if (PathingUtil.isStandableBank(level, bank))
                {
                    candidates.add(bank.immutable());
                }
            }
        }

        // Try each candidate with a couple of strategies before we give up.
        for (BlockPos cand : candidates)
        {
            if (!level.isLoaded(cand)) continue;

            // 1) Let nav build a path implicitly (often more permissive than prebuilding)
            boolean accepted = pet.getNavigation().moveTo(cand.getX() + 0.5, cand.getY() + 0.5, cand.getZ() + 0.5, speed);
            if (accepted && pet.getNavigation().isInProgress())
            {
                return;
            }

            // 2) Explicit path build with BlockPos overload; accuracy 1–2 is safer than 0
            Path p1 = pet.getNavigation().createPath(cand, 1);
            if (p1 == null)
            {
                p1 = pet.getNavigation().createPath(cand, 2);
            }
            if (p1 != null)
            {
                pet.getNavigation().moveTo(p1, speed);
                return;
            }
        }

        // As a last tiny nudge: try the air above water with a slightly wider tolerance
        BlockPos A = targetWaterPos.above();
        if (level.isLoaded(A) && level.getBlockState(A).isAir())
        {
            Path p2 = pet.getNavigation().createPath(A, 2);
            if (p2 != null)
            {
                pet.getNavigation().moveTo(p2, speed);
                return;
            }
        }

        // Couldn’t secure a path right now. Do NOT immediately blacklist:
        // give the finder a beat (chunks update, small position change) then retry.
        // Your tick() can call replan; here we just back off briefly.
        TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.debug("ReturnToWater: defer abort; no path to {}, retrying soon.", targetWaterPos));
        
        this.cooldownTicks = Math.max(this.cooldownTicks, 10); // ~0.5 s pause before next canUse()
    }

    /**
     * Determines if the goal should continue to run. This method will return true if the following conditions are met: - the target
     * water position is not null - the goal has not been running for more than the maximum number of ticks - the pet is not already in
     * water or a bubble - the target water position is still valid water - the pet's navigation is not done
     * 
     * @return true if the goal should continue to run, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        if (targetWaterPos == null) return false;
        if (ticksRunning > maxRunTicks) return false; // hard timeout

        // If we’ve reached water or are safe now, done
        if (isInWaterOrBubble(pet)) return false;

        // Target still valid water?
        if (!PathingUtil.isWater(pet.level(), targetWaterPos)) return false;

        // Path still valid or navigation still running?
        return !pet.getNavigation().isDone();
    }

    /**
     * Executes the tick logic for this goal. This function is called once per tick for the AI to perform its actions. It checks for
     * progress toward the target water position, and if stuck, will try to replan once toward the same target. If replanning fails, it
     * will give up on this target and put it on the blacklist. If the path is finished but the pet is not yet in water, it will
     * perform a small local reseek to find a new target if possible.
     */
    @Override
    public void tick()
    {
        ticksRunning++;

        // Progress / stuck detection
        Vec3 now = pet.position();
        if (now.distanceToSqr(lastPos) <= PROGRESS_EPSILON_SQ)
        {
            stuckTicks++;
        }
        else
        {
            stuckTicks = 0;
            lastPos = now;
        }

        // If path is finished but we’re not in water yet (e.g., nav says done but short), try a final step
        if (pet.getNavigation().isDone() && !isInWaterOrBubble(pet))
        {
            // small local reseek
            BlockPos nearby = findNearestReachableWater(pet.blockPosition(), 6);
            if (nearby != null && !nearby.equals(targetWaterPos))
            {
                targetWaterPos = nearby;
                replanTowards(nearby);
            }
        }
        
        if (stuckTicks > STUCK_TICKS_LIMIT)
        {
            // Try to replan once toward same target
            if (replanTowards(targetWaterPos))
            {
                stuckTicks = 0;
            }
            else
            {
                // Give up on this target
                blacklistTarget(targetWaterPos);
                stop(); // will trigger cleanup; canUse() will be re-evaluated next tick
                cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
            }
        }
    }

    @Override
    public void stop()
    {
        // Clean termination
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

    @Override
    public boolean isInterruptable()
    {
        // Allow higher-priority (e.g., panic) to preempt
        return true;
    }

    // -----------------------
    // Helpers
    // -----------------------

    private boolean isInWaterOrBubble(LivingEntity e)
    {
        return e.isInWaterOrBubble();
    }

    private void blacklistTarget(BlockPos pos)
    {
        if (pos == null) return;
        blacklist.put(pos.immutable(), BLACKLIST_TICKS);
    }

    private boolean isBlacklisted(BlockPos pos)
    {
        Integer left = blacklist.get(pos);
        return left != null && left > 0;
    }

    /**
     * Replans the path to the given target position, if possible, and starts navigating to it.
     * 
     * @param to the target position to replan towards
     * @return true if the replan attempt was successful and the pet is now navigating to the target, false otherwise
     */
    private boolean replanTowards(BlockPos to)
    {
        if (to == null) return false;
        Path path = pet.getNavigation().createPath(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, 0);
        if (path != null)
        {
            pet.getNavigation().moveTo(path, speed);
            return true;
        }
        return false;
    }

    /**
     * Finds a nearby water position that is both water and pathfindable. Uses a bounded expanding cube scan (cheap, no
     * allocation-heavy BFS).
     */
    private BlockPos findNearestReachableWater(BlockPos origin, int radius)
    {
        // Fast path: if standing next to water, choose that
        BlockPos best = null;
        double bestDist2 = Double.MAX_VALUE;

        // Scan outward; prefer positions the pathfinder considers valid for water travel
        for (int r = 1; r <= radius; r++)
        {
            // shell scan at manhattan distance r (simple cube but skip inner layers for speed)
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dy = -2; dy <= 2; dy++)
                { // vertical search is limited; pets don’t need huge Y swings
                    for (int dz = -r; dz <= r; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // outer shell only

                        BlockPos p = origin.offset(dx, dy, dz);
                        if (!level.isLoaded(p)) continue;
                        if (!PathingUtil.isWater(pet.level(), p)) continue;
                        if (isBlacklisted(p)) continue;

                        // Ensure the nav system thinks it can be used for water pathing
                        // and there’s at least some space to occupy (no solid collision above).
                        if (!level.getBlockState(p).isPathfindable(PathComputationType.WATER)) continue;

                        // Optional: ensure headroom in water (pet can fit)
                        BlockPos head = p.above();
                        if (!level.getBlockState(head).getFluidState().is(FluidTags.WATER) && !level.getBlockState(head).isAir())
                        {
                            continue;
                        }

                        double d2 = p.distSqr(origin);
                        if (d2 < bestDist2)
                        {
                            best = p.immutable();
                            bestDist2 = d2;
                        }
                    }
                }
            }

            // Early return if we found a very close one to reduce expensive path attempts
            if (best != null && bestDist2 <= 9.0)
            { // within 3 blocks
                return best;
            }
        }

        return best; // may be null
    }
}
