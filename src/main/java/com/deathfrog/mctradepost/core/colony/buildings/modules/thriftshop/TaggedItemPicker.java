package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TaggedItemPicker
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<TagKey<Item>, List<Item>> CACHE = new HashMap<>();

    /**
     * Prevent log spam: remember which item ids we’ve already warned about.
     * (If you prefer “warn once per reload”, clear this in clearCache().)
     */
    private static final Set<String> MULTI_TIER_WARNED = new HashSet<>();

    // The tier tags we want to detect conflicts across.
    private static final List<TagKey<Item>> TIER_TAGS = List.of(
        ModTags.RARE_FINDS_TIER1_TAG,
        ModTags.RARE_FINDS_TIER2_TAG,
        ModTags.RARE_FINDS_TIER3_TAG,
        ModTags.RARE_FINDS_TIER4_TAG
    );

    private TaggedItemPicker() {}

    /**
     * Roll a single item from a datapack tag.
     * <p>
     * If the tag is empty or contains no items, an empty item stack is returned.
     * If the tag is not empty, a single item stack is generated from the tag and returned.
     * <p>
     * This method is intended to be used on the server side. On the client side, it will always return an empty item stack.
     * @param level the server level to retrieve the loot table from
     * @param rand the random source to use when retrieving the item
     * @param tag the datapack tag to retrieve the item from
     * @return a single item stack from the given datapack tag, or an empty item stack if the tag is empty or contains no items
     */
    public static ItemStack rollFromTag(ServerLevel level, RandomSource rand, @Nonnull TagKey<Item> tag)
    {
        if (level == null || level.isClientSide) return ItemStack.EMPTY;

        List<Item> items = CACHE.computeIfAbsent(tag, t -> t != null ? buildList(level, t) : List.of());
        if (items.isEmpty()) return ItemStack.EMPTY;

        Item picked = items.get(rand.nextInt(items.size()));

        if (picked == null) return ItemStack.EMPTY;

        int size = 8;

        if (tag.equals(ModTags.RARE_FINDS_TIER1_TAG)) size = 32;
        else if (tag.equals(ModTags.RARE_FINDS_TIER2_TAG)) size = 16;
        else if (tag.equals(ModTags.RARE_FINDS_TIER3_TAG)) size = 8;
        else if (tag.equals(ModTags.RARE_FINDS_TIER4_TAG)) size = 2;

        return new ItemStack(picked, rand.nextInt(size) + 1);
    }


    /**
     * Builds a list of items from a datapack tag.
     * <p>
     * If the tag is empty or contains no items, an empty list is returned.
     * If the tag is not empty, a list of items is generated from the tag and returned.
     * <p>
     * This method is intended to be used on the server side. On the client side, it will always return an empty list.
     * @param level the server level to retrieve the loot table from
     * @param tag the datapack tag to retrieve the items from
     * @return a list of items from the given datapack tag, or an empty list if the tag is empty or contains no items
     */
    private static List<Item> buildList(Level level, @Nonnull TagKey<Item> tag)
    {
        if (level == null) return List.of();

        final HolderLookup.RegistryLookup<Item> itemLookup = level.registryAccess().lookupOrThrow(NullnessBridge.assumeNonnull(Registries.ITEM));

        // In 1.21, Tag lookup yields a HolderSet.Named<T>
        final Optional<HolderSet.Named<Item>> maybeNamed = itemLookup.get(tag);

        if (maybeNamed.isEmpty())
        {
            return List.of();
        }

        final HolderSet.Named<Item> named = maybeNamed.get();

        final List<Item> out = new java.util.ArrayList<>();

        for (Holder<Item> holder : named)
        {
            if (holder == null) continue;

            final Item item = holder.value();
            if (item == null) continue;

            // Conflict detection across tier tags (logging only)
            warnIfMultiTier(holder);

            out.add(item);
        }

        return java.util.Collections.unmodifiableList(out);
    }

    /**
     * Warns if an item appears in more than one tier tag.
     * <p>
     * This method checks if the given item stack matches any of the tier tags. If it matches more than one, a warning is logged.
     * The warning includes the ID of the item and the names of the tags that it matches.
     * <p>
     * The warning is only logged once per item ID, to avoid spamming the log.
     * @param probe the item stack to check
     */
    private static void warnIfMultiTier(@Nonnull Holder<Item> probe)
    {
        int matches = 0;
        StringBuilder tags = new StringBuilder();

        for (TagKey<Item> t : TIER_TAGS)
        {
            if (t == null) continue;

            if (probe.is(t))
            {
                matches++;
                if (tags.length() > 0) tags.append(", ");
                tags.append(t.location());
            }
        }

        if (matches > 1)
        {
            Item probedItem = probe.value();

            if (probedItem == null) return;

            String itemId = BuiltInRegistries.ITEM.getKey(probedItem).toString();

            // Log once to avoid spam
            if (MULTI_TIER_WARNED.add(itemId))
            {
                LOGGER.warn("[RareFinds] Item {} appears in multiple tier tags: {}", itemId, tags);
            }
        }
    }

    /** Call this on datapack reload if you want tags to update without restart. */
    public static void clearCache()
    {
        CACHE.clear();
        MULTI_TIER_WARNED.clear(); // so warnings can reappear after reload (optional)
    }
}
