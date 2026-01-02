package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_RAREFINDS;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketOffer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.util.Utils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ThriftShopOffersModule extends AbstractBuildingModule implements IPersistentModule 
{
    public static final Logger LOGGER = LogUtils.getLogger();

    // ---------------- NBT keys ----------------
    private static final String TAG_OFFERS = "offers";
    private static final String TAG_LAST_ROLL = "lastRoll";

    private static final String TAG_OFFER_STACK = "stack";
    private static final String TAG_OFFER_PRICE = "price";
    private static final String TAG_OFFER_TIER = "tier"; // store enum name or ordinal

    List<MarketOffer> offers = new ArrayList<>();
    long lastRollDay = 0L;

    public ThriftShopOffersModule() 
    {
        // Constructor implementation
    }


    /**
     * Deserializes the NBT data for the module, restoring its state from the provided CompoundTag.
     * Clears the current display shelf contents and repopulates it using the data from the NBT.
     * Synchronizes the display frames in the world with the deserialized shelf contents.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the building.
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        // If no tag exists, keep current state (MineColonies pattern)
        if (compound == null || provider == null)
        {
            return;
        }

        if (compound.contains(TAG_LAST_ROLL, Tag.TAG_LONG))
        {
            lastRollDay = compound.getLong(TAG_LAST_ROLL);
        }

        // offers
        if (!compound.contains(TAG_OFFERS, Tag.TAG_LIST))
        {
            // Nothing persisted; leave offers as-is (or clear if you prefer)
            return;
        }

        final ListTag list = compound.getList(TAG_OFFERS, Tag.TAG_COMPOUND);

        final List<MarketOffer> loaded = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag offerTag = list.getCompound(i);

            // tier
            MarketTier tier = MarketTier.TIER1_COMMON;
            if (offerTag.contains(TAG_OFFER_TIER, Tag.TAG_STRING))
            {
                String name = offerTag.getString(TAG_OFFER_TIER);
                try
                {
                    tier = MarketTier.valueOf(name);
                }
                catch (IllegalArgumentException ex)
                {
                    // Unknown tier string from older version; keep default
                }
            }
            else if (offerTag.contains(TAG_OFFER_TIER, Tag.TAG_INT))
            {
                int ord = offerTag.getInt(TAG_OFFER_TIER);
                MarketTier[] values = MarketTier.values();
                if (ord >= 0 && ord < values.length)
                {
                    tier = values[ord];
                }
            }

            // stack
            ItemStack stack = ItemStack.EMPTY;
            if (offerTag.contains(TAG_OFFER_STACK, Tag.TAG_COMPOUND))
            {
                // 1.21+ safe read using provider
                stack = ItemStack.parseOptional(provider, NullnessBridge.assumeNonnull(offerTag.getCompound(TAG_OFFER_STACK)));
            }

            // price
            int price = -1;
            if (offerTag.contains(TAG_OFFER_PRICE, Tag.TAG_COMPOUND))
            {
                price = offerTag.getInt(TAG_OFFER_PRICE);
            }

            if (!stack.isEmpty() && price > 0)
            {
                loaded.add(new MarketOffer(stack, tier, price));
            }
        }

        // Replace state
        offers.clear();
        offers.addAll(loaded);

        markDirty();
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
        if (compound == null || provider == null)
        {
            return;
        }

        compound.putLong(TAG_LAST_ROLL, lastRollDay);

        final ListTag list = new ListTag();

        for (final MarketOffer offer : offers)
        {
            if (offer == null || offer.stack() == null || offer.price() <= 0)
            {
                continue;
            }

            final ItemStack stack = offer.stack();
            final int price = offer.price();

            if (stack.isEmpty())
            {
                continue;
            }

            final CompoundTag offerTag = new CompoundTag();

            // Store tier as enum name (stable across reorderings)
            offerTag.putString(TAG_OFFER_TIER, NullnessBridge.assumeNonnull(offer.tier().name()));

            // Store item stack using provider-aware save (1.21 safe)
            offerTag.put(TAG_OFFER_STACK, NullnessBridge.assumeNonnull(stack.save(provider)));

            offerTag.putInt(TAG_OFFER_PRICE, price);

            list.add(offerTag);
        }

        compound.put(TAG_OFFERS, list);
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
        // Always write lastRoll first so client can show “last refreshed”
        buf.writeLong(lastRollDay);

        // Write offers count + each offer
        buf.writeVarInt(offers.size());

        for (final MarketOffer offer : offers)
        {
            // Defensive defaults (client code must mirror this)
            final ItemStack stack = (offer != null && offer.stack() != null) ? offer.stack() : ItemStack.EMPTY;
            final int price = (offer != null && offer.price() > 0) ? offer.price() : -1;
            final MarketTier tier = (offer != null && offer.tier() != null) ? offer.tier() : MarketTier.TIER1_COMMON;

            // ItemStack: MineColonies commonly uses a helper (Utils.serializeCodecMess / deserializeCodecMess).
            Utils.serializeCodecMess(buf, stack);
            buf.writeVarInt(price);

            // Tier: write enum ordinal (smaller), or name (more stable).
            // For view packets, ordinal is fine as long as you control both ends.
            buf.writeVarInt(tier.ordinal());
        }
    }

    /**
     * Rolls the daily offers for the thrift shop. This is called automatically by the building module on server side.
     * The rolled offers are stored in the offers field of this class.
     */
    public void rollDailyOffers()
    {
        Level level = building.getColony().getWorld();

        if (level == null || level.isClientSide)
        {
            return;
        }

        // Current Minecraft "day number"
        final long currentDay = level.getDayTime() / MarketDailyRoller.TICKS_PER_DAY;

        // lastRoll stores the day number
        if (lastRollDay == currentDay)
        {
            TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Rare Finds - Skipping rolls for already-rolled day: {}", building.getColony().getID(), lastRollDay));
            return; 
        }

        // TODO: Enforce building level requirement and/or research requirement.
        // TODO: Research 1: Enable offers
        // TODO: Research 1.a: Increase offer size
        // TODO: Research 1.a.1: Access rare and epic.
        // TODO: Research 1.b: Access reroll
        // TODO: Research 1.b.1: Increase reroll attempts.

        TraceUtils.dynamicTrace(TRACE_RAREFINDS, () -> LOGGER.info("Colony {}: Rare Finds - rolling daily offers for day: {}", building.getColony().getID(), currentDay));

        lastRollDay = currentDay;
        offers = MarketDailyRoller.rollDailyOffers((ServerLevel) level, building.getColony().getID(), 4, 3, 2, 1);

        markDirty();
    }
}
