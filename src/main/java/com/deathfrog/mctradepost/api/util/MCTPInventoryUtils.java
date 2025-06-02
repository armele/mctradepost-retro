package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.tileentities.TileEntityRack;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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

    /**
     * Returns a map of all items stored in the building, including its inventory and all racks, with their count.
     * 
     * @param building the building view to query
     * @return a map of item stacks to their count
     */
    public static Object2IntMap<ItemStorage> contentsForBuilding(IBuildingView building) {
        final Set<BlockPos> containerList = new HashSet<>(building.getContainerList());

        final Object2IntOpenHashMap<ItemStorage> storedItems = new Object2IntOpenHashMap<>();
        storedItems.defaultReturnValue(0); // avoid nulls on get()

        final Level world = building.getColony().getWorld();
        containerList.add(building.getPosition());

        for (final BlockPos blockPos : containerList)
        {
            final BlockEntity rack = world.getBlockEntity(blockPos);
            if (rack instanceof TileEntityRack)
            {
                final Map<ItemStorage, Integer> rackStorage = ((TileEntityRack) rack).getAllContent();

                for (final Map.Entry<ItemStorage, Integer> entry : rackStorage.entrySet())
                {
                    storedItems.addTo(entry.getKey(), entry.getValue().intValue());
                }
            }
        }

        return storedItems;
    }

}
