package com.deathfrog.mctradepost.core.entity.pets.scavenge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.deathfrog.mctradepost.api.entity.pets.PetRoles;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Reverse lookup from possible forage outputs to their source blocks. */
public final class FocusedForagingIndex
{
    private static MinecraftServer indexedServer;
    private static Map<PetRoles, Map<Item, Set<Block>>> sources = Map.of();

    /**
     * Prevents instantiation of this utility class.
     */
    private FocusedForagingIndex() { }

    /**
     * Returns the source blocks whose forage tables may produce an item for a
     * particular pet role. The index is initialized lazily for a new server.
     *
     * @param server server owning the active datapack resources
     * @param role forage role whose sources should be queried
     * @param item desired possible output
     * @return immutable set of compatible source blocks
     */
    public static synchronized Set<Block> sourcesFor(final MinecraftServer server, final PetRoles role, final Item item)
    {
        if (server == null || role == null || item == null) return Set.of();
        if (indexedServer != server) rebuild(server);
        return sources.getOrDefault(role, Map.of()).getOrDefault(item, Set.of());
    }

    /**
     * Determines whether one source block may produce the requested item.
     *
     * @param server server owning the active datapack resources
     * @param role forage role used to interpret the source
     * @param block prospective source block
     * @param item requested output item
     * @return {@code true} when the reverse index contains the source mapping
     */
    public static synchronized boolean mayProduce(final MinecraftServer server, final PetRoles role, final Block block, final Item item)
    {
        return sourcesFor(server, role, item).contains(block);
    }

    /**
     * Rebuilds the reverse output-to-source index from the active server's
     * flattened pet-foraging definitions.
     *
     * @param server server whose current datapack resources should be indexed
     */
    public static synchronized void rebuild(final MinecraftServer server)
    {
        final Map<PetRoles, Map<Item, Set<Block>>> mutable = new HashMap<>();
        final List<PetForagingJeiEntry> entries = PetForagingJeiDataBuilder.build(server);
        for (PetForagingJeiEntry entry : entries)
        {
            final Block source = BuiltInRegistries.BLOCK.get(entry.sourceBlock());
            
            for (ItemStack output : entry.outputs())
            {
                if (output.isEmpty()) continue;
                mutable.computeIfAbsent(entry.role(), ignored -> new HashMap<>())
                    .computeIfAbsent(output.getItem(), ignored -> new HashSet<>())
                    .add(source);
            }
        }
        
        final Map<PetRoles, Map<Item, Set<Block>>> frozen = new HashMap<>();
        mutable.forEach((role, byItem) -> {
            final Map<Item, Set<Block>> frozenItems = new HashMap<>();
            byItem.forEach((item, blocks) -> frozenItems.put(item, Set.copyOf(blocks)));
            frozen.put(role, Map.copyOf(frozenItems));
        });
        sources = Map.copyOf(frozen);
        indexedServer = server;
    }
}
