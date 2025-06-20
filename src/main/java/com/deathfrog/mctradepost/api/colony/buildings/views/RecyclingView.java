package com.deathfrog.mctradepost.api.colony.buildings.views;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkRecyclingEngineer;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;


/*
 * ✅ Best Practice
* If a field should:
* Be visible on the client UI → put it in serializeNBT() in the building and deserializeNBT() both in the building and deserialize here in the view.
* 
*/

@OnlyIn(Dist.CLIENT)
public class RecyclingView extends AbstractBuildingView {
    protected Set<ItemStorage> allItems = new java.util.HashSet<>();

    public RecyclingView(final IColonyView colony, final BlockPos location) {
        super(colony, location);
    }

    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf) {
        super.deserialize(buf);

        CompoundTag compound = buf.readNbt();

        this.allItems = deserializeAllowableItems(buf.registryAccess(), compound);
    }

    /**
     * Deserializes the allowable items from the given NBT compound tag and updates the building's state.
     * This method clears the current list of allowable items and repopulates it by reading from the
     * compound tag specified. The allowable items are stored under the key specified by 
     * EntityAIWorkRecyclingEngineer#RECYCLING_LIST, and each item is represented as a CompoundTag 
     * within a ListTag. The items are added to the allItems map with a default value of 0.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of allowable items.
     */
    public Set<ItemStorage> deserializeAllowableItems(HolderLookup.Provider provider, CompoundTag tag) {
        Object2IntOpenHashMap<ItemStorage> allowedItems = new Object2IntOpenHashMap<>();

        if (tag != null && tag.contains(EntityAIWorkRecyclingEngineer.RECYCLING_LIST)) {
            ListTag outputTag = tag.getList(EntityAIWorkRecyclingEngineer.RECYCLING_LIST, Tag.TAG_COMPOUND);

            for (int i = 0; i < outputTag.size(); i++) {
                CompoundTag itemTag = outputTag.getCompound(i);
                ItemStack stack = ItemStack.parseOptional(provider, itemTag.getCompound("stack"));
                int count = itemTag.getInt("count");

                if (!stack.isEmpty()) {
                    allowedItems.addTo(new ItemStorage(stack), count);
                }
            }
        }

        return allowedItems.keySet();
    }


    /**
     * Retrieves a set of ItemStorage objects representing all items that are stored in this building's warehouse.
     * @return a set of ItemStorage objects representing all items that are stored in this building's warehouse.
     */
    public Set<ItemStorage> getRecyclableItems()
    {
        return allItems;
    }
}
