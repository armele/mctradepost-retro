package com.deathfrog.mctradepost.core.event.wishingwell.resurrection;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent, authoritative store of burials made available by the MineColonies burial event. */
public final class BuriedCitizenRegistry extends SavedData
{
    private static final String DATA_NAME = MCTradePostMod.MODID + "_buried_citizens";
    private static final @Nonnull Factory<BuriedCitizenRegistry> FACTORY =
        new Factory<>(BuriedCitizenRegistry::new, BuriedCitizenRegistry::load);

    public record Burial(UUID id, int colonyId, BlockPos gravePos, CompoundTag citizenNbt,
        String citizenName, String jobName, int burialDay) {}

    private final Map<UUID, Burial> burials = new HashMap<>();

    public static BuriedCitizenRegistry get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Burial add(int colonyId, BlockPos gravePos, CompoundTag citizenNbt, String citizenName,
        String jobName, int burialDay)
    {
        burials.values().removeIf(entry -> entry.colonyId() == colonyId && entry.gravePos().equals(gravePos));
        Burial burial = new Burial(UUID.randomUUID(), colonyId, gravePos.immutable(), citizenNbt.copy(),
            citizenName, jobName == null ? "" : jobName, burialDay);
        burials.put(burial.id(), burial);
        setDirty();
        return burial;
    }

    public Optional<Burial> find(BlockPos gravePos)
    {
        return burials.values().stream().filter(entry -> entry.gravePos().equals(gravePos)).findFirst();
    }

    public Optional<Burial> get(UUID id)
    {
        return Optional.ofNullable(burials.get(id));
    }

    public Optional<Burial> remove(UUID id)
    {
        Burial removed = burials.remove(id);
        if (removed != null) setDirty();
        return Optional.ofNullable(removed);
    }

    public void restore(Burial burial)
    {
        burials.put(burial.id(), burial);
        setDirty();
    }

    private static BuriedCitizenRegistry load(CompoundTag tag, HolderLookup.Provider provider)
    {
        BuriedCitizenRegistry registry = new BuriedCitizenRegistry();
        ListTag list = tag.getList("burials", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            Burial burial = new Burial(entry.getUUID("id"), entry.getInt("colony"),
                BlockPos.of(entry.getLong("pos")), entry.getCompound("citizen").copy(),
                entry.getString("name"), entry.getString("job"), entry.getInt("day"));
            registry.burials.put(burial.id(), burial);
        }
        return registry;
    }

    @SuppressWarnings("null")
    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider)
    {
        ListTag list = new ListTag();
        for (Burial burial : burials.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", burial.id());
            entry.putInt("colony", burial.colonyId());
            entry.putLong("pos", burial.gravePos().asLong());
            entry.put("citizen", burial.citizenNbt().copy());
            entry.putString("name", burial.citizenName());
            entry.putString("job", burial.jobName());
            entry.putInt("day", burial.burialDay());
            list.add(entry);
        }
        tag.put("burials", list);
        return tag;
    }
}
