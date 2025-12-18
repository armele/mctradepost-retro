package com.deathfrog.mctradepost.api.entity.pets;

import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

public class PetHelper<P extends Animal & ITradePostPet>
{
    protected P pet;

    public PetHelper(P pet)
    {
        this.pet = pet;
    }

    public P getPet()
    {
        return pet;
    }

    /**
     * Registers the pet with the given BuildingPetshop and adds it to the world. Marks the BuildingPetshop as dirty so that the pet
     * list gets reserialized when the building is synced to the client.
     * 
     * @param building the building to register the pet with.
     */
    public void doRegistration(BuildingPetshop building)
    {
        if (building == null) return;

        P localPet = this.pet;

        if (localPet == null) return;

        localPet.setTrainerBuilding(building);
        BlockPos spawnPos = findNearbyValidSpawn(building.getColony().getWorld(), building.getPosition(), 3);
        localPet.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        building.getColony().getWorld().addFreshEntity(localPet);
        PetRegistryUtil.register(localPet);
        building.markPetsDirty();
    }

    /**
     * Finds a nearby valid spawn position for an entity. The search is done in a cube centered at the given origin with the given
     * radius. The vertical range is small, as the search is only done in a 5-block high range centered at the origin's Y coordinate.
     *
     * @param level  the level to search in.
     * @param origin the origin of the search.
     * @param radius the radius of the search.
     * @return a valid spawn position or the origin if no valid spot is found.
     */
    public static BlockPos findNearbyValidSpawn(Level level, BlockPos origin, int radius)
    {
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -2; dy <= 2; dy++)
            { // small vertical range
                for (int dz = -radius; dz <= radius; dz++)
                {
                    BlockPos checkPos = origin.offset(dx, dy, dz);
                    BlockPos checkBelow = checkPos.below();

                    if (checkPos == null || checkBelow == null) continue;

                    if (level.getBlockState(checkPos).isAir() &&
                        level.getBlockState(checkBelow).isSolidRender(level, checkBelow))
                    {
                        return checkPos;
                    }
                }
            }
        }
        return origin; // fallback if no valid spot found
    }
}
