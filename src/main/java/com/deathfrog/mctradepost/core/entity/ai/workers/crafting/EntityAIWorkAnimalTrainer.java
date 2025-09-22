package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.CRAFT;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GATHERING_REQUIRED_MATERIALS;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DUMPING;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetHelper;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.ItemHandlerHelpers;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.deathfrog.mctradepost.core.colony.jobs.JobAnimalTrainer;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.util.AdvancementUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EntityAIWorkAnimalTrainer extends AbstractEntityAICrafting<JobAnimalTrainer, BuildingPetshop>
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String PETS_TRAINED = "pets_trained";
    public static final String PETS_FED = "pets_fed";
    public static final String WORKSTATIONS_EMPTIED = "workstations_emptied";

    public static final int PETFOOD_SIZE = 8;

    private List<ITradePostPet> hungryPets = new ArrayList<>();
    private List<ITradePostPet> petsWithFullishWorksites = new ArrayList<>();
    private ITradePostPet currentTargetPet = null;

    public enum AnimalTrainerStates implements IAIState
    {
        RAISE_PET,
        FEED_PET,
        GATHER_PET_FOOD,
        EMPTY_WORKSITES;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    /**
     * Crafting icon
     */
    private final static VisibleCitizenStatus CRAFTING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/animaltrainer.png"),
            "com.mctradepost.gui.visiblestatus.animaltrainer");

    @SuppressWarnings("unchecked")
    public EntityAIWorkAnimalTrainer(@NotNull JobAnimalTrainer job)
    {
        super(job);
        super.registerTargets(new AITarget<IAIState>(IDLE, START_WORKING, 2),
            new AITarget<IAIState>(START_WORKING, DECIDE, 2),
            new AITarget<IAIState>(DECIDE, this::decide, 2),
            new AITarget<IAIState>(AnimalTrainerStates.RAISE_PET, this::raisePet, 2),
            new AITarget<IAIState>(AnimalTrainerStates.GATHER_PET_FOOD, this::gatherPetFood, 50),
            new AITarget<IAIState>(AnimalTrainerStates.FEED_PET, this::feedPet, 50),
            new AITarget<IAIState>(AnimalTrainerStates.EMPTY_WORKSITES, this::emptyWorkSites, 50),
            new AITarget<IAIState>(DUMPING, this::dump, 50),
            new AITarget<IAIState>(CRAFT, this::craft, 50));
        worker.setCanPickUpLoot(true);
    }

    /**
     * The building class this AI is intended to be used with.
     * 
     * @return the building class
     */
    @Override
    public Class<BuildingPetshop> getExpectedBuildingClass()
    {
        return BuildingPetshop.class;
    }

    /**
     * Decides what the AnimalTrainer AI should do next.
     * <p>
     * If there is room for more pets, the AI will attempt to raise a new pet.
     * Otherwise, the AI will check if any of the existing pets are hungry and
     * need to be fed. If there are hungry pets, the AI will attempt to gather
     * pet food. If there are no hungry pets, the AI will fall back to the
     * superclass's decision making.
     * <p>
     * @return the next state to transition to
     */
    @Override
    public IAIState decide()
    {

        hungryPets.clear();
        petsWithFullishWorksites.clear();

        for (ITradePostPet pet : building.getPets())
        {
            if (pet instanceof Animal animal)
            {
                if (animal.getHealth() < animal.getMaxHealth())
                {
                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Pet {} needs to be fed.", animal));
                    hungryPets.add(pet);
                }
            }

            if (pet.getWorkLocation() != null)
            {
                Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(worker.level(), pet.getWorkLocation(), null);
                if (!optProvider.isPresent())
                {
                    continue;
                }

                IItemHandlerCapProvider chestHandlerOpt = optProvider.get();
                int howFull = MCTPInventoryUtils.filledSlotsPercentage(chestHandlerOpt);

                if (howFull >= 40)
                {
                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Inventory needs to be emptied.", pet.getWorkLocation()));
                    petsWithFullishWorksites.add(pet);
                }
            }
        }

        if (hungryPets.size() > 0)
        {
            return AnimalTrainerStates.GATHER_PET_FOOD;
        }

        if (petsWithFullishWorksites.size() > 0)
        {
            return AnimalTrainerStates.EMPTY_WORKSITES;
        }

        if (building.getPets().size() < (MCTPConfig.petsPerLevel.get() * building.getBuildingLevel()))
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Room for pets. I should raise one!"));
            return AnimalTrainerStates.RAISE_PET;
        }

        return super.decide();
    }


    /**
     * Dump the inventory into the warehouse.
     *
     * @return the next state to go to.
     */
    private IAIState dump()
    {
        final @Nullable IWareHouse warehouse = building.getColony().getBuildingManager().getClosestWarehouseInColony(worker.getOnPos());
        if (warehouse == null)
        {
            return START_WORKING;
        }

        if (!walkToBuilding(warehouse))
        {
            setDelay(WALK_DELAY);
            return DUMPING;
        }

        warehouse.getTileEntity().dumpInventoryIntoWareHouse(worker.getInventoryCitizen());

        return START_WORKING;
    }

    /**
     * If we have hungry pets, find the food they want.  First in the worker inventory,
     * then in the building inventory and ordered from the warehouse if necessary.
     * @return the next AI state to transition to.
     */
    public IAIState gatherPetFood()
    {
        if (hungryPets.isEmpty())
        {
            return DECIDE;
        }

        needsCurrently = null;

        for (ITradePostPet pet : hungryPets)
        {

            if (pet.getPetData() == null)
            {
                continue;
            }
                    
            currentTargetPet = pet;

            ItemStorage food = new ItemStorage(PetTypes.foodForPet(currentTargetPet.getPetData().getAnimal().getClass()), PETFOOD_SIZE);

            int onHand = InventoryUtils.getItemCountInItemHandler(worker.getCitizenData().getInventory(), stack -> Objects.equals(new ItemStorage(stack), food));

            if (onHand < PETFOOD_SIZE)
            {
                int inStock = InventoryUtils.getItemCountInItemHandler(building.getItemHandlerCap(), stack -> Objects.equals(new ItemStorage(stack), food));

                if (inStock >= PETFOOD_SIZE)
                {
                    needsCurrently = new Tuple<>(stack -> Objects.equals(stack.getItem(), food.getItem()), PETFOOD_SIZE);
                }
                else
                {
                    boolean alreadyRequested = false;
                    final ImmutableList<IRequest<? extends Stack>> openRequests = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
                    for (final IRequest<? extends Stack> request : openRequests)
                    {
                        if (request.getRequest().getStack().equals(food.getItemStack()))
                        {
                            alreadyRequested = true;
                            break;
                        }
                    }

                    if (!alreadyRequested)
                    {
                        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Requesting {} from warehouse.", food.getItemStack().getHoverName()));

                        worker.getCitizenData().createRequestAsync(new Stack(food.getItemStack(), PETFOOD_SIZE, 2));
                    }
                }
            }
            else
            {
                return AnimalTrainerStates.FEED_PET;
            }
        }


        if (needsCurrently != null)
        {
            return GATHERING_REQUIRED_MATERIALS;
        }

        return DECIDE;
    }

    /**
     * After picking up the required food, the next AI state to transition to is AnimalTrainerStates.FEED_PET.
     * This state is responsible for feeding the injured pet.
     */
    @Override
    public IAIState getStateAfterPickUp()
    {
        return AnimalTrainerStates.FEED_PET;
    }


    /**
     * Provide the pet with food at its working location if it is injured. 
     * @return the next AI state to transition to.
     */
    public IAIState feedPet()
    {
        if (currentTargetPet == null || currentTargetPet.getWorkLocation() == null)
        {
            currentTargetPet = null;
            return DECIDE;
        }

        if (!walkToSafePos(currentTargetPet.getWorkLocation()))
        {
            setDelay(2);
            return getState();
        }

        BlockEntity be = worker.level().getBlockEntity(currentTargetPet.getWorkLocation());
        
        if (be instanceof Container) 
        {
            Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(worker.level(), currentTargetPet.getWorkLocation(), null);
            if (!optProvider.isPresent())
            {
                currentTargetPet = null;
                return DECIDE;
            }

            IItemHandlerCapProvider chestHandlerOpt = optProvider.get();
            unloadWorkLocation(currentTargetPet);
            
            // Put the food into the work location
            ItemStorage food = new ItemStorage(PetTypes.foodForPet(currentTargetPet.getPetData().getAnimal().getClass()), PETFOOD_SIZE);
            int foodslot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(worker, stack -> Objects.equals(new ItemStorage(stack), food));

            if (foodslot >= 0)
            {
                InventoryUtils.transferItemStackIntoNextBestSlotFromProvider(worker, foodslot, chestHandlerOpt.getItemHandlerCap());

                StatsUtil.trackStat(building, PETS_FED, 1);
                worker.getCitizenExperienceHandler().addExperience(1.0);
                be.setChanged();
            }

        }

        return DUMPING;
    }


    /**
     * Walk to a pet's work location that has a full or almost full inventory and unload it. If the pet's inventory is not full or almost full, do nothing.
     * @return the next AI state to transition to, which is either DUMPING or DECIDE.
     */
    public IAIState emptyWorkSites()
    {
        if (petsWithFullishWorksites.isEmpty())
        {
            return DECIDE;
        }

        for (ITradePostPet pet : petsWithFullishWorksites)
        {
            if (pet.getWorkLocation() == null || BlockPos.ZERO.equals(pet.getWorkLocation()))
            {
                continue;
            }

            if (!walkToSafePos(pet.getWorkLocation()))
            {
                setDelay(2);
                return getState();
            }

            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Unloading work station at {}.", pet.getWorkLocation()));
            unloadWorkLocation(pet);
        }

        return DUMPING;
        
    }


    /**
     * Unload everything from the work location into the worker's inventory.
     * If the worker's inventory is full, stop unloading.
     */
    private void unloadWorkLocation(ITradePostPet pet)
    {
        BlockEntity be = worker.level().getBlockEntity(pet.getWorkLocation());
        Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(worker.level(), pet.getWorkLocation(), null);
        if (!optProvider.isPresent())
        {
            return;
        }

        IItemHandlerCapProvider chestHandlerOpt = optProvider.get();

        // Unload everything in the work location.
        for (int i = 0; i < chestHandlerOpt.getItemHandlerCap().getSlots(); i++)
        {
            ItemStack stack = chestHandlerOpt.getItemHandlerCap().getStackInSlot(i);
            if (!stack.isEmpty())
            {
                boolean canTake = InventoryUtils.transferItemStackIntoNextBestSlotFromProvider(chestHandlerOpt, i, worker.getItemHandlerCap());

                if (!canTake)
                {
                    break;
                }
            }
        }

        be.setChanged();

        StatsUtil.trackStat(building, WORKSTATIONS_EMPTIED, 1);
        worker.getCitizenExperienceHandler().addExperience(1.0);
    
    }


    /**
     * Decides whether to raise a pet or not.
     * 
     * @return the next AI state to transition to.
     */
    public IAIState raisePet()
    {
        if (!walkToBuilding())
        {
            setDelay(2);
            return getState();
        }

        for (PetTypes type : PetTypes.values())
        {
            ItemStack trainingStack = type.getTrainingItem();

            int count = MCTPInventoryUtils.combinedInventoryCount(building, new ItemStorage(trainingStack));
            if (count < trainingStack.getCount())
            {
                continue;
            }

            MCTPInventoryUtils.combinedInventoryRemoval(building, new ItemStorage(trainingStack), trainingStack.getCount());
            
            try
            {
                // Instantiate the pet
                Animal pet = type.getPetClass()
                    .getConstructor(EntityType.class, Level.class)
                    .newInstance(type.getEntityType(), building.getColony().getWorld());

                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER,
                    () -> LOGGER.info("Raising a pet: {}", type.getPetClass().getSimpleName()));

                if (pet instanceof ITradePostPet)
                {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    PetHelper helper = new PetHelper(pet);
                    helper.doRegistration(building);
                }
                
                AdvancementUtils.TriggerAdvancementPlayersForColony(building.getColony(),
                        player -> MCTPAdvancementTriggers.PET_TRAINED.get().trigger(player));

                StatsUtil.trackStat(building, PETS_TRAINED, 1);
                worker.getCitizenExperienceHandler().addExperience(2.0);
                
                break;
            }
            catch (ReflectiveOperationException e)
            {
                MCTradePostMod.LOGGER.error("Failed to instantiate pet of type: " + type.name(), e);
            }
        }

        return DECIDE;
    }

    /**
     * Perform the crafting operation for the animal trainer. This method is called by the {@link AbstractEntityAICrafting} class when
     * the AI is in the CRAFT state. This method is responsible for setting the visible status of the citizen to indicate crafting
     * activity and then calling the superclass's {@link #craft()} method to perform the actual crafting operation.
     * <p>The superclass's method will update the building's statistics accordingly and handle the crafting process. This method will
     * be called recursively until all items have been crafted or the AI is interrupted.
     * 
     * @return the next AI state to transition to.
     */
    @Override
    public IAIState craft()
    {
        worker.getCitizenData().setVisibleStatus(CRAFTING);
        return super.craft();
    }
}
