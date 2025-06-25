package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class StationData
{
    public enum TrackConnectionStatus
    {
        CONNECTED, DISCONNECTED, UNKNOWN
    }

    private TrackConnectionStatus trackConnectionStatus = TrackConnectionStatus.UNKNOWN;
    private BlockPos position = null;
    private long lastChecked = 0;
    private int colonyId = -1;
    private ResourceKey<Level> dimension = null;

    public StationData(IBuilding building)
    {
        this.position = building.getPosition();
        colonyId = building.getColony().getID();
        dimension = building.getColony().getDimension();
        trackConnectionStatus = TrackConnectionStatus.UNKNOWN;
    }

    public StationData(ResourceKey<Level> dimension, int colonyId, BlockPos position)
    {
        this.dimension = dimension;
        this.colonyId = colonyId;
        this.position = position;
        trackConnectionStatus = TrackConnectionStatus.UNKNOWN;
    }

    /**
     * Retrieves the current track connection status.
     *
     * @return the track connection status, which indicates whether the track 
     *         is CONNECTED, DISCONNECTED, or UNKNOWN.
     */
    public TrackConnectionStatus getTrackConnectionStatus()
    {
        return trackConnectionStatus;
    }

    /**
     * Sets the track connection status, and records the game time of this setting.
     *
     * @param trackConnectionStatus the track connection status, which indicates
     *        whether the track is CONNECTED, DISCONNECTED, or UNKNOWN.
     */
    public void setTrackConnectionStatus(TrackConnectionStatus trackConnectionStatus)
    {
        this.trackConnectionStatus = trackConnectionStatus;
        lastChecked = Minecraft.getInstance().level.getGameTime();
    }

    /**
     * Retrieves the station that this StationData object refers to.
     *
     * @return the station that this StationData object refers to.
     */
    public IBuilding getStation(@Nonnull Level level)
    {
        IBuilding station = IColonyManager.getInstance().getBuilding(level, position);
        return station;
    }

    /**
     * Retrieves the game time at which the track connection status was last updated.
     * 
     * @return the game time at which the track connection status was last updated.
     */
    public long getLastChecked()
    {
        return lastChecked;
    }

    /**
     * Retrieves the dimension of the colony associated with this station.
     * 
     * @return the dimension of the colony associated with this station.
     */
    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    /**
     * Retrieves the ID of the colony associated with this station.
     *
     * @return the colony ID.
     */
    public int getColonyId() {
        return colonyId;
    }

    /**
     * Retrieves the BlockPos of this station.
     * 
     * @return the BlockPos of this station.
     */
    public BlockPos getPosition() {
        return position;
    }

    /**
     * Retrieves the age of the last check, which is the difference between the
     * current game time and the last time the track connection status was updated.
     * 
     * @return the age of the last check, in game ticks.
     */
    public long ageOfCheck()
    {
        return Minecraft.getInstance().level.getGameTime() - lastChecked;
    }

    /**
     * Serializes the station data to NBT.
     *
     * The tag contains the following information:
     *
     * - The track connection status, which is one of CONNECTED, DISCONNECTED, or
     *   UNKNOWN.
     * - The position of the station, as a set of integers containing the x, y, and
     *   z coordinates of the station's block position.
     * - The game time at which the track connection status was last updated, in
     *   milliseconds.
     *
     * @return the serialized NBT tag.
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();

        tag.putString("dimension", dimension.location().toString());
        tag.putString("trackConnectionStatus", trackConnectionStatus.name());
        tag.putInt("colonyId", colonyId);

        if (position != null) {
            tag.putInt("x", position.getX());
            tag.putInt("y", position.getY());
            tag.putInt("z", position.getZ());
        }

        tag.putLong("lastChecked", lastChecked);

        return tag;
    }


    /**
     * Deserializes the station data from NBT.
     *
     * The tag is expected to contain the following information:
     *
     * - The track connection status, which is one of CONNECTED, DISCONNECTED, or
     *   UNKNOWN, as a string.
     * - The position of the station, as a set of integers containing the x, y, and
     *   z coordinates of the station's block position.
     * - The game time at which the track connection status was last updated, in
     *   milliseconds.
     *
     * @param level The level in which the station is located.
     * @param tag The tag containing the serialized station data.
     * @return The deserialized station data.
     */
    public static StationData fromNBT(CompoundTag tag) {
        BlockPos position = BlockPos.ZERO;
        StationData data = null;
        int colonyId = -1;

        if (tag.contains("colonyId")) {
            colonyId = tag.getInt("colonyId");
        }

        if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            position = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        }

        if (BlockPos.ZERO.equals(position)) {
            BuildingStation.LOGGER.warn("Failed to deserialize station from tag: {} - no position found.", tag);
            return null;    
        }

        String dimension = tag.getString("dimension");
        ResourceLocation level = ResourceLocation.parse(dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

        data = new StationData(levelKey, colonyId, position);

        if (tag.contains("trackConnectionStatus")) {
            try {
                data.trackConnectionStatus = TrackConnectionStatus.valueOf(tag.getString("trackConnectionStatus"));
            } catch (IllegalArgumentException e) {
                data.trackConnectionStatus = TrackConnectionStatus.UNKNOWN; // Fallback
            }
        }

        data.lastChecked = tag.getLong("lastChecked");

        BuildingStation.LOGGER.debug("Deserialized station data: {}", data);

        return data;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "StationData {" +
            "colonyId = " + colonyId +
            ", dimension = " + dimension +
            ", position = " + position +
            ", trackConnectionStatus=" + trackConnectionStatus +
            ", ageOfLastCheck=" + ageOfCheck() +
            '}';
    }
}
