package com.deathfrog.mctradepost.core.entity.pets.companion;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative persistent store for Companion Staff bindings and companion state. */
public final class CompanionRegistry extends SavedData
{
    private static final String DATA_NAME = MCTradePostMod.MODID + "_companions";
    private static final @Nonnull Factory<CompanionRegistry> FACTORY =
        new Factory<>(CompanionRegistry::new, CompanionRegistry::load);
    private final Map<UUID, CompanionRecord> records = new HashMap<>();

    /** Mutable persisted state for one bound staff. */
    public static final class CompanionRecord
    {
        public UUID bindingId;
        public UUID companionId;
        public int colonyId;
        public String shopDimension;
        public BlockPos shopPosition;
        public boolean active;
        public String activeDimension = "";
        public BlockPos activePosition = BlockPos.ZERO;
        public long cooldownUntil;
        public CompoundTag inventory = new CompoundTag();

        private CompanionRecord(UUID bindingId, UUID companionId, int colonyId, String shopDimension, BlockPos shopPosition)
        {
            this.bindingId = bindingId;
            this.companionId = companionId;
            this.colonyId = colonyId;
            this.shopDimension = shopDimension;
            this.shopPosition = shopPosition.immutable();
        }
    }

    /**
     * Returns the global companion registry stored with the server's Overworld.
     *
     * @param server active server
     * @return persistent companion registry
     */
    public static CompanionRegistry get(@Nonnull MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Creates and stores a new staff binding.
     *
     * @param colonyId bound colony ID
     * @param dimension bound shop dimension ID
     * @param shopPosition bound shop building ID
     * @return newly created companion record
     */
    public CompanionRecord create(int colonyId, @Nonnull String dimension, @Nonnull BlockPos shopPosition)
    {
        UUID bindingId = UUID.randomUUID();
        CompanionRecord record = new CompanionRecord(bindingId, UUID.randomUUID(), colonyId, dimension, shopPosition);
        records.put(bindingId, record);
        setDirty();
        return record;
    }

    /**
     * Looks up a companion binding.
     *
     * @param bindingId staff binding ID
     * @return matching record, when present
     */
    public Optional<CompanionRecord> find(@Nonnull UUID bindingId)
    {
        return Optional.ofNullable(records.get(bindingId));
    }

    /** Marks a mutated companion record for persistence. */
    public void changed()
    {
        setDirty();
    }

    /**
     * Permanently removes a companion record.
     *
     * @param bindingId staff binding ID
     * @return removed record, when present
     */
    public Optional<CompanionRecord> remove(@Nonnull UUID bindingId)
    {
        CompanionRecord removed = records.remove(bindingId);
        if (removed != null)
        {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    private static CompanionRegistry load(CompoundTag tag, HolderLookup.Provider provider)
    {
        CompanionRegistry registry = new CompanionRegistry();
        ListTag list = tag.getList("companions", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++)
        {
            CompoundTag entry = list.getCompound(index);
            CompanionRecord record = new CompanionRecord(entry.getUUID("binding"), entry.getUUID("companion"),
                entry.getInt("colony"), entry.getString("shop_dimension"), BlockPos.of(entry.getLong("shop_position")));
            record.active = entry.getBoolean("active");
            record.activeDimension = entry.getString("active_dimension");
            record.activePosition = BlockPos.of(entry.getLong("active_position"));
            record.cooldownUntil = entry.getLong("cooldown_until");
            record.inventory = entry.getCompound("inventory").copy();
            registry.records.put(record.bindingId, record);
        }
        return registry;
    }

    /**
     * Serializes every companion record.
     *
     * @param tag destination tag
     * @param provider registry lookup provider
     * @return populated destination tag
     */
    @SuppressWarnings("null")
    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider)
    {
        ListTag list = new ListTag();
        for (CompanionRecord record : records.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("binding", record.bindingId);
            entry.putUUID("companion", record.companionId);
            entry.putInt("colony", record.colonyId);
            entry.putString("shop_dimension", record.shopDimension);
            entry.putLong("shop_position", record.shopPosition.asLong());
            entry.putBoolean("active", record.active);
            entry.putString("active_dimension", record.activeDimension);
            entry.putLong("active_position", record.activePosition.asLong());
            entry.putLong("cooldown_until", record.cooldownUntil);
            entry.put("inventory", record.inventory.copy());
            list.add(entry);
        }
        tag.put("companions", list);
        return tag;
    }
}
