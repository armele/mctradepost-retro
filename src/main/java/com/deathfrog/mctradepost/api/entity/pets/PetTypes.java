package com.deathfrog.mctradepost.api.entity.pets;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum PetTypes
{
    WOLF(new ItemStack(Items.BONE, 16), PetWolf.class, MCTradePostMod.PET_WOLF.get()),
    FOX(new ItemStack(Items.SWEET_BERRIES, 16), PetFox.class, MCTradePostMod.PET_FOX.get()),
    AXOLOTL(new ItemStack(Items.KELP, 16), PetAxolotl.class, MCTradePostMod.PET_AXOLOTL.get());


    private final ItemStack trainingItem;
    private final Class<? extends Animal> petClass;
    private final EntityType<? extends Animal> entityType;

    PetTypes(ItemStack trainingItem, Class<? extends Animal> petClass, EntityType<? extends Animal> entityType)
    {
        this.trainingItem = trainingItem;
        this.petClass = petClass;
        this.entityType = entityType;
    }

    /**
     * Gets the item that is required to train this pet.
     *
     * @return the item that is required to train this pet
     */
    public ItemStack getTrainingItem() {
        return trainingItem;
    }

    /**
     * Gets the class of the pet entity that is created when this type of pet is trained.
     *
     * @return the class of the pet entity that is created when this type of pet is trained
     */
    public Class<? extends Animal> getPetClass() {
        return petClass;
    }

    /**
     * Gets the EntityType of the pet entity that is created when this type of pet is trained.
     *
     * @return the EntityType of the pet entity that is created when this type of pet is trained
     */
    public EntityType<? extends Animal> getEntityType() {
        return entityType;
    }
}