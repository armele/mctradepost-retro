package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;

import net.minecraft.core.BlockPos;

public class BuildingPetstore extends AbstractBuilding
{

    public BuildingPetstore(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    @Override
    public String getSchematicName()
    {
        return ModBuildings.PETSTORE_ID;
    }

    // Armadillo
    // Axolotl
    // Bat
    // Camel
    // Cat
    // Dolphin
    // Donkey
    // Fox
    // Frog
    // Goat
    // Horse
    // Llama
    // Mule
    // Ocelot
    // Panda
    // Parrot
    // Polar Bear
    // Turtle
    // Wolf

    
}
