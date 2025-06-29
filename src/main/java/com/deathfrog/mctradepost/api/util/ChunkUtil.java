package com.deathfrog.mctradepost.api.util;

import java.util.Comparator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ChunkUtil {

    public static final TicketType<ChunkPos> SEARCH_TICKET =
            TicketType.create("track_search", Comparator.comparingLong(ChunkPos::toLong), 2);


    /**
     * Ensure the given chunk is loaded, and if not, load it synchronously.
     * This method is used during pathfinding to ensure that the chunk containing the rail
     * block is loaded. If the chunk is already loaded, nothing happens.
     * @param level The level to check.
     * @param cp the chunk to check for.
     * @return true if the chunk is loaded, false if not.
     */
    public static boolean ensureChunkLoaded(ServerLevel level, ChunkPos cp) {
        // already resident?
        if (level.hasChunk(cp.x, cp.z)) return true;

        // 1. add a short-lived ticket (radius = 2 keeps neighbours for rail shape)
        level.getChunkSource().addRegionTicket(SEARCH_TICKET, cp, 2, cp);

        // 2. pull the chunk synchronously so the search can proceed *this* tick
        level.getChunk(cp.x, cp.z, ChunkStatus.FULL, true);   // may generate

        return level.hasChunk(cp.x, cp.z);
    }    

    /**
     * Ensure the given chunk is loaded, and if not, load it synchronously.
     * This method is used during pathfinding to ensure that the chunk containing the rail
     * block is loaded. If the chunk is already loaded, nothing happens.
     * @param level The level to check.
     * @param pos the position of the block to check for.
     * @return true if the chunk is loaded, false if not.
     */
    public static boolean ensureChunkLoaded(ServerLevel level, BlockPos pos) {
        return ensureChunkLoaded(level, new ChunkPos(pos));
    }
}
