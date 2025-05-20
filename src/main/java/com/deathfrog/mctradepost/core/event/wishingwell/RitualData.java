package com.deathfrog.mctradepost.core.event.wishingwell;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.minecolonies.api.util.NBTUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class RitualData implements INBTSerializable<CompoundTag> {
    private final Map<BlockPos, WishingWellHandler.RitualState> activeRituals = new HashMap<>();
    private final Set<BlockPos> knownWells = new HashSet<>();

    public Map<BlockPos, WishingWellHandler.RitualState> getActiveRituals() {
        return activeRituals;
    }

    public Set<BlockPos> getKnownWells() {
        return knownWells;
    }

    @Override
    public CompoundTag serializeNBT(@Nonnull HolderLookup.Provider provider)  {
        CompoundTag tag = new CompoundTag();

        ListTag ritualList = new ListTag();
        for (Map.Entry<BlockPos, WishingWellHandler.RitualState> entry : activeRituals.entrySet()) {
            CompoundTag ritualTag = new CompoundTag();
            ritualTag.put("Pos", NBTUtils.writeBlockPos(entry.getKey()));
            ritualTag.putInt("Coins", entry.getValue().coins);
            ritualTag.putLong("LastUsed", entry.getValue().lastUsed);
            ritualList.add(ritualTag);
        }
        tag.put("ActiveRituals", ritualList);

        ListTag wellList = new ListTag();
        for (BlockPos pos : knownWells) {
            wellList.add(NBTUtils.writeBlockPos(pos));
        }
        tag.put("KnownWells", wellList);

        return tag;
    }

    @Override
    public void deserializeNBT(@Nonnull HolderLookup.Provider provider, @Nonnull CompoundTag tag) {
        activeRituals.clear();
        knownWells.clear();

        ListTag ritualList = tag.getList("ActiveRituals", Tag.TAG_COMPOUND);
        for (Tag t : ritualList) {
            CompoundTag ritualTag = (CompoundTag) t;
            BlockPos pos = NBTUtils.readBlockPos(ritualTag.getCompound("Pos"));
            WishingWellHandler.RitualState state = new WishingWellHandler.RitualState();
            state.coins = ritualTag.getInt("Coins");
            state.lastUsed = ritualTag.getLong("LastUsed");
            activeRituals.put(pos, state);
        }

        ListTag wellList = tag.getList("KnownWells", Tag.TAG_INT_ARRAY);
        for (Tag t : wellList) {
            BlockPos pos = NBTUtils.readBlockPos((IntArrayTag) t);
            knownWells.add(pos);
        }
    }
}