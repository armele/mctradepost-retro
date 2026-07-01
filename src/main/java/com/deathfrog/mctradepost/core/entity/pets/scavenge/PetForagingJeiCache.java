package com.deathfrog.mctradepost.core.entity.pets.scavenge;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for server-synced pet foraging JEI entries.
 * <p>
 * JEI registration and packet handling happen through different lifecycles, so this small cache gives the JEI plugin a stable place
 * to read the most recent server-provided display data.
 * </p>
 */
public final class PetForagingJeiCache
{
    private static List<PetForagingJeiEntry> entries = List.of();

    private PetForagingJeiCache()
    {
    }

    /**
     * Returns the most recently synced foraging entries.
     *
     * @return immutable list of display entries
     */
    public static List<PetForagingJeiEntry> getEntries()
    {
        return entries;
    }

    /**
     * Replaces the cached entries with a defensive immutable copy of the server-provided data.
     *
     * @param syncedEntries entries received from the server
     */
    public static void setEntries(final List<PetForagingJeiEntry> syncedEntries)
    {
        entries = List.copyOf(new ArrayList<>(syncedEntries));
    }
}
