package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;

public class UnloadInventoryToWorkLocationGoal<P extends Animal & ITradePostPet> extends Goal
{
    private final P pet;
    private final float unloadThreshold; // e.g., 0.8 = 80%

    // --- anti-hang safeguards ---
    private int ticksRunning;
    private int stuckTicks;
    private Vec3 lastPos;
    private int replanAttempts;
    private int cooldownTicks;

    // Tunables
    private static final double ARRIVAL_DIST_SQ = 2.5 * 2.5; // within ~2.5 blocks
    private static final int MAX_RUN_TICKS = 20 * 12;        // 12s hard timeout
    private static final int STUCK_TICKS_LIMIT = 40;         // ~2s without progress
    private static final double PROGRESS_EPSILON_SQ = 0.05 * 0.05;
    private static final int MAX_REPLANS = 3;
    private static final int COOLDOWN_TICKS_ON_FAIL = 40;
    private static final boolean ALLOW_FALLBACK_UNLOAD_AT_RANGE = true; // set false if you want strict proximity
    private static final double FALLBACK_RANGE = 3.5;         // try unloading at ~3.5 blocks if LOS
    private static final double FALLBACK_RANGE_SQ = FALLBACK_RANGE * FALLBACK_RANGE;

    private boolean hasArrived;
    private BlockPos workPos;
    private boolean unloaded = false;
    private int noApproachTicks = 0;
    private double bestDistSq = Double.MAX_VALUE;

    private final Random rng = new Random();

    public UnloadInventoryToWorkLocationGoal(P pet, float unloadThreshold)
    {
        this.pet = pet;
        this.unloadThreshold = unloadThreshold;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * Determines if the goal can be used.
     * 
     * This goal can be used if the cooldown has expired, the pet is not on the client side, the pet has an inventory, the pet has a work location, the inventory is non-empty, and the fraction of full slots in the inventory is >= the unload threshold.
     * 
     * @return true if the goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        if (pet.getNavigation().isInProgress()) return false; // don’t steal MOVE slot

        if (cooldownTicks > 0)
        {
            cooldownTicks--;
            return false;
        }

        if (pet.level().isClientSide) return false;
        if (pet.getInventory() == null) return false;

        BlockPos wp = pet.getWorkLocation();
        if (wp == null) return false;

        int fullSlots = 0;
        int totalSlots = pet.getInventory().getSlots();
        for (int i = 0; i < totalSlots; i++)
        {
            if (!pet.getInventory().getStackInSlot(i).isEmpty())
            {
                fullSlots++;
            }
        }
        return totalSlots > 0 && ((float) fullSlots / totalSlots) >= unloadThreshold;
    }

    /**
     * Determines if the goal can continue to be used.
     * 
     * This goal can continue to run if the pet has not unloaded its inventory, the timeout has not been exceeded, and either the pet is still navigating to the work position or the pet is close enough to try unloading.
     * 
     * @return true if the goal can continue to run, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        // Continue while not unloaded, not timed out, and either navigating or close enough to try.
        if (unloaded) return false;
        if (ticksRunning > MAX_RUN_TICKS) return false;
        if (workPos == null) return false;

        boolean navInProgress = pet.getNavigation().isInProgress();
        boolean closeEnough = pet.position().distanceToSqr(Vec3.atCenterOf(workPos)) <= ARRIVAL_DIST_SQ;

        return navInProgress || closeEnough;
    }

    /**
     * Starts the goal by setting the target work position, and moving towards it.
     * Also resets the state variables like unloaded, hasArrived, ticksRunning, stuckTicks, replanAttempts, and lastPos.
     */
    @Override
    public void start()
    {
        this.workPos = pet.getWorkLocation();
        this.hasArrived = false;
        this.unloaded = false;
        this.ticksRunning = 0;
        this.stuckTicks = 0;
        this.replanAttempts = 0;
        this.lastPos = pet.position();

        if (workPos != null)
        {
            moveTowards(workPos, 1.1);
        }
    }

    /**
     * Ticks the unload goal.
     * 
     * <p>This method is called every tick that the goal is active. It checks if the pet has unloaded its inventory, if the timeout has not been exceeded, and if either the pet is still navigating to the work position or the pet is close enough to try unloading.</p>
     * 
     * <p>If the pet has unloaded, stops the goal. If the timeout has been exceeded, attempts to unload at the current position or aborts. If the pet is close enough, attempts to unload at the work position. If the pet is stuck, attempts to replan to the work position. If replanning fails, attempts to unload at the current position or aborts.</p>
     * 
     * @see #canContinueToUse()
     * @see #start()
     * @see #stop()
     */
    @Override
    public void tick()
    {
        ticksRunning++;

        // Detect progress
        Vec3 now = pet.position();
        if (now.distanceToSqr(lastPos) <= PROGRESS_EPSILON_SQ) stuckTicks++;
        else
        {
            stuckTicks = 0;
            lastPos = now;
        }

        // Arrived (or close enough)
        if (workPos != null)
        {
            double d2 = now.distanceToSqr(Vec3.atCenterOf(workPos));
            if (d2 <= ARRIVAL_DIST_SQ)
            {
                hasArrived = true;
                tryUnloadInto(workPos);
                return; // stop tick early if we unloaded (stop() will reset)
            }
        }

        // If navigation finished but we’re still not close, try replanning locally
        if (!pet.getNavigation().isInProgress() && !hasArrived && workPos != null && !unloaded)
        {
            if (!replanToWorkPos()) // if we couldn't replan, consider fallback
            {
                tryFallbackUnloadOrAbort();
            }
            return;
        }

        if (workPos != null)
        {
            double dNow = pet.position().distanceToSqr(Vec3.atCenterOf(workPos));
            // track best distance seen this run (initialize to +INF in start())
            if (dNow + 1e-6 < bestDistSq)
            {
                bestDistSq = dNow;
                noApproachTicks = 0;
            }
            else
            {
                noApproachTicks++;
            }
            if (noApproachTicks > 40)
            {   
                // ~2s not getting closer
                if (!replanToWorkPos()) tryFallbackUnloadOrAbort();
                return;
            }
        }

        // Stuck detection: no progress for a while -> replan or fallback
        if (stuckTicks > STUCK_TICKS_LIMIT && workPos != null && !unloaded)
        {
            if (!replanToWorkPos())
            {
                tryFallbackUnloadOrAbort();
            }
        }

        // Hard timeout safety
        if (ticksRunning > MAX_RUN_TICKS && !unloaded)
        {
            tryFallbackUnloadOrAbort();
        }
    }

    /**
     * Resets the goal state and stops the navigation of the pet.
     * 
     * <p>This method is called when the goal is stopped. It resets all internal state and stops the navigation of the pet.</p>
     * 
     * @see net.minecraft.world.entity.ai.goal.Goal#stop()
     */
    @Override
    public void stop()
    {
        hasArrived = false;
        workPos = null;
        unloaded = false;
        ticksRunning = 0;
        stuckTicks = 0;
        replanAttempts = 0;
        pet.getNavigation().stop();
    }

    // -----------------------
    // Helpers
    // -----------------------

    private void moveTowards(BlockPos pos, double speed)
    {
        pet.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    /**
     * Attempts to replan the path to the work position.
     * 
     * <p>This method is called when the pet is stuck and the goal is not yet satisfied. It will try to replan the path to the work location
     * by first trying to path to the center of the block, and if that fails, tries to path to the 8 surrounding blocks in a random order.
     * If all of those attempts fail, the goal is considered failed and the pet should stop navigating to the work location.</p>
     * 
     * @return true if the replan was successful, false otherwise
     */
    private boolean replanToWorkPos()
    {
        if (workPos == null) return false;
        if (replanAttempts >= MAX_REPLANS) return false;

        replanAttempts++;

        // Try center first
        if (tryPlanTo(workPos)) return true;

        // Try nudges around (cardinals + diagonals)
        BlockPos[] candidates = {workPos.north(),
            workPos.south(),
            workPos.east(),
            workPos.west(),
            workPos.north().east(),
            workPos.north().west(),
            workPos.south().east(),
            workPos.south().west()};

        // Shuffle a bit to avoid deterministic bad ordering
        for (int i = candidates.length - 1; i > 0; i--)
        {
            int j = rng.nextInt(i + 1);
            BlockPos tmp = candidates[i];
            candidates[i] = candidates[j];
            candidates[j] = tmp;
        }

        for (BlockPos c : candidates)
        {
            if (tryPlanTo(c))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Attempts to replan the path to the given target position.
     * 
     * <p>This method is called when the pet is stuck and the goal is not yet satisfied. It will try to replan the path to the target position
     * by trying to path to the center of the block. If the replan fails, the goal is considered failed and the pet should stop navigating to the work location.</p>
     * 
     * @param target the target position to replan to
     * @return true if the replan was successful, false otherwise
     */
    private boolean tryPlanTo(BlockPos target)
    {
        // If we’re already pretty close to this candidate, don’t re-path needlessly.
        if (pet.position().distanceToSqr(Vec3.atCenterOf(target)) < 1.0) return true;

        boolean ok = pet.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.1);
        return ok && pet.getNavigation().isInProgress();
    }

    /**
     * Tries to unload the pet's inventory into the work position, but as a fallback, if the pet is within a small radius and has line of sight
     * to the work position, it will unload without standing exactly on the block. If this fallback is not possible, it will abort the unload
     * process with a cooldown to avoid thrashing.
     */
    private void tryFallbackUnloadOrAbort()
    {
        if (ALLOW_FALLBACK_UNLOAD_AT_RANGE && workPos != null)
        {
            // If we’re within a small radius and have LOS, allow unloading without standing exactly on the block.
            if (pet.position().distanceToSqr(Vec3.atCenterOf(workPos)) <= FALLBACK_RANGE_SQ && hasLineOfSightToBlock(workPos))
            {
                tryUnloadInto(workPos);
                return;
            }
        }
        // Could not replan and fallback not possible -> abort safely with cooldown to avoid thrash.
        cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
        // stop() clears state + stops navigation
        stop();
    }

    /**
     * Returns true if the pet has line of sight to the given block position, and false otherwise.
     * 
     * <p>This method is used to determine if the pet can unload its inventory into the work position without standing exactly on the block. If the
     * pet is within a small radius and has line of sight to the work position, it will unload without standing exactly on the block. Otherwise, it
     * will abort the unload process with a cooldown to avoid thrashing.</p>
     * 
     * @param pos the block position to check line of sight to
     * @return true if the pet has line of sight to the block, false otherwise
     */
    private boolean hasLineOfSightToBlock(BlockPos pos) {
        if (pos == null) return false;
        if (!pet.level().isLoaded(pos)) return false;

        Vec3 start = pet.getEyePosition();
        Vec3 end   = Vec3.atCenterOf(pos);

        // Colliders = respect solid collision shapes; ignore fluids
        ClipContext ctx = new ClipContext(
            start,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            pet
        );

        BlockHitResult hit = pet.level().clip(ctx);

        // True if nothing blocked us OR the first block we hit is exactly the target pos
        return hit.getType() == HitResult.Type.MISS || pos.equals(hit.getBlockPos());
    }


    /**
     * Attempts to unload the entire inventory of the pet into the given block position.
     *
     * <p>This method is used by the unload goal to actually transfer items from the pet's inventory to the work position. It does so by attempting
     * to merge items into the container at the target position, and if no merge is possible, attempting to place them into an empty slot. Any items
     * that cannot be unloaded are left in the pet's inventory. If any items were unloaded, the container is marked as changed and the unload goal
     * terminates with success.</p>
     *
     * @param pos the block position of the container to unload into
     */
    private void tryUnloadInto(BlockPos pos)
    {
        if (!(pet.level() instanceof ServerLevel serverLevel)) return;

        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (!(be instanceof Container container))
        {
            // No container anymore; abort (but don’t hang).
            cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
            stop();
            return;
        }

        boolean changed = false;

        for (int i = 0; i < pet.getInventory().getSlots(); i++)
        {
            ItemStack stack = pet.getInventory().getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // Insert into any slots we can: first try merges, then empties.
            stack = tryMergeIntoContainer(stack, container);
            if (!stack.isEmpty())
            {
                stack = tryPlaceIntoEmptySlot(stack, container);
            }

            // Write back remaining / emptied stack
            pet.getInventory().setStackInSlot(i, stack);
            if (!changed && !stack.isEmpty())
            { /* still items left; may try again later */ }
            changed = true;
        }

        if (changed)
        {
            be.setChanged();
        }
        else
        {
            cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
            stop();
            return;
        }

        // Mark success and terminate
        this.unloaded = true;
        stop();
    }

    /**
     * Attempts to merge the given item stack into the given container by inserting it into any slots that are not full and that contain the same item.
     * 
     * <p>This method is used by the unload goal to attempt to merge items from the pet's inventory into the container at the work position. If the given
     * item stack is empty, this method will return an empty stack. Otherwise, it will attempt to merge the given item stack into the container by iterating
     * over all slots in the container and checking if the item stack can be merged into each slot. If the item stack can be merged into a slot, the
     * item stack is shrunk by the number of items successfully merged, and the method continues to the next slot. If the item stack cannot be merged into
     * any slot, the method returns the item stack unchanged.</p>
     * 
     * @param stack the item stack to attempt to merge into the container
     * @param container the container to attempt to merge the item stack into
     * @return the remaining item stack after attempting to merge it into the container
     */
    private ItemStack tryMergeIntoContainer(ItemStack stack, Container container)
    {
        for (int j = 0; j < container.getContainerSize() && !stack.isEmpty(); j++)
        {
            ItemStack target = container.getItem(j);
            if (target.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(target, stack)) continue;

            int space = Math.min(target.getMaxStackSize(), container.getMaxStackSize()) - target.getCount();
            if (space <= 0) continue;

            int toTransfer = Math.min(space, stack.getCount());
            if (toTransfer > 0)
            {
                target.grow(toTransfer);
                stack.shrink(toTransfer);
            }
        }
        return stack;
    }

    /**
     * Attempts to place the given item stack into any empty slots in the given container.
     * 
     * <p>This method is used by the unload goal to attempt to place items from the pet's inventory into the container at the work position. If the given
     * item stack is empty, this method will return an empty stack. Otherwise, it will iterate over all slots in the container and attempt to place the
     * given item stack into the first empty slot it finds. If the item stack can be placed into the slot, the item stack is shrunk by the number of items
     * successfully placed, and the method continues to the next slot. If the item stack cannot be placed into any slot, the method returns the item stack
     * unchanged.</p>
     * 
     * @param stack the item stack to attempt to place into the container
     * @param container the container to attempt to place the item stack into
     * @return the remaining item stack after attempting to place it into the container
     */
    private ItemStack tryPlaceIntoEmptySlot(ItemStack stack, Container container)
    {
        for (int j = 0; j < container.getContainerSize() && !stack.isEmpty(); j++)
        {
            ItemStack target = container.getItem(j);
            if (!target.isEmpty()) continue;

            int max = Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
            int insert = Math.min(max, stack.getCount());

            ItemStack placed = stack.copy();
            placed.setCount(insert);
            container.setItem(j, placed);
            stack.shrink(insert);
        }
        return stack;
    }
}
