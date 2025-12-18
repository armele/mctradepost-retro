package com.deathfrog.mctradepost.api.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class DimensionUtil
{
    /**
     * Try to resolve a Level from a ResourceKey on either side.
     *
     * @param key            the dimension key, e.g. minecraft:overworld
     * @param referenceLevel any Level you already have handy (often `this.level` or colony.getWorld()). It tells us which side we’re
     *                       on.
     * @return the Level if available, else {@code null}.
     */
    @Nullable
    public static Level resolveLevel(@Nonnull ResourceKey<Level> key, Level referenceLevel)
    {
        // --- Server side ----------------------------------------------------
        if (referenceLevel instanceof ServerLevel serverLevel)
        {
            return serverLevel.getServer().getLevel(key);     // may be null if not loaded
        }

        // --- Client side ----------------------------------------------------
        if (referenceLevel instanceof ClientLevel clientLevel)
        {
            // Client only has its current world; make sure it matches
            return clientLevel.dimension().equals(key) ? clientLevel : null;
        }

        return null;  // Fallback (dedicated data packs, etc.)
    }
}
