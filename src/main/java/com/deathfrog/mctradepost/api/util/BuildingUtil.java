package com.deathfrog.mctradepost.api.util;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.blocks.BlockTrough;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.util.Tuple;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class BuildingUtil {
    public static final String TAG_DIMENSION = "dimension";
    public static final String TAG_POSITION = "position";
    public static final String TAG_BUILDING_ID = "building_id";
    public static final String TAG_COLONY_ID = "colony_id";

    public static CompoundTag uniqueBuildingNBT(@Nonnull IBuilding building)
    { 
        CompoundTag tag = new CompoundTag();

        if (building != null)
        {
            tag.putString(TAG_DIMENSION, building.getColony().getDimension().location().toString());
            tag.putInt(TAG_COLONY_ID, building.getColony().getID());
            BlockPosUtil.write(tag, TAG_BUILDING_ID, building.getID());
            BlockPosUtil.write(tag, TAG_POSITION, building.getPosition());
        }
        
        return tag;
    }

    /**
     * Retrieves the building from the given NBT data.
     * 
     * @param tag the CompoundTag containing the data for the building.
     * @return the building, or null if none found from the given data.
     */
    public static IBuilding buildingFromNBT(@Nonnull CompoundTag tag) 
    { 
        String dimension = tag.getString(TAG_DIMENSION);
        ResourceLocation level = ResourceLocation.parse(dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

        BlockPos buildingId = BlockPosUtil.read(tag, TAG_BUILDING_ID);
        BlockPos buildingPos = BlockPosUtil.read(tag, TAG_POSITION);

        IColony colony = IColonyManager.getInstance().getColonyByPosFromDim(levelKey, buildingId);

        if (colony == null)
        {
            MCTradePostMod.LOGGER.warn("No colony identifiable from dimension {} and position {}.", dimension, buildingId);
            return null;
        } 

        IBuilding building = colony.getBuildingManager().getBuilding(buildingId);

        return building;
    }   

    /**
     * Retrieves the building from the given NBT data.
     * 
     * @param tag the CompoundTag containing the data for the building.
     * @return the building, or null if none found from the given data.
     */
    public static IBuildingView buildingViewFromNBT(@Nonnull CompoundTag tag) 
    { 
        String dimension = tag.getString(TAG_DIMENSION);
        ResourceLocation level = ResourceLocation.parse(dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

        int colonyId = tag.getInt(TAG_COLONY_ID);

        BlockPos buildingposition = BlockPosUtil.read(tag, TAG_POSITION);
        BlockPos buildingId = BlockPosUtil.read(tag, TAG_BUILDING_ID);

        IColonyView colony = IColonyManager.getInstance().getColonyView(colonyId, levelKey);

        if (colony == null)
        {
            MCTradePostMod.LOGGER.warn("No colony identifiable from colony Id {}, dimension {}.", colonyId, dimension);
            return null;
        } 

        IBuildingView building = colony.getBuilding(buildingId);

        if (building == null)
        {
            MCTradePostMod.LOGGER.warn("No building view identifiable from ID {} with position {}.", buildingId, buildingposition);
        }

        return building;
    } 

    /**
     * Finds the destination for herding within a building, searching for a block of type BlockTrough. Iterates through the bounding
     * box defined by the building's corners to locate the trough.
     *
     * @param building the building within which the search is conducted
     * @return the BlockPos of the BlockTrough if found; otherwise, the building's current position
     */
    public static BlockPos findHerdingDestination(IBuilding building)
    {
        Level level = building.getColony().getWorld();
        Tuple<BlockPos, BlockPos> corners = building.getCorners();

        BlockPos min = BlockPos.containing(Math.min(corners.getA().getX(), corners.getB().getX()),
            Math.min(corners.getA().getY(), corners.getB().getY()),
            Math.min(corners.getA().getZ(), corners.getB().getZ()));

        BlockPos max = BlockPos.containing(Math.max(corners.getA().getX(), corners.getB().getX()),
            Math.max(corners.getA().getY(), corners.getB().getY()),
            Math.max(corners.getA().getZ(), corners.getB().getZ()));

        for (int x = min.getX(); x <= max.getX(); x++)
        {
            for (int y = min.getY(); y <= max.getY(); y++)
            {
                for (int z = min.getZ(); z <= max.getZ(); z++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof BlockTrough)
                    {
                        return pos;
                    }
                }
            }
        }

        return building.getPosition(); // No trough found
    }
}
