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
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent per-dimension index of Trade Interchange blocks. */
public final class TradeInterchangeRegistry extends SavedData
{
    private static final String DATA_NAME = MCTradePostMod.MODID + "_trade_interchanges";
    private static final String TAG_INTERCHANGES = "interchanges";
    private final Set<BlockPos> interchanges = new HashSet<>();
    private static final @Nonnull Factory<TradeInterchangeRegistry> FACTORY =
        new Factory<>(TradeInterchangeRegistry::new, TradeInterchangeRegistry::load);

    public static TradeInterchangeRegistry get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void add(BlockPos pos)
    {
        if (interchanges.add(pos.immutable())) setDirty();
    }

    public void remove(BlockPos pos)
    {
        if (interchanges.remove(pos)) setDirty();
    }

    public Set<BlockPos> interchanges()
    {
        return Collections.unmodifiableSet(interchanges);
    }

    private static TradeInterchangeRegistry load(CompoundTag tag, HolderLookup.Provider provider)
    {
        TradeInterchangeRegistry data = new TradeInterchangeRegistry();
        ListTag list = tag.getList(TAG_INTERCHANGES, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) data.interchanges.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        return data;
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider)
    {
        ListTag list = new ListTag();
        for (BlockPos pos : interchanges) list.add(LongTag.valueOf(pos.asLong()));
        tag.put(TAG_INTERCHANGES, list);
        return tag;
    }
}
