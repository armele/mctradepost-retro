package com.deathfrog.mctradepost.api.entity.pets;

import java.util.UUID;

import com.minecolonies.api.colony.buildings.IBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

public interface ITradePostPet
{
    public UUID getUUID();
    ResourceKey<Level> getDimension();

    public void setTrainerBuilding(IBuilding building);

    public IBuilding getTrainerBuilding();

    public void setWorkLocation(BlockPos location);

    public BlockPos getWorkLocation();

    public String getAnimalType();

    public <P extends Animal & ITradePostPet & IHerdingPet> PetData<P> getPetData();

    public ItemStackHandler getInventory();

    public void resetGoals();

    default String petInfo()
    {
        return String.format(
            "%s[animalType=%s, role=%s, trainerBuilding=%s, workLocation=%s, petLocation=%s, availableGoals=%s, activeGoal=%s, stallTicks=%d, inventorySize=%d]",
            this.getClass().getSimpleName(),
            getAnimalType() != null ? getAnimalType() : "null",
            getPetData() != null && getWorkLocation() != null && getPetData().getAnimal() != null ?
                getPetData().roleFromWorkLocation(getPetData().getAnimal().level()) :
                "Unassigned",
            getTrainerBuilding() != null ? getTrainerBuilding().getClass().getSimpleName() : "null",
            getWorkLocation() != null && !BlockPos.ZERO.equals(getWorkLocation()) ? getWorkLocation().toShortString() :
                "No work location set",
            getPetData() != null ? getPetData().getAnimal().getOnPos().toShortString() : "No Pet Data available.",
            getPetData() != null && getPetData().getAvailableGoals() != null && !getPetData().getAvailableGoals().isEmpty() ?
                getPetData().getAvailableGoals().stream().map(w -> w.getPriority() + ":" + w.getGoal().getClass().getSimpleName()).toList() :
                "No Available Goals",
            getPetData() != null && getPetData().getActiveGoal() != null ? getPetData().getActiveGoal().getClass().getSimpleName() :
                "No Active Goal",
            getPetData() != null ? getPetData().getStallTicks() : -1,
            getInventory() != null ? getInventory().getSlots() : 0);
    }
}
