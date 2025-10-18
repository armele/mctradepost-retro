package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.Objects;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class StationData
{
    public static final StationData EMPTY =
        new StationData(ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")),
            -1,
            BlockPos.ZERO);

    public enum TrackConnectionStatus
    {
        CONNECTED, DISCONNECTED, UNKNOWN
    }

    private BlockPos buildingposition = null;
    private int colonyId = -1;
    private ResourceKey<Level> dimension = null;

    public StationData(ITradeCapable building)
    {
        this.buildingposition = building.getPosition();
        colonyId = building.getColony().getID();
        dimension = building.getColony().getDimension();
    }

    public StationData(ResourceKey<Level> dimension, int colonyId, BlockPos buildingpos)
    {
        this.dimension = dimension;
        this.colonyId = colonyId;
        this.buildingposition = buildingpos;
    }

    /**
     * Retrieves the station that this StationData object refers to.
     *
     * @return the station that this StationData object refers to.
     */
    public ITradeCapable getStation()
    {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromDim(dimension, buildingposition);
        if (colony == null)
        {
            BuildingStation.LOGGER.warn("No colony identifiable from dimension {} and position {}.", dimension, buildingposition);
            return null;
        }

        ITradeCapable station = (ITradeCapable) colony.getBuildingManager().getBuilding(buildingposition);

        return station;
    }

    /**
     * Determines whether the station associated with this StationData object is an outpost (i.e. a BuildingOutpost).
     *
     * @return true if the station is an outpost, false if not.
     */
    public boolean isOutpost()
    {
        return getStation() instanceof BuildingOutpost;
    }

    /**
     * Retrieves the dimension of the colony associated with this station.
     * 
     * @return the dimension of the colony associated with this station.
     */
    public ResourceKey<Level> getDimension()
    {
        return dimension;
    }

    /**
     * Retrieves the ID of the colony associated with this station.
     *
     * @return the colony ID.
     */
    public int getColonyId()
    {
        return colonyId;
    }

    /**
     * Retrieves the BlockPos of this station.
     * 
     * @return the BlockPos of this station.
     */
    public BlockPos getBuildingPosition()
    {
        return buildingposition;
    }

    /**
     * Retrieves the BlockPos of the starting point of the track associated with this station.
     * 
     * @return the BlockPos of the track associated with this station.
     */
    public BlockPos getRailStartPosition()
    {
        BlockPos railposition = BlockPos.ZERO;

        ITradeCapable building = getStation();
        if (building != null)
        {
            railposition = building.getRailStartPosition();
        }

        return railposition;
    }

    /**
     * Serializes the station data to NBT. The tag contains the following information: - The track connection status, which is one of
     * CONNECTED, DISCONNECTED, or UNKNOWN. - The position of the station, as a set of integers containing the x, y, and z coordinates
     * of the station's block position. - The game time at which the track connection status was last updated, in milliseconds.
     *
     * @return the serialized NBT tag.
     */
    public CompoundTag toNBT()
    {
        CompoundTag tag = new CompoundTag();

        tag.putString("dimension", dimension.location().toString());
        tag.putInt("colonyId", colonyId);

        BlockPosUtil.write(tag, "buildingposition", buildingposition);

        return tag;
    }

    /**
     * Deserializes the station data from NBT. The tag is expected to contain the following information: - The track connection status,
     * which is one of CONNECTED, DISCONNECTED, or UNKNOWN, as a string. - The position of the station, as a set of integers containing
     * the x, y, and z coordinates of the station's block position. - The game time at which the track connection status was last
     * updated, in milliseconds.
     *
     * @param level The level in which the station is located.
     * @param tag   The tag containing the serialized station data.
     * @return The deserialized station data.
     */
    public static StationData fromNBT(CompoundTag tag)
    {
        BlockPos buildingposition = BlockPos.ZERO;
        StationData data = null;
        int colonyId = -1;

        if (tag.contains("colonyId"))
        {
            colonyId = tag.getInt("colonyId");
        }

        buildingposition = BlockPosUtil.read(tag, "buildingposition");

        if (buildingposition == null || BlockPos.ZERO.equals(buildingposition))
        {
            BuildingStation.LOGGER.warn("Failed to deserialize station positions from tag: {} - no position found.", tag);
            return null;
        }

        String dimension = tag.getString("dimension");
        ResourceLocation level = ResourceLocation.parse(dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

        data = new StationData(levelKey, colonyId, buildingposition);

        // BuildingStation.LOGGER.info("Deserialized station data: {}", data);

        return data;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString()
    {
        return "StationData {" + "colonyId = " +
            colonyId +
            ", dimension = " +
            dimension +
            ", buildingposition = " +
            buildingposition +
            '}';
    }

    /**
     * Compares this StationData object to another object for equality. Two StationData objects are considered equal if they have the
     * same colonyId, buildingposition, and dimension.
     *
     * @param obj the object to compare for equality.
     * @return true if the specified object is equal to this StationData; false otherwise.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        StationData that = (StationData) obj;
        return colonyId == that.colonyId && Objects.equals(buildingposition, that.buildingposition) &&
            Objects.equals(dimension, that.dimension);
    }

    /**
     * Returns a hash code value for the object. This method is supported for the benefit of hash tables such as those provided by
     * {@link java.util.HashMap}.
     * <p>The general contract of <code>hashCode</code> is:
     * <ul>
     * <li>Whenever <code>it1.equals(it2)</code> is <code>true</code>, then <code>it1.hashCode()==it2.hashCode()</code> must also be
     * <code>true</code>.</li>
     * <li>Whenever <code>it1.hashCode()==it2.hashCode()</code> is <code>true</code>, then <code>it1.equals(it2)</code> must also be
     * <code>true</code>.</li>
     * </ul>
     * <p>The hashcode of this object is the hashcode of the buildingposition, the hashcode of the colonyId, and the hashcode of the
     * dimension combined into one single hashcode.
     * 
     * @return a hash code value for the object.
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(buildingposition, colonyId, dimension);
    }
}
