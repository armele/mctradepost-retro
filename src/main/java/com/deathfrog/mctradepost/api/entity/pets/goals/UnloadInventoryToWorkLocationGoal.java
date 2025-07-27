package com.deathfrog.mctradepost.api.entity.pets.goals;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;

public class UnloadInventoryToWorkLocationGoal<P extends Animal & ITradePostPet> extends Goal
{
    private final P pet;
    private final float unloadThreshold; // e.g., 0.8 = 80%
    private boolean hasArrived;
    private BlockPos workPos;

    public UnloadInventoryToWorkLocationGoal(P pet, float unloadThreshold)
    {
        this.pet = pet;
        this.unloadThreshold = unloadThreshold;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (pet.getInventory() == null || pet.getWorkLocation() == null || pet.level().isClientSide)
        {
            return false;
        }

        int fullSlots = 0;
        int totalSlots = pet.getInventory().getSlots();

        for (int i = 0; i < totalSlots; i++)
        {
            if (!pet.getInventory().getStackInSlot(i).isEmpty())
            {
                fullSlots++;
            }
        }

        return ((float) fullSlots / totalSlots) >= unloadThreshold;
    }

    @Override
    public boolean canContinueToUse()
    {
        return !hasArrived && pet.getNavigation().isInProgress();
    }

    @Override
    public void start()
    {
        this.workPos = pet.getWorkLocation();
        this.hasArrived = false;

        if (workPos != null)
        {
            pet.getNavigation().moveTo(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public void tick() {
        if (workPos != null && pet.blockPosition().closerToCenterThan(Vec3.atCenterOf(workPos), 1.5)) {
            hasArrived = true;

            if (pet.level() instanceof ServerLevel serverLevel) {
                BlockEntity be = serverLevel.getBlockEntity(workPos);
                if (be instanceof Container container) {
                    for (int i = 0; i < pet.getInventory().getSlots(); i++) {
                        ItemStack stack = pet.getInventory().getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            // Try inserting this entire stack into the container
                            for (int j = 0; j < container.getContainerSize() && !stack.isEmpty(); j++) {
                                ItemStack targetStack = container.getItem(j);

                                if (targetStack.isEmpty()) {
                                    int insertCount = Math.min(stack.getCount(), container.getMaxStackSize());
                                    ItemStack inserted = stack.copy();
                                    inserted.setCount(insertCount);
                                    container.setItem(j, inserted);
                                    stack.shrink(insertCount);

                                    if (stack.isEmpty()) {
                                        pet.getInventory().setStackInSlot(i, ItemStack.EMPTY);
                                    }
                                    break;
                                } else if (ItemStack.isSameItemSameComponents(targetStack, stack) &&
                                        targetStack.getCount() < targetStack.getMaxStackSize()) {
                                    int space = targetStack.getMaxStackSize() - targetStack.getCount();
                                    int toTransfer = Math.min(stack.getCount(), space);
                                    targetStack.grow(toTransfer);
                                    stack.shrink(toTransfer);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void stop()
    {
        hasArrived = false;
        workPos = null;
    }
}
