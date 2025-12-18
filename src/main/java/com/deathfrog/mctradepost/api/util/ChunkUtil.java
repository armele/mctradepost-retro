package com.deathfrog.mctradepost.api.util;

import java.util.Comparator;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ChunkUtil
{
    @SuppressWarnings("null")
    public static final TicketType<ChunkPos> SEARCH_TICKET =
        TicketType.create("track_search", Comparator.comparingLong(ChunkPos::toLong), 2);

    /**
     * Ensure the given chunk is loaded, and if not, load it synchronously. This method is used during pathfinding to ensure that the
     * chunk containing the rail block is loaded. If the chunk is already loaded, nothing happens.
     * 
     * @param level The level to check.
     * @param cp    the chunk to check for.
     * @return true if the chunk is loaded, false if not.
     */
    public static boolean ensureChunkLoaded(ServerLevel level, ChunkPos cp)
    {
        // already resident?
        if (level.hasChunk(cp.x, cp.z)) return true;

        // 1. add a short-lived ticket (radius = 2 keeps neighbours for rail shape)
        level.getChunkSource().addRegionTicket(NullnessBridge.assumeNonnull(SEARCH_TICKET), cp, 2, cp);

        // 2. pull the chunk synchronously so the search can proceed *this* tick
        level.getChunk(cp.x, cp.z, NullnessBridge.assumeNonnull(ChunkStatus.FULL), true);   // may generate

        return level.hasChunk(cp.x, cp.z);
    }

    /**
     * Ensure the given chunk is loaded, and if not, load it synchronously. This method is used during pathfinding to ensure that the
     * chunk containing the rail block is loaded. If the chunk is already loaded, nothing happens.
     * 
     * @param level The level to check.
     * @param pos   the position of the block to check for.
     * @return true if the chunk is loaded, false if not.
     */
    public static boolean ensureChunkLoaded(@Nonnull ServerLevel level, @Nonnull BlockPos pos)
    {
        return ensureChunkLoaded(level, new ChunkPos(pos));
    }

    /**
     * Release the chunk ticket for the given BlockPos and radius. This method should be called after pathfinding has completed to
     * release the ticket and free up resources.
     * 
     * @param level  the level to release the ticket in
     * @param pos    the BlockPos of the chunk to release
     * @param radius the radius of the ticket to release
     */
    public static void releaseChunkTicket(@Nonnull ServerLevel level, @Nonnull BlockPos pos, int radius)
    {
        ChunkPos cp = new ChunkPos(pos);
        level.getChunkSource().removeRegionTicket(NullnessBridge.assumeNonnull(SEARCH_TICKET), cp, radius, cp);
    }
}
