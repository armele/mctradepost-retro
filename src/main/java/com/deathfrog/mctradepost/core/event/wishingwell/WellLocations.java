package com.deathfrog.mctradepost.core.event.wishingwell;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler.RitualState;
import com.minecolonies.api.util.NBTUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class WellLocations implements INBTSerializable<CompoundTag> {
    private final Map<BlockPos, WishingWellHandler.RitualState> activeRituals = new HashMap<>();
    private final Set<BlockPos> knownWells = new HashSet<>();

    public Map<BlockPos, WishingWellHandler.RitualState> getActiveRituals() {
        return activeRituals;
    }

    public Set<BlockPos> getKnownWells() {
        return knownWells;
    }

    @Override
    public CompoundTag serializeNBT(@Nonnull HolderLookup.Provider provider)
    {
        CompoundTag tag = new CompoundTag();

        ListTag ritualList = new ListTag();
        for (Map.Entry<BlockPos, RitualState> entry : activeRituals.entrySet())
        {
            BlockPos pos = entry.getKey();
            RitualState state = entry.getValue();

            // Recompute counts from current pools (handles null lists too)
            int baseCount = state.entityCount(state.baseCoins);
            int goldCount = state.entityCount(state.goldCoins);
            int diamondCount = state.entityCount(state.diamondCoins);

            CompoundTag ritualTag = new CompoundTag();
            ritualTag.put("Pos", NBTUtils.writeBlockPos(pos));
            ritualTag.putInt("BaseCoinCount", baseCount);
            ritualTag.putInt("GoldCoinCount", goldCount);
            ritualTag.putInt("DiamondCoinCount", diamondCount);
            ritualTag.putInt("CompanionCount", state.companionCount);
            ritualTag.putLong("LastUsed", state.lastUsed);
            ritualList.add(ritualTag);
        }
        tag.put("ActiveRituals", ritualList);

        ListTag wellList = new ListTag();
        for (BlockPos pos : knownWells)
        {
            wellList.add(NBTUtils.writeBlockPos(pos));
        }
        tag.put("KnownWells", wellList);

        return tag;
    }

    @Override
    public void deserializeNBT(@Nonnull HolderLookup.Provider provider, @Nonnull CompoundTag tag)
    {
        activeRituals.clear();
        knownWells.clear();

        ListTag ritualList = tag.getList("ActiveRituals", Tag.TAG_COMPOUND);
        for (Tag t : ritualList)
        {
            CompoundTag ritualTag = (CompoundTag) t;
            BlockPos pos = NBTUtils.readBlockPos(ritualTag.getCompound("Pos"));

            RitualState state = new RitualState();
            // Store the saved counts so requirement checks don’t lie between load and first rebuild
            int savedBase = ritualTag.getInt("BaseCoinCount");
            int savedGold = ritualTag.getInt("GoldCoinCount");
            int savedDiamond = ritualTag.getInt("DiamondCoinCount");

            // Make lists null for now; we’ll rebuild them from the world on first tick/usage.
            state.baseCoins = null;
            state.goldCoins = null;
            state.diamondCoins = null;

            state.companionCount = ritualTag.getInt("CompanionCount");
            state.lastUsed = ritualTag.getLong("LastUsed");

            // Optionally store the saved counts in transient fields if you want
            // requirement checks to pass before we rebuild from the world:
            state.cachedBaseCount = savedBase;
            state.cachedGoldCount = savedGold;
            state.cachedDiamondCount = savedDiamond;

            activeRituals.put(pos, state);
        }

        ListTag wellList = tag.getList("KnownWells", Tag.TAG_INT_ARRAY);
        for (Tag t : wellList)
        {
            BlockPos pos = NBTUtils.readBlockPos((IntArrayTag) t);
            knownWells.add(pos);
        }
    }

}