package com.deathfrog.mctradepost.api.colony.buildings.modules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import com.minecolonies.api.util.Utils;

public class RecyclingItemListModule extends ItemListModule
{
    /*
     * A list of things that were requested from the warehouse
     * and have not yet been processed.
     */
    private ArrayList<ItemStorage> pendingRecyclingQueue = new ArrayList<>();

    public RecyclingItemListModule(String id)
    {
        super(id);
    }

    /**
     * Returns a list of items that were requested from the warehouse and have not yet been processed.
     * 
     * @return The list of items to be processed.
     */
    public List<ItemStorage> getPendingRecyclingQueue()
    {
        return pendingRecyclingQueue;
    }

    /**
     * Deserializes the pending recycling queue from NBT.
     *
     * @param provider The provider to use for looking up holders.
     * @param compound The compound tag containing the serialized state.
     */
    public void deserializeNBT(@NotNull HolderLookup.@NotNull Provider provider, CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        pendingRecyclingQueue.clear();
        ListTag filterableList = compound.getList("pendingList", 10);

        for (int i = 0; i < filterableList.size(); ++i)
        {
            pendingRecyclingQueue.add(new ItemStorage(ItemStack.parseOptional(provider, filterableList.getCompound(i))));
        }
    }

    /**
     * Serializes the pending recycling queue to NBT.
     *
     * @param provider The provider to use for looking up holders.
     * @param compound The compound tag to which the state should be serialized.
     */
    public void serializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound)
    {
        super.serializeNBT(provider, compound);

        ListTag filteredItems = new ListTag();
        Iterator<ItemStorage> iterator = this.pendingRecyclingQueue.iterator();

        while (iterator.hasNext())
        {
            ItemStorage item = (ItemStorage) iterator.next();
            filteredItems.add(item.getItemStack().saveOptional(provider));
        }

        compound.put("pendingList", filteredItems);
    }

    /**
     * Serializes the pending recycling queue to the given buffer. 
     * The buffer is used to transfer data to the client, where each 
     * item in the queue is serialized using a utility method.
     *
     * @param buf The buffer to serialize the state of the pending 
     *            recycling queue into.
     */
    public void serializeToView(@NotNull RegistryFriendlyByteBuf buf)
    {
        super.serializeToView(buf);
        buf.writeInt(this.pendingRecyclingQueue.size());
        Iterator<ItemStorage> iterator = this.pendingRecyclingQueue.iterator();

        while (iterator.hasNext())
        {
            ItemStorage item = (ItemStorage) iterator.next();
            Utils.serializeCodecMess(buf, item.getItemStack());
        }
    }
}
