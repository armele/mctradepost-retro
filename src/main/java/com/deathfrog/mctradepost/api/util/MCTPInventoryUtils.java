package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.tileentities.TileEntityRack;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.entity.player.Player;

import static com.ldtteam.structurize.items.ModItems.buildTool;

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
     * @param building the building to query
     * @return a map of item stacks to their count
     */
    public static Object2IntMap<ItemStorage> contentsForBuilding(IBuilding building) {
        final Object2IntOpenHashMap<ItemStorage> storedItems = new Object2IntOpenHashMap<>();
        if (building == null) {
            return storedItems;
        }

        final Set<BlockPos> containerList = new HashSet<>(building.getContainers());
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

    /**
     * This implementation is COPIED from the private method of com.minecolonies.api.crafting.GenericRecipe
     * 
     * Calculates the secondary outputs of a recipe, e.g. the items left over after crafting.
     * @param recipe the recipe to calculate the secondary outputs for
     * @param world the world to use for the calculation, or null if not applicable
     * @return a list of the secondary outputs, or an empty list if no secondary outputs are possible
     */
    @NotNull
    public static List<ItemStack> calculateSecondaryOutputs(@NotNull final Recipe<?> recipe,
                                                             @Nonnull final Level world)
    {
        if (recipe instanceof final CraftingRecipe craftingRecipe)
        {
            final List<Ingredient> inputs = recipe.getIngredients();
            final CraftingContainer inv = new TransientCraftingContainer(new AbstractContainerMenu(MenuType.CRAFTING, 0)
            {
                @Override
                public @Nonnull ItemStack quickMoveStack(final @Nonnull Player player, final int slot)
                {
                    return ItemStack.EMPTY;
                }

                @Override
                public boolean stillValid(@Nonnull final Player playerIn)
                {
                    return false;
                }

            }, 3, 3);
            for (int slot = 0; slot < inputs.size(); ++slot)
            {
                final ItemStack[] stacks = inputs.get(slot).getItems();
                if (stacks.length > 0)
                {
                    inv.setItem(slot, stacks[0].copy());
                }
            }
            if (craftingRecipe.matches(inv.asCraftInput(), world))
            {
                return craftingRecipe.getRemainingItems(inv.asCraftInput()).stream()
                        .filter(ItemStackUtils::isNotEmpty)
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

}
