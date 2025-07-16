package com.deathfrog.mctradepost.api.entity.pets;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.NBTUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

public class BaseTradePostPet implements ITradePostPet 
{
    protected Animal animal;
    protected int colonyId;
    protected IBuilding building;

    public BaseTradePostPet(Animal animal)
    {
        this.animal = animal;
    }

    /**
     * Serializes the state of this BaseTradePostPet into the given CompoundTag.
     *
     * @param compound the CompoundTag to write the BaseTradePostPet's data into.
     *                 Includes the colony ID and, if available, the position and
     *                 dimension of the associated building.
     */
    public void toNBT(CompoundTag compound)
    {
            compound.putInt("ColonyId", this.getColonyId());
            if (this.getBuilding() != null)
            {
                compound.put("BuildingId", NBTUtils.writeBlockPos(this.getBuilding().getPosition()));
                compound.putString("Dimension", this.getBuilding().getColony().getDimension().location().toString());
            }
    }

    public void fromNBT(CompoundTag compound)
    {
        this.setColonyId(compound.getInt("ColonyId"));
        BlockPos pos = NBTUtils.readBlockPos(compound.get("BuildingId"));
        if (!pos.equals(BlockPos.ZERO))
        {
            String dimension = compound.getString("Dimension");
            ResourceLocation level = ResourceLocation.parse(dimension);
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, level);

            this.setBuilding(findBuildingByPosition(levelKey, pos));
        }
    }

    /**
     * Finds the BuildingStation associated with a given BlockPos in a given
     * dimension.
     *
     * @param dimension the dimension to search in
     * @param pos       the BlockPos to search for
     * @return the BuildingStation, or null if none is found
     */
    private IBuilding findBuildingByPosition(ResourceKey<Level> dimension, BlockPos pos)
    {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromDim(dimension, pos);
        if (colony == null)
        {
            MCTradePostMod.LOGGER.warn("No colony identifiable from dimension {} and position {}.", dimension, pos);
            return null;
        }

        BuildingStation station = (BuildingStation) colony.getBuildingManager().getBuilding(pos);

        return station;
    }

    @Override
    public void setColonyId(int colonyId)
    {
        this.colonyId = colonyId;
    }

    @Override
    public int getColonyId()
    {
        return colonyId;
    }

    @Override
    public IBuilding getBuilding()
    {
        return building;
    }

    @Override
    public void setBuilding(IBuilding building)
    {
        this.building = building;
    }

    
}
