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

/** Persistent per-dimension index of placed Mooring Bay blocks. */
public final class MooringBayRegistry extends SavedData
{
    private static final String DATA_NAME = MCTradePostMod.MODID + "_mooring_bays";
    private static final String TAG_BAYS = "bays";
    private final Set<BlockPos> bays = new HashSet<>();
    private static final @Nonnull Factory<MooringBayRegistry> FACTORY =
        new Factory<>(MooringBayRegistry::new, MooringBayRegistry::load);

    /**
     * Gets the registry belonging to a server dimension.
     *
     * @param level dimension whose registry is requested
     * @return persistent Mooring Bay registry
     */
    public static MooringBayRegistry get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Registers a placed Mooring Bay.
     *
     * @param pos block position to register
     */
    public void add(BlockPos pos)
    {
        if (bays.add(pos.immutable())) setDirty();
    }

    /**
     * Removes a Mooring Bay position.
     *
     * @param pos block position to remove
     */
    public void remove(BlockPos pos)
    {
        if (bays.remove(pos)) setDirty();
    }

    /**
     * Gets every registered position without permitting callers to mutate the registry.
     *
     * @return unmodifiable set of Mooring Bay positions
     */
    public Set<BlockPos> bays()
    {
        return Collections.unmodifiableSet(bays);
    }

    /**
     * Restores a registry from dimension saved data.
     *
     * @param tag serialized registry
     * @param provider registry lookup provider
     * @return restored registry
     */
    private static MooringBayRegistry load(CompoundTag tag, HolderLookup.Provider provider)
    {
        MooringBayRegistry data = new MooringBayRegistry();
        ListTag list = tag.getList(TAG_BAYS, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++)
        {
            data.bays.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }
        return data;
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider)
    {
        ListTag list = new ListTag();
        for (BlockPos pos : bays)
        {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put(TAG_BAYS, list);
        return tag;
    }
}
