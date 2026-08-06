package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent per-dimension index of placed trade docks. */
public final class TradeDockRegistry extends SavedData
{
    private static final String DATA_NAME = MCTradePostMod.MODID + "_trade_docks";
    private static final String TAG_DOCKS = "docks";
    private final Set<BlockPos> docks = new HashSet<>();

    private static final Factory<TradeDockRegistry> FACTORY = new Factory<>(TradeDockRegistry::new, TradeDockRegistry::load, null);

    public static TradeDockRegistry get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void add(BlockPos pos)
    {
        if (docks.add(pos.immutable())) setDirty();
    }

    public void remove(BlockPos pos)
    {
        if (docks.remove(pos)) setDirty();
    }

    public Set<BlockPos> docks()
    {
        return Collections.unmodifiableSet(docks);
    }

    private static TradeDockRegistry load(CompoundTag tag, HolderLookup.Provider provider)
    {
        TradeDockRegistry data = new TradeDockRegistry();
        ListTag list = tag.getList(TAG_DOCKS, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) data.docks.add(BlockPos.of(((net.minecraft.nbt.LongTag) list.get(i)).getAsLong()));
        return data;
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider)
    {
        ListTag list = new ListTag();
        for (BlockPos pos : docks) list.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        tag.put(TAG_DOCKS, list);
        return tag;
    }
}
