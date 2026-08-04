package com.deathfrog.mctradepost.core.rarefinds.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the structure-template to loot-table reverse map absent from vanilla. */
public final class StructureLootScanner
{
    /**
     * One loot-table occurrence found in a structure template.
     *
     * @param structure structure template ID
     * @param lootTable referenced loot table ID
     * @param nbtPath diagnostic path to the reference
     */
    public record Reference(ResourceLocation structure, ResourceLocation lootTable, String nbtPath) { }

    /**
     * Aggregate structure scan output.
     *
     * @param references every discovered loot-table occurrence
     * @param allStructures every successfully enumerated structure template ID
     */
    public record Result(List<Reference> references, Set<ResourceLocation> allStructures) { }

    /** Prevents instantiation of this utility class. */
    private StructureLootScanner() { }

    /**
     * Scans every effective compressed structure NBT resource.
     *
     * @param manager resolved server resource manager
     * @return deterministic reverse-map records and all scanned structures
     */
    @SuppressWarnings("null")
    public static Result scan(final ResourceManager manager)
    {
        final List<Reference> references = new ArrayList<>();
        final Set<ResourceLocation> structures = new HashSet<>();
        final Map<ResourceLocation, Resource> files = manager.listResources("structure", id -> id.getPath().endsWith(".nbt"));
        for (final Map.Entry<ResourceLocation, Resource> entry : files.entrySet())
        {
            final ResourceLocation structure = structureId(entry.getKey());
            structures.add(structure);
            try (InputStream input = entry.getValue().open())
            {
                if (input == null) continue;

                final CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                scanTag(root, structure, "$", references);
            }
            catch (Exception ex)
            {
                MCTradePostMod.LOGGER.warn("Unable to scan structure template {}", structure, ex);
            }
        }
        references.sort(Comparator.comparing((Reference r) -> r.lootTable().toString())
            .thenComparing(r -> r.structure().toString()).thenComparing(Reference::nbtPath));
        return new Result(List.copyOf(references), Set.copyOf(structures));
    }

    /**
     * Recursively visits compound and list tags to find block-entity and entity loot references.
     *
     * @param tag current NBT node
     * @param structure owning structure template
     * @param path diagnostic path within the NBT tree
     * @param output discovered-reference accumulator
     */
    @SuppressWarnings("null")
    private static void scanTag(final Tag tag, final ResourceLocation structure, final String path,
                                final List<Reference> output)
    {
        if (tag instanceof CompoundTag compound)
        {
            for (final String key : compound.getAllKeys())
            {
                if (key == null) continue;

                final Tag child = compound.get(key);
                if ("LootTable".equals(key) && child != null)
                {
                    final ResourceLocation table = ResourceLocation.tryParse(child.getAsString());
                    if (table != null) output.add(new Reference(structure, table, path + ".LootTable"));
                }
                if (child != null) scanTag(child, structure, path + "." + key, output);
            }
        }
        else if (tag instanceof net.minecraft.nbt.ListTag list)
        {
            for (int i = 0; i < list.size(); i++) scanTag(list.get(i), structure, path + "[" + i + "]", output);
        }
    }

    /**
     * Converts {@code structure/<path>.nbt} resource paths into structure IDs.
     *
     * @param file structure resource location
     * @return logical structure template ID
     */
    @SuppressWarnings("null")
    private static ResourceLocation structureId(final ResourceLocation file)
    {
        final String path = file.getPath();
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
            path.substring("structure/".length(), path.length() - ".nbt".length()));
    }
}
