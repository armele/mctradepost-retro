package com.deathfrog.mctradepost.api.entity.pets.goals;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETOTHERGOALS;

import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.ItemHandlerHelpers;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class HaulInventoryToWarehouseGoal<P extends Animal & ITradePostPet> extends Goal
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final double ARRIVAL_DIST_SQ = 3.0D * 3.0D;
    private static final int MAX_RUN_TICKS = 20 * 20;
    private static final int COOLDOWN_TICKS_ON_FAIL = 20 * 10;
    private static final int COOLDOWN_TICKS_ON_SUCCESS = 20 * 5;
    private static final int MAX_REPLANS = 4;
    private static final double SPEED = 1.1D;

    private enum Phase
    {
        PICKUP,
        DELIVER
    }

    private final P pet;
    private int cooldownTicks;
    private int ticksRunning;
    private int replanAttempts;
    private boolean done;
    private boolean failed;
    private Phase phase;
    private BlockPos targetPos;

    public HaulInventoryToWarehouseGoal(@Nonnull P pet)
    {
        this.pet = pet;
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Flag.MOVE)));
    }

    @Override
    public boolean canUse()
    {
        Level level = pet.level();
        if (level == null || level.isClientSide) return false;
        if (cooldownTicks-- > 0) return false;
        if (!PetRoles.HAULING.equals(pet.getPetData().roleFromWorkLocation(level))) return false;

        return hasDeliverablePetInventory() || hasSourceItemsToPickUp(level);
    }

    @Override
    public boolean canContinueToUse()
    {
        Level level = pet.level();
        if (level == null || level.isClientSide) return false;
        if (done || failed || ticksRunning > MAX_RUN_TICKS) return false;
        if (targetPos == null) return false;

        return phase != null;
    }

    @Override
    public void start()
    {
        done = false;
        failed = false;
        ticksRunning = 0;
        replanAttempts = 0;

        Level level = pet.level();
        if (level == null || level.isClientSide)
        {
            failed = true;
            return;
        }

        phase = hasDeliverablePetInventory() ? Phase.DELIVER : Phase.PICKUP;
        targetPos = getTargetForPhase(level, phase);

        if (targetPos == null)
        {
            failed = true;
            cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
            return;
        }

        moveTowards(targetPos);
    }

    @Override
    public void tick()
    {
        Level level = pet.level();
        if (level == null || level.isClientSide) return;

        ticksRunning++;

        if (targetPos == null || phase == null)
        {
            failed = true;
            cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
            return;
        }

        if (!isAtTarget())
        {
            if (!pet.getNavigation().isInProgress() && !replan())
            {
                failed = true;
                cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
            }
            return;
        }

        if (phase == Phase.PICKUP)
        {
            boolean pickedUp = pickUpFromHauler(level);
            if (!pickedUp && !hasDeliverablePetInventory())
            {
                done = true;
                cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
                return;
            }

            phase = Phase.DELIVER;
            targetPos = getTargetForPhase(level, phase);
            replanAttempts = 0;

            if (targetPos == null)
            {
                failed = true;
                cooldownTicks = COOLDOWN_TICKS_ON_FAIL;
                return;
            }

            moveTowards(targetPos);
            return;
        }

        boolean delivered = deliverToStorage(level);
        done = true;
        cooldownTicks = delivered ? COOLDOWN_TICKS_ON_SUCCESS : COOLDOWN_TICKS_ON_FAIL;
    }

    @Override
    public void stop()
    {
        pet.getNavigation().stop();
        phase = null;
        targetPos = null;
        ticksRunning = 0;
        replanAttempts = 0;
    }

    @Nullable
    private BlockPos getTargetForPhase(@Nonnull Level level, @Nonnull Phase nextPhase)
    {
        if (nextPhase == Phase.PICKUP)
        {
            BlockPos workPos = pet.getWorkLocation();
            return workPos == null || BlockPos.ZERO.equals(workPos) ? null : workPos;
        }

        IWareHouse warehouse = pet.getTrainerBuilding() == null ? null :
            pet.getTrainerBuilding().getColony().getServerBuildingManager().getClosestWarehouseInColony(pet.blockPosition());

        if (warehouse != null)
        {
            return warehouse.getPosition();
        }

        IBuilding trainerBuilding = pet.getTrainerBuilding();
        return trainerBuilding == null ? null : trainerBuilding.getPosition();
    }

    private boolean hasSourceItemsToPickUp(@Nonnull Level level)
    {
        BlockPos workPos = pet.getWorkLocation();
        if (workPos == null || BlockPos.ZERO.equals(workPos)) return false;

        Optional<IItemHandler> source = ItemHandlerHelpers.getHandler(level, workPos, null);
        if (source.isEmpty()) return false;

        IItemHandler sourceHandler = source.get();
        for (int slot = 0; slot < sourceHandler.getSlots(); slot++)
        {
            ItemStack stack = sourceHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) return true;
        }

        return false;
    }

    private boolean hasDeliverablePetInventory()
    {
        IItemHandler inventory = pet.getInventory();
        Item petFood = PetTypes.foodForPet(pet.getClass());
        int foodToKeep = petFood == null ? 0 : maxFoodToKeep(petFood);
        int foodSeen = 0;

        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (petFood == null || !stack.is(petFood)) return true;

            foodSeen += stack.getCount();
            if (foodSeen > foodToKeep) return true;
        }

        return false;
    }

    /**
     * Allows the pet to pick up inventory from the hauler work block.
     * 
     * @param level
     * @return
     */
    @SuppressWarnings("null")
    private boolean pickUpFromHauler(@Nonnull Level level)
    {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        BlockPos workPos = pet.getWorkLocation();
        if (workPos == null || BlockPos.ZERO.equals(workPos)) return false;

        Optional<IItemHandler> source = ItemHandlerHelpers.getHandler(serverLevel, workPos, null);
        if (source.isEmpty()) return false;

        IItemHandler sourceHandler = source.get();
        boolean changed = false;

        for (int slot = 0; slot < sourceHandler.getSlots(); slot++)
        {
            ItemStack simulatedExtract = sourceHandler.extractItem(slot, Integer.MAX_VALUE, true);
            if (simulatedExtract.isEmpty()) continue;

            ItemStack simulatedRemainder = ItemHandlerHelper.insertItemStacked(pet.getInventory(), simulatedExtract.copy(), true);
            int transferable = simulatedExtract.getCount() - simulatedRemainder.getCount();
            if (transferable <= 0) break;

            ItemStack extracted = sourceHandler.extractItem(slot, transferable, false);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(pet.getInventory(), extracted, false);
            if (!remainder.isEmpty())
            {
                ItemHandlerHelper.insertItemStacked(sourceHandler, remainder, false);
            }

            if (extracted.getCount() != remainder.getCount())
            {
                changed = true;
            }
        }

        if (changed)
        {
            markBlockChanged(serverLevel, workPos);
            TraceUtils.dynamicTrace(TRACE_PETOTHERGOALS, () -> LOGGER.info("Pet {} picked up items from hauler at {}.",
                pet.getUUID(), workPos.toShortString()));
        }

        return changed;
    }

    private boolean deliverToStorage(@Nonnull Level level)
    {
        if (!(level instanceof ServerLevel)) return false;

        IBuilding targetBuilding = getDeliveryBuilding();
        if (targetBuilding == null) return false;

        IItemHandler targetInventory = targetBuilding.getItemHandlerCap();
        if (targetInventory == null) return false;

        boolean changed = deliverNonFood(targetInventory);
        changed = deliverExcessPetFood(targetInventory) || changed;

        if (changed)
        {
            targetBuilding.markDirty();
            TraceUtils.dynamicTrace(TRACE_PETOTHERGOALS, () -> LOGGER.info("Pet {} delivered hauled items to {} at {}.",
                pet.getUUID(), targetBuilding.getClass().getSimpleName(), targetBuilding.getPosition().toShortString()));
        }

        return changed;
    }

    @Nullable
    private IBuilding getDeliveryBuilding()
    {
        IBuilding trainerBuilding = pet.getTrainerBuilding();
        if (trainerBuilding == null) return null;

        IWareHouse warehouse = trainerBuilding.getColony().getServerBuildingManager().getClosestWarehouseInColony(pet.blockPosition());
        if (warehouse instanceof IBuilding warehouseBuilding)
        {
            return warehouseBuilding;
        }

        return trainerBuilding;
    }

    /**
     * Delivers everything but one stack of food to the target inventory.
     * 
     * @param targetInventory
     * @return
     */
    @SuppressWarnings("null")
    private boolean deliverNonFood(@Nonnull IItemHandler targetInventory)
    {
        IItemHandler petInventory = pet.getInventory();
        Item petFood = PetTypes.foodForPet(pet.getClass());
        boolean changed = false;

        for (int slot = 0; slot < petInventory.getSlots(); slot++)
        {
            ItemStack stack = petInventory.getStackInSlot(slot);
            if (stack.isEmpty() || (petFood != null && stack.is(petFood))) continue;

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetInventory, stack.copy(), false);
            if (remainder.getCount() != stack.getCount())
            {
                pet.getInventory().setStackInSlot(slot, remainder);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * If there is more than one stack of pet food, deliver that extra amount to the target inventory.
     * 
     * @param targetInventory
     * @return
     */
    @SuppressWarnings("null")
    private boolean deliverExcessPetFood(@Nonnull IItemHandler targetInventory)
    {
        Item petFood = PetTypes.foodForPet(pet.getClass());
        if (petFood == null) return false;

        int firstFoodSlot = -1;
        int foodStackLimit = maxFoodToKeep(petFood);
        boolean changed = false;

        for (int slot = 0; slot < pet.getInventory().getSlots(); slot++)
        {
            ItemStack stack = pet.getInventory().getStackInSlot(slot);
            if (stack.isEmpty() || !stack.is(petFood)) continue;

            if (firstFoodSlot < 0)
            {
                firstFoodSlot = slot;
                if (stack.getCount() <= foodStackLimit)
                {
                    continue;
                }

                ItemStack excess = stack.copyWithCount(stack.getCount() - foodStackLimit);
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetInventory, excess, false);
                int delivered = excess.getCount() - remainder.getCount();
                if (delivered <= 0) continue;

                stack.setCount(foodStackLimit + remainder.getCount());
                pet.getInventory().setStackInSlot(slot, stack);
                changed = true;
                continue;
            }

            ItemStack keptFood = pet.getInventory().getStackInSlot(firstFoodSlot);
            int roomInKeptStack = Math.max(0, foodStackLimit - keptFood.getCount());
            if (roomInKeptStack > 0)
            {
                int movedToKeptStack = Math.min(roomInKeptStack, stack.getCount());
                keptFood.grow(movedToKeptStack);
                stack.shrink(movedToKeptStack);
                pet.getInventory().setStackInSlot(firstFoodSlot, keptFood);
                pet.getInventory().setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
                changed = true;
            }

            if (stack.isEmpty()) continue;

            ItemStack toDeliver = stack.copy();
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetInventory, toDeliver, false);
            int delivered = toDeliver.getCount() - remainder.getCount();
            if (delivered <= 0) continue;

            stack.shrink(delivered);
            pet.getInventory().setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            changed = true;
        }

        return changed;
    }

    private int maxFoodToKeep(@Nonnull Item petFood)
    {
        return petFood.getDefaultInstance().getMaxStackSize();
    }

    private boolean isAtTarget()
    {
        BlockPos localTarget = targetPos;
        if (localTarget == null) return false;

        Vec3 center = NullnessBridge.assumeNonnull(Vec3.atCenterOf(localTarget));
        return pet.position().distanceToSqr(center) <= ARRIVAL_DIST_SQ;
    }

    private void moveTowards(@Nonnull BlockPos pos)
    {
        pet.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, SPEED);
    }

    private boolean replan()
    {
        BlockPos localTarget = targetPos;
        if (localTarget == null || replanAttempts++ >= MAX_REPLANS) return false;

        moveTowards(localTarget);
        return pet.getNavigation().isInProgress();
    }

    private void markBlockChanged(@Nonnull ServerLevel level, @Nonnull BlockPos pos)
    {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null)
        {
            be.setChanged();
        }
    }
}
