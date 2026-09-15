package com.deathfrog.mctradepost.core.rarefinds.blacklist;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.ModTags;
import com.deathfrog.mctradepost.core.blacklist.ItemBlacklistRules;
import com.google.gson.JsonElement;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Server-authoritative Rare Finds blacklist. The legacy item tag remains an
 * unconditional deny; allow rules only override deny rules from this manager.
 */
public final class RareFindBlacklistManager extends SimpleJsonResourceReloadListener
{
    public static final String RARE_FINDS_BLACKLIST_FOLDER = "rarefinds_blacklist";
    private static final ItemBlacklistRules RULES = new ItemBlacklistRules("Rare Finds");
    private static volatile Set<Item> synchronizedItems = Set.of();

    /** Creates the server resource reload listener for Rare Finds rules. */
    public RareFindBlacklistManager()
    {
        super(MCTradePostMod.GSON, RARE_FINDS_BLACKLIST_FOLDER);
    }

    /**
     * Rebuilds Rare Finds rules after datapack resources are prepared.
     *
     * @param jsonMap blacklist JSON resources
     * @param resourceManager active resource manager
     * @param profiler reload profiler
     */
    @Override
    protected void apply(@Nonnull final Map<ResourceLocation, JsonElement> jsonMap,
        @Nonnull final ResourceManager resourceManager, @Nonnull final ProfilerFiller profiler)
    {
        RULES.load(jsonMap);
    }

    /**
     * Registers the Rare Finds listener with the server resource reload.
     *
     * @param event reload-listener registration event
     */
    @SubscribeEvent
    public static void listenForRareFindBlacklistRecords(@Nonnull final AddReloadListenerEvent event)
    {
        event.addListener(new RareFindBlacklistManager());
    }

    /**
     * Evaluates legacy tag membership, synchronized client state, and datapack rules.
     *
     * @param stack item stack to evaluate
     * @return whether the stack is excluded from Rare Finds
     */
    public static boolean isBlacklisted(final ItemStack stack)
    {
        return stack == null || stack.isEmpty()
            || stack.is(ModTags.ITEMS.RARE_FINDS_BLACKLIST_TAG)
            || synchronizedItems.contains(stack.getItem())
            || RULES.isBlacklisted(stack, null);
    }

    /**
     * Builds the complete server result that is sent to clients.
     *
     * @return immutable set of all effectively blacklisted registered items
     */
    public static Set<Item> effectiveItems()
    {
        final Set<Item> result = new HashSet<>();
        for (final Item item : BuiltInRegistries.ITEM)
        {
            if (item == null) continue;

            final ItemStack stack = new ItemStack(item);
            if (stack.is(ModTags.ITEMS.RARE_FINDS_BLACKLIST_TAG) || RULES.isBlacklisted(stack, null))
            {
                result.add(item);
            }
        }
        return Set.copyOf(result);
    }

    /**
     * Installs a server-provided snapshot for client-side candidate filtering.
     *
     * @param items effective blacklist received from the server
     */
    public static void installSynchronizedItems(@Nonnull final Set<Item> items)
    {
        synchronizedItems = Set.copyOf(items);
    }
}
