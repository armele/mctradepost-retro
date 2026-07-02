package com.deathfrog.mctradepost.api.entity.pets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

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
        Optional<BlockPos> spawnPos = findNearbyValidSpawn(localPet, building.getPosition(), 3);
        if (spawnPos.isEmpty())
        {
            MCTradePostMod.LOGGER.warn("Unable to find a safe spawn position near pet shop {} for pet {}.", building.getPosition(), localPet);
            return;
        }

        BlockPos targetPos = spawnPos.get();
        localPet.setPos(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        building.getColony().getWorld().addFreshEntity(localPet);
        PetRegistryUtil.register(localPet);
        building.markPetsDirty();
    }

    /**
     * Finds a nearby safe spawn position for an entity. The search prefers nearby positions
     * and validates the actual entity bounding box against block collisions.
     *
     * @param entity the entity to place.
     * @param origin the origin of the search.
     * @param radius the radius of the search.
     * @return a valid spawn position, or empty if no safe spot is found.
     */
    public static Optional<BlockPos> findNearbyValidSpawn(Entity entity, BlockPos origin, int radius)
    {
        if (entity == null || entity.level() == null || origin == null)
        {
            return Optional.empty();
        }

        return findNearbyValidSpawn(entity.level(), entity, origin, radius);
    }

    /**
     * Finds a nearby safe spawn position for an entity in the specified level.
     *
     * @param level the level where the entity should be placed
     * @param entity the entity to place
     * @param origin the origin of the search
     * @param radius the radius of the search
     * @return a valid spawn position, or empty if no safe spot is found
     */
    public static Optional<BlockPos> findNearbyValidSpawn(Level level, Entity entity, BlockPos origin, int radius)
    {
        if (level == null || entity == null || origin == null)
        {
            return Optional.empty();
        }

        List<BlockPos> candidates = new ArrayList<>();
        int searchRadius = Math.max(5, radius);

        for (int dx = -searchRadius; dx <= searchRadius; dx++)
        {
            for (int dy = -3; dy <= 3; dy++)
            {
                for (int dz = -searchRadius; dz <= searchRadius; dz++)
                {
                    candidates.add(origin.offset(dx, dy, dz));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));

        for (BlockPos candidate : candidates)
        {
            if (isSafeSpawnPosition(level, entity, candidate))
            {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    /**
     * Is this a safe spot for the designated entity to spawn?
     * 
     * @param level
     * @param entity
     * @param pos
     * @return
     */
    @SuppressWarnings("null")
    private static boolean isSafeSpawnPosition(Level level, Entity entity, BlockPos pos)
    {
        if (level == null || entity == null || pos == null || !level.isInWorldBounds(pos) || !level.isLoaded(pos))
        {
            return false;
        }

        BlockPos floorPos = pos.below();

        if (floorPos == null)
        {
            return false;
        }

        if (!level.isInWorldBounds(floorPos) || !level.isLoaded(floorPos) || !level.loadedAndEntityCanStandOn(floorPos, entity))
        {
            return false;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;
        AABB targetBox = entity.getDimensions(entity.getPose()).makeBoundingBox(x, y, z).deflate(1.0E-7D);

        if (!level.noCollision(entity, targetBox) || level.containsAnyLiquid(targetBox))
        {
            return false;
        }

        for (BlockPos occupiedPos : BlockPos.betweenClosed(
            (int) Math.floor(targetBox.minX),
            (int) Math.floor(targetBox.minY),
            (int) Math.floor(targetBox.minZ),
            (int) Math.floor(targetBox.maxX),
            (int) Math.floor(targetBox.maxY),
            (int) Math.floor(targetBox.maxZ)))
        {
            if (!level.isInWorldBounds(occupiedPos) || !level.isLoaded(occupiedPos))
            {
                return false;
            }
        }

        return true;
    }
}
