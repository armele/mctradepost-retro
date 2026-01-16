package com.deathfrog.mctradepost.api.entity.pets;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.minecolonies.api.blocks.ModBlocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum PetTypes
{
    WOLF(new ItemStack(NullnessBridge.assumeNonnull(Items.BONE), 16), 3, PetWolf.class, NullnessBridge.assumeNonnull(MCTradePostMod.PET_WOLF.get()), "Wolf", true),
    FOX(new ItemStack(NullnessBridge.assumeNonnull(Items.SWEET_BERRIES), 16), 3, PetFox.class, NullnessBridge.assumeNonnull(MCTradePostMod.PET_FOX.get()), "Fox", true),
    AXOLOTL(new ItemStack(NullnessBridge.assumeNonnull(Items.KELP), 16), 3, PetAxolotl.class, NullnessBridge.assumeNonnull(MCTradePostMod.PET_AXOLOTL.get()), "Axolotl", true),
    COW(new ItemStack(NullnessBridge.assumeNonnull(ModBlocks.blockDurum.asItem()), 16), 2, Cow.class, NullnessBridge.assumeNonnull(EntityType.COW), "Cow", false),
    PIG(new ItemStack(NullnessBridge.assumeNonnull(Items.CARROT), 16), 2, Pig.class, NullnessBridge.assumeNonnull(EntityType.PIG), "Pig", false),
    CHICKEN(new ItemStack(NullnessBridge.assumeNonnull(Items.WHEAT_SEEDS), 16), 2, Chicken.class, NullnessBridge.assumeNonnull(EntityType.CHICKEN), "Chicken", false),
    SHEEP(new ItemStack(NullnessBridge.assumeNonnull(Items.WHEAT), 16), 2, Sheep.class, NullnessBridge.assumeNonnull(EntityType.SHEEP), "Sheep", false);

    private final @Nonnull ItemStack trainingItem;
    private final @Nonnull Class<? extends Animal> petClass;
    private final @Nonnull EntityType<? extends Animal> entityType;
    private final @Nonnull String typeName;
    private final int coinCost;
    private final boolean isPet;

    PetTypes(@Nonnull ItemStack trainingItem,
        int coinCost,
        @Nonnull Class<? extends Animal> petClass,
        @Nonnull EntityType<? extends Animal> entityType,
        @Nonnull String typeName,
        boolean isPet)
    {
        this.trainingItem = trainingItem;
        this.petClass = petClass;
        this.entityType = entityType;
        this.typeName = typeName;
        this.coinCost = coinCost;
        this.isPet = isPet;
    }

    /**
     * Gets the item that is required to train this pet.
     *
     * @return the item that is required to train this pet
     */
    public ItemStack getTrainingItem()
    {
        return trainingItem;
    }

    /**
     * Gets the class of the pet entity that is created when this type of pet is trained.
     *
     * @return the class of the pet entity that is created when this type of pet is trained
     */
    public Class<? extends Animal> getPetClass()
    {
        return petClass;
    }

    /**
     * Gets the EntityType of the pet entity that is created when this type of pet is trained.
     *
     * @return the EntityType of the pet entity that is created when this type of pet is trained
     */
    public EntityType<? extends Animal> getEntityType()
    {
        return entityType;
    }

    /**
     * Gets the cost in coins of training this type of pet.
     * @return the cost in coins of training this type of pet
     */
    public int getCoinCost()
    {
        return coinCost;
    }

    /**
     * Gets the human-readable name of the pet type, such as "Wolf", "Fox", or "Axolotl". This name is used in the user interface to
     * display the name of the pet.
     * 
     * @return the human-readable name of the pet type
     */
    public @Nonnull String getTypeName()
    {
        return typeName;
    }

    /**
     * Given a Class object for a pet entity, returns the corresponding PetTypes enum value, or null if no such pet is registered.
     *
     * @param petClass the Class object for the pet entity
     * @return the PetTypes enum value for the given pet class, or null if no such pet is registered
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
     * Given a human-readable pet type name, such as "Wolf", "Fox", or "Axolotl", returns the corresponding PetTypes enum value, or
     * null if no such pet is registered.
     * 
     * @param petString the human-readable pet type name
     * @return the PetTypes enum value for the given pet name, or null if no such pet is registered
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
     * Checks if the given item stack is a food item for a pet.
     * <p>This method checks if the item in the given item stack is equal to any of the food items registered in the
     * <code>PetTypes</code> enum. If it is, the method returns <code>true</code>; otherwise, it returns <code>false</code>.
     * <p>This method is used by the <code>EatFromInventoryHealGoal</code> to check if a pet has food in its inventory.
     * 
     * @param stack the item stack to check
     * @return whether the item in the given item stack is a food item for a pet
     */
    public static boolean isPetFood(ItemStack stack)
    {
        for (PetTypes type : PetTypes.values())
        {
            if (type.getTrainingItem().getItem().equals(stack.getItem()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a PetData object for the given pet type, using the given CompoundTag to initialize it.
     * 
     * @param type     the pet type to create a PetData for
     * @param compound the CompoundTag containing the data with which to initialize the PetData
     * @return the created PetData object
     */
    public static PetData<?> createPetDataForType(PetTypes type, CompoundTag compound)
    {
        PetData<?> pet = switch (type)
        {
            case PetTypes.WOLF -> new PetData<PetWolf>(null, compound);
            case PetTypes.FOX -> new PetData<PetFox>(null, compound);
            case PetTypes.AXOLOTL -> new PetData<PetAxolotl>(null, compound);
            default -> throw new IllegalArgumentException("Unknown pet type: " + type.getTypeName());
        };

        return pet;
    }

    /**
     * Checks if the given Enum value represents a pet type (as opposed to a non-pet animal)
     * 
     * @return whether the given Enum value represents a pet type
     */
    public boolean isPet()
    {
        return isPet;
    }
}
