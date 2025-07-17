package com.deathfrog.mctradepost.api.util;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
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
}
