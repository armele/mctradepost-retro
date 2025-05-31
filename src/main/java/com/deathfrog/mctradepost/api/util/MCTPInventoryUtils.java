package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public class MCTPInventoryUtils {
    public static int findRandomSlotInItemHandlerWith(@NotNull IItemHandler itemHandler, @NotNull Predicate<ItemStack> itemStackSelectionPredicate) {
    List<Integer> matchingSlots = new ArrayList<>();

    for (int slot = 0; slot < itemHandler.getSlots(); ++slot) {
        if (itemStackSelectionPredicate.test(itemHandler.getStackInSlot(slot))) {
            matchingSlots.add(slot);
        }
    }

    if (matchingSlots.isEmpty()) {
        return -1;
    }

    return matchingSlots.get(ThreadLocalRandom.current().nextInt(matchingSlots.size()));
}

}
