package com.deathfrog.mctradepost.api.entity.pets;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum PetTypes
{
    WOLF(new ItemStack(Items.BONE, 16), PetWolf.class, MCTradePostMod.PET_WOLF.get(), "Wolf"),
    FOX(new ItemStack(Items.SWEET_BERRIES, 16), PetFox.class, MCTradePostMod.PET_FOX.get(), "Fox"),
    AXOLOTL(new ItemStack(Items.KELP, 16), PetAxolotl.class, MCTradePostMod.PET_AXOLOTL.get(), "Axolotl");


    private final ItemStack trainingItem;
    private final Class<? extends Animal> petClass;
    private final EntityType<? extends Animal> entityType;
    private final String typeName;

    PetTypes(ItemStack trainingItem, Class<? extends Animal> petClass, EntityType<? extends Animal> entityType, String typeName)
    {
        this.trainingItem = trainingItem;
        this.petClass = petClass;
        this.entityType = entityType;
        this.typeName = typeName;
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

    /**
     * Gets the human-readable name of the pet type, such as "Wolf", "Fox", or "Axolotl".
     * This name is used in the user interface to display the name of the pet.
     * 
     * @return the human-readable name of the pet type
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * Given a Class object for a pet entity, returns the corresponding PetTypes enum
     * value, or null if no such pet is registered.
     *
     * @param petClass the Class object for the pet entity
     * @return the PetTypes enum value for the given pet class, or null if no such pet
     *         is registered
     */
    public static PetTypes fromPetClass(Class<? extends Animal> petClass)
    {
        for (PetTypes type : PetTypes.values())
        {
            if (type.getPetClass().isAssignableFrom(petClass))
            {
                return type;
            }
        }
        return null;
    }

    /**
     * Given a human-readable pet type name, such as "Wolf", "Fox", or "Axolotl",
     * returns the corresponding PetTypes enum value, or null if no such pet is
     * registered.
     * 
     * @param petString the human-readable pet type name
     * @return the PetTypes enum value for the given pet name, or null if no such
     *         pet is registered
     */
    public static PetTypes fromPetString(String petString)
    {
        for (PetTypes type : PetTypes.values())
        {
            if (type.getTypeName().equals(petString))
            {
                return type;
            }
        }
        return null;
    }

    /**
     * Gets the food item that is used to tame or feed a pet of the given class.
     * 
     * @param petClass the class of the pet to get the food for
     * @return the food item that is used to tame a pet of the given class, or null if no such pet is registered
     */
    public static Item foodForPet(Class<? extends Animal> petClass)
    {
        for (PetTypes type : PetTypes.values())
        {
            if (type.getPetClass().isAssignableFrom(petClass))
            {
                return type.getTrainingItem().getItem();
            }
        }

        return null;
    }

    /**
     * Creates a PetData object for the given pet type, using the given CompoundTag to initialize it.
     * 
     * @param type the pet type to create a PetData for
     * @param compound the CompoundTag containing the data with which to initialize the PetData
     * @return the created PetData object
     */
    public static PetData<?> createPetDataForType(PetTypes type, CompoundTag compound)
    {
        PetData<?> pet = switch (type) 
        {
            case PetTypes.WOLF     -> new PetData<PetWolf>(null, compound);
            case PetTypes.FOX      -> new PetData<PetFox>(null, compound);
            case PetTypes.AXOLOTL  -> new PetData<PetAxolotl>(null, compound);
            default -> throw new IllegalArgumentException("Unknown pet type: " + type.getTypeName());
        };

        return pet;
    }
}