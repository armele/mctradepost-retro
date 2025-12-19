package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.mojang.logging.LogUtils;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;

public class OutpostExportModule extends ItemListModule implements ITickingModule
{
    public static final Logger LOGGER = LogUtils.getLogger();
    final public static String ID = "outpost_exports";
    protected Set<ItemStorage> outpostInventory = new HashSet<ItemStorage>();

    final public static String OUTPOST_EXPORT_WINDOW_DESC = "com.mctradepost.core.gui.modules.outpost.exports";

    public static final int INVENTORY_COOLDOWN = 60;
    public static int inventoryCooldown = 0;

    public OutpostExportModule()
    {
        super(ID);
    }


    /**
     * Deserializes the outpost export list from the provided NBT compound.
     * The compound should contain a list of items, each represented as a compound
     * tag with an item stack inside. The outpost export list is cleared and then
     * populated with the deserialized items.
     *
     * @param provider the holder lookup provider for item and block references.
     * @param compound the NBT compound containing the serialized outpost export list.
     */
    public void deserializeNBT(@NotNull HolderLookup.@NotNull Provider provider, CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        outpostInventory.clear();

        List<ItemStorage> possibleItems = new ArrayList<ItemStorage>();
        ListTag filterableList = compound.getList("possibleItemList", 10);

        for (int i = 0; i < filterableList.size(); ++i)
        {
            CompoundTag itemTag = filterableList.getCompound(i);

            if (itemTag.isEmpty())
            {
                continue;
            }
            
            possibleItems.add(new ItemStorage(ItemStack.parseOptional(NullnessBridge.assumeNonnull(provider), itemTag)));
        }

        this.outpostInventory.addAll(possibleItems);
    }

    /**
     * Serializes the outpost export list into an NBT compound.
     * The compound will contain a list of items, each represented as a compound
     * tag with an item stack inside.
     *
     * @param provider the holder lookup provider for item and block references
     * @param compound the NBT compound to which the outpost export list should be serialized
     */
    public void serializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound)
    {
        super.serializeNBT(provider, compound);

        ListTag filteredItems = new ListTag();
        Iterator<ItemStorage> itemIterator = this.outpostInventory.iterator();

        while (itemIterator.hasNext())
        {
            ItemStorage item = (ItemStorage) itemIterator.next();
            filteredItems.add(item.getItemStack().saveOptional(NullnessBridge.assumeNonnull(provider)));
        }

        compound.put("possibleItemList", filteredItems);
    }



    /**
     * Serializes the outpost export list to the given RegistryFriendlyByteBuf for
     * transmission to the client.
     *
     * @param buf the buffer to serialize the outpost export list to
     */
    public void serializeToView(@NotNull RegistryFriendlyByteBuf buf)
    {
        super.serializeToView(buf);
        buf.writeInt(this.outpostInventory.size());
        Iterator<ItemStorage> itemIterator = this.outpostInventory.iterator();

        while (itemIterator.hasNext())
        {
            ItemStorage item = (ItemStorage) itemIterator.next();
            Utils.serializeCodecMess(buf, item.getItemStack());
        }
    }

    /**
     * Called every tick that the colony updates.
     * This method is responsible for checking the outpost's member buildings for
     * items to add to the outpost's export list. If an item is added to the
     * export list, the module is marked as dirty and the export list is
     * refreshed in the GUI.
     * 
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(@NotNull IColony colony) 
    { 

        if (inventoryCooldown > 0)
        {
            inventoryCooldown--;
            return;
        }
    
        inventoryCooldown = INVENTORY_COOLDOWN;

        if (building instanceof BuildingOutpost outpost)
        {
            for (BlockPos outpostSpot : outpost.getWorkBuildings())
            {
                IBuilding outpostMemberBuilding = colony.getBuildingManager().getBuilding(outpostSpot);

                if (outpostMemberBuilding != null)
                {
                    IItemHandler itemHandler = null;
                    
                    if (outpost.getOutpostLevel() > 0 && outpostMemberBuilding.getBuildingLevel() > 0)
                    {
                        // Safeguard against Minecolonies bug for buildings that don't include racks.
                        try
                        {
                            itemHandler = outpostMemberBuilding.getItemHandlerCap();
                        }
                        catch (Exception e)
                        {
                            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.error("Failed to get outpost item handler capability for {}: {}", outpostMemberBuilding.getBuildingDisplayName(), e));
                        }
                    }
                    
                    if (itemHandler == null)
                    {
                        continue;
                    }

                    boolean didAdd = false;

                    for (int i = 0; i < itemHandler.getSlots(); i++)
                    {
                        ItemStack stack = itemHandler.getStackInSlot(i);

                        if (!stack.isEmpty())
                        {
                            didAdd = didAdd || outpostInventory.add(new ItemStorage(stack.copy(), 1, true, true));
                        }
                    }

                    if (didAdd)
                    {
                        this.markDirty();
                    }
                }
            }
        }

        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Refreshing outpost export module. Items: {}", outpostInventory.size()));
    }
}
