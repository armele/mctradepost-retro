package com.deathfrog.mctradepost.api.util;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.minecolonies.api.colony.IAnimalData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.managers.interfaces.IManagedAnimal;

import net.minecraft.world.entity.animal.Animal;

public final class PetAnimalManagerUtil
{
    private PetAnimalManagerUtil()
    {
    }

    @SuppressWarnings("unchecked")
    public static void ensureManaged(@Nonnull final ITradePostPet pet)
    {
        if (!(pet instanceof Animal animal) || animal.level().isClientSide || !(pet instanceof IManagedAnimal))
        {
            return;
        }

        final IBuilding trainerBuilding = resolveTrainerBuilding(pet);
        if (trainerBuilding == null)
        {
            return;
        }

        final IManagedAnimal<? extends Animal> managedAnimal = (IManagedAnimal<? extends Animal>) pet;
        final IColony colony = trainerBuilding.getColony();
        IAnimalData animalData = managedAnimal.getAnimalData();

        if (animalData == null && managedAnimal.getManagedAnimalId() != 0)
        {
            final IAnimalData existingData = colony.getAnimalManager().getAnimal(managedAnimal.getManagedAnimalId());
            if (existingData != null && animal.getUUID().equals(existingData.getUUID()))
            {
                colony.getAnimalManager().registerAnimal(managedAnimal);
                animalData = managedAnimal.getAnimalData();
            }
        }

        if (animalData == null || managedAnimal.getManagedAnimalId() == 0 || colony.getAnimalManager().getAnimal(managedAnimal.getManagedAnimalId()) == null)
        {
            animalData = colony.getAnimalManager().createAndRegisterAnimalData(managedAnimal);
            managedAnimal.setAnimalData(animalData);
        }

        if (animalData == null)
        {
            return;
        }

        managedAnimal.setColonyId(colony.getID());

        if (animalData.getHomeBuilding() == null || !animalData.getHomeBuilding().equals(trainerBuilding))
        {
            animalData.setHomeBuilding(trainerBuilding);
        }

        if (trainerBuilding instanceof BuildingPetshop petshop)
        {
            petshop.applyPersistedPetData(pet);
            petshop.rememberPetData(pet);
        }
    }

    public static boolean recoverRegistration(@Nonnull final ITradePostPet pet)
    {
        if (!(pet instanceof Animal animal) || animal.level().isClientSide)
        {
            return false;
        }

        final IBuilding trainerBuildingBefore = pet.getTrainerBuilding();
        final IBuilding trainerBuilding = resolveTrainerBuilding(pet);
        if (trainerBuilding == null)
        {
            return false;
        }

        boolean changed = trainerBuildingBefore == null || !trainerBuildingBefore.equals(trainerBuilding);
        if (trainerBuildingBefore == null)
        {
            pet.setTrainerBuilding(trainerBuilding);
        }

        if (!PetRegistryUtil.isRegistered(pet))
        {
            changed = PetRegistryUtil.register(pet) || changed;
        }
        else
        {
            ensureManaged(pet);
        }

        return changed;
    }

    @SuppressWarnings("unchecked")
    private static IBuilding resolveTrainerBuilding(@Nonnull final ITradePostPet pet)
    {
        final IBuilding trainerBuilding = pet.getTrainerBuilding();
        if (trainerBuilding != null)
        {
            return trainerBuilding;
        }

        if (!(pet instanceof Animal animal) || !(pet instanceof IManagedAnimal))
        {
            return null;
        }

        final IManagedAnimal<? extends Animal> managedAnimal = (IManagedAnimal<? extends Animal>) pet;
        IAnimalData animalData = managedAnimal.getAnimalData();
        if (animalData == null && managedAnimal.getColonyId() != 0 && managedAnimal.getManagedAnimalId() != 0)
        {
            final IColony colony = IColonyManager.getInstance().getColonyByWorld(managedAnimal.getColonyId(), animal.level());
            if (colony != null)
            {
                animalData = colony.getAnimalManager().getAnimal(managedAnimal.getManagedAnimalId());
                if (animalData != null && animal.getUUID().equals(animalData.getUUID()))
                {
                    colony.getAnimalManager().registerAnimal(managedAnimal);
                    animalData = managedAnimal.getAnimalData();
                }
            }
        }

        if (animalData == null || animalData.getHomeBuilding() == null)
        {
            return null;
        }

        pet.setTrainerBuilding(animalData.getHomeBuilding());
        return animalData.getHomeBuilding();
    }

    @SuppressWarnings("unchecked")
    public static void clearHomeBuilding(@Nonnull final ITradePostPet pet)
    {
        if (!(pet instanceof IManagedAnimal))
        {
            return;
        }

        final IManagedAnimal<? extends Animal> managedAnimal = (IManagedAnimal<? extends Animal>) pet;
        final IAnimalData animalData = managedAnimal.getAnimalData();
        if (animalData != null)
        {
            animalData.setHomeBuilding(null);
        }
    }
}
