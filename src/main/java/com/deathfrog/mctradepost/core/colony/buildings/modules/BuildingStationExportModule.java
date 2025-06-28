package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.NbtTagConstants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;


public class BuildingStationExportModule extends AbstractBuildingModule implements IPersistentModule
{
    public record ExportData(BlockPos station, ItemStorage itemStorage, int cost)
    {
    }

    /**
     * The import tag name.
     */
    private static final String TAG_EXPORTS = "exports";

    /**
     * The cost tag name.
     */
    private static final String TAG_COST = "cost";

    /**
     * The list of exports configured.
     */
    protected final List<ExportData> exportList = new ArrayList<>();

    /**
     * Deserializes the NBT data for the trade list, restoring its state from the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        exportList.clear();
        final ListTag exportListTag = compound.getList(TAG_EXPORTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < exportListTag.size(); i++)
        {
            final CompoundTag compoundNBT = exportListTag.getCompound(i);
            BlockPos station = BlockPosUtil.read(compoundNBT, "station");
            int colonyId = compoundNBT.getInt("colonyId");
            ItemStorage itemStorage = new ItemStorage(ItemStack.parseOptional(provider, compoundNBT.getCompound(NbtTagConstants.STACK)));
            int quantity = compoundNBT.getInt(TAG_COST);
            exportList.add(new ExportData(station, itemStorage, quantity));
        }
    }

    /**
     * Serializes the NBT data for the trade list, storing its state in the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        @NotNull final ListTag exportListTag = new ListTag();
        for (ExportData exportData : exportList)
        {
            final CompoundTag compoundNBT = new CompoundTag();
            BlockPosUtil.write(compoundNBT, "station", exportData.station);
            compoundNBT.put(NbtTagConstants.STACK, exportData.itemStorage.getItemStack().saveOptional(provider));
            compoundNBT.putInt(TAG_COST, exportData.cost);
            exportListTag.add(compoundNBT);
        }
        compound.put(TAG_EXPORTS, exportListTag);
    }

    /**
     * Serializes the trade list to the given RegistryFriendlyByteBuf for
     * transmission to the client. 
     *
     * @param buf the buffer to serialize the trade list to.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(exportList.size());
        for (ExportData exportData : exportList)
        {
            buf.writeInt(exportData.station.getX());
            buf.writeInt(exportData.station.getY());
            buf.writeInt(exportData.station.getZ());
            Utils.serializeCodecMess(buf, exportData.itemStorage.getItemStack());
            buf.writeInt(exportData.cost);
        }
    }
}