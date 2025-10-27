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

    public void deserializeNBT(@NotNull HolderLookup.@NotNull Provider provider, CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        outpostInventory.clear();

        List<ItemStorage> possibleItems = new ArrayList<ItemStorage>();
        ListTag filterableList = compound.getList("possibleItemList", 10);

        for (int i = 0; i < filterableList.size(); ++i)
        {
            possibleItems.add(new ItemStorage(ItemStack.parseOptional(provider, filterableList.getCompound(i))));
        }

        this.outpostInventory.addAll(possibleItems);
    }

    public void serializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound)
    {
        super.serializeNBT(provider, compound);

        ListTag filteredItems = new ListTag();
        Iterator<ItemStorage> itemIterator = this.outpostInventory.iterator();

        while (itemIterator.hasNext())
        {
            ItemStorage item = (ItemStorage) itemIterator.next();
            filteredItems.add(item.getItemStack().saveOptional(provider));
        }

        compound.put("possibleItemList", filteredItems);
    }


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
                IBuilding outpostBuilding = colony.getBuildingManager().getBuilding(outpostSpot);

                if (outpostBuilding != null)
                {
                    IItemHandler itemHandler = null;
                    
                    // Safeguard against Minecolonies bug for buildings that don't include racks.
                    try
                    {
                        itemHandler = outpostBuilding.getItemHandlerCap();
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Failed to get outpost item handler capability for {}: {}", outpostBuilding.getBuildingDisplayName(), e);
                    }

                    if (itemHandler == null)
                    {
                        continue;
                    }

                    for (int i = 0; i < itemHandler.getSlots(); i++)
                    {
                        ItemStack stack = itemHandler.getStackInSlot(i);

                        if (!stack.isEmpty())
                        {
                            outpostInventory.add(new ItemStorage(stack.copy(), 1));
                        }
                    }
                }
            }
        }

        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Refreshing outpost export module. Items: {}", outpostInventory.size()));
    }
}
