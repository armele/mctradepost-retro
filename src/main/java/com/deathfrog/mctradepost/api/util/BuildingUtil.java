package com.deathfrog.mctradepost.api.util;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

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
     * Retrieves the building associated with the given dimension and position.
     *
     * @param dimensionKey the key of the dimension where the building is located.
     * @param buildingId the position identifier of the building within the dimension.
     * @return the building located at the specified dimension and position, or null if no building or colony is found.
     */
    public static IBuilding buildingFromDimPos(ResourceKey<Level> dimensionKey, BlockPos buildingId)
    {
        if (buildingId == null || BlockPos.ZERO.equals(buildingId))
        {
            return null;
        }

        IColony colony = IColonyManager.getInstance().getColonyByPosFromDim(dimensionKey, buildingId);

        if (colony == null)
        {
            MCTradePostMod.LOGGER.warn("No colony identifiable from dimension {} and position {}. {}", dimensionKey, buildingId, new Exception());
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
    public static IBuilding buildingFromNBT(@Nonnull CompoundTag tag) 
    { 
        IBuilding building = null;
        String dimension = tag.getString(TAG_DIMENSION);
        ResourceLocation level = ResourceLocation.parse(dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

        BlockPos buildingId = BlockPosUtil.read(tag, TAG_BUILDING_ID);

        if (buildingId != null && !BlockPos.ZERO.equals(buildingId))
        {
            building = buildingFromDimPos(levelKey, buildingId);
        }

        return building;
    }   

    /**
     * Retrieves the building view associated with the given dimension and position.
     * 
     * @param dimensionKey the key of the dimension where the building is located.
     * @param buildingId the position identifier of the building within the dimension.
     * @return the building view located at the specified dimension and position, or null if no building view or colony is found.
     */
    public static IBuildingView buildingViewFromDimPos(ResourceKey<Level> dimensionKey, BlockPos buildingId)
    {
        IBuildingView buildingView = IColonyManager.getInstance().getBuildingView(dimensionKey, buildingId);

        if (buildingView == null)
        {
            // MCTradePostMod.LOGGER.warn("No building view identifiable from ID {} with position {}.", buildingId, buildingId);
        }
        
        return buildingView;
    }

    /**
     * Retrieves the building view from the given NBT data.
     * 
     * @param tag the CompoundTag containing the data for the building.
     * @return the building, or null if none found from the given data.
     */
    public static IBuildingView buildingViewFromNBT(@Nonnull CompoundTag tag) 
    { 
        String dimension = tag.getString(TAG_DIMENSION);
        ResourceLocation level = ResourceLocation.parse(dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

        BlockPos buildingId = BlockPosUtil.read(tag, TAG_BUILDING_ID);

        IBuildingView buildingView = buildingViewFromDimPos(levelKey, buildingId);

        return buildingView;
    } 
}
