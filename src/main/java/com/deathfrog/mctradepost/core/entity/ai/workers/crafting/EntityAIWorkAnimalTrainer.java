package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.CRAFT;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DUMPING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import java.util.Set;

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
import com.minecolonies.api.util.constant.ColonyConstants;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.util.AdvancementUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EntityAIWorkAnimalTrainer extends AbstractEntityAICrafting<JobAnimalTrainer, BuildingPetshop>
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String PETS_TRAINED = "pets_trained";
    public static final String PETS_FED = "pets_fed";
    public static final String WORKSTATIONS_EMPTIED = "workstations_emptied";
    public static final double EMPTY_CHANCE = .10;
    public static final double FEEDING_CHANCE = .25;

    public static final int PETCHECK_FREQUENCY = 20;
    public static final int PETFOOD_SIZE = 8;

    private List<ITradePostPet> hungryPets = new ArrayList<>();
    private Map<BlockPos, Long> workStations = new HashMap<BlockPos, Long>();

    private ITradePostPet currentTargetPet = null;
    private int petCheckCooldown = PETCHECK_FREQUENCY;
    private BlockPos currentWorkLocation = null;

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

        if (petCheckCooldown-- <= 0)
        {
            checkOnPets();
            petCheckCooldown = PETCHECK_FREQUENCY;
        }

        if (hungryPets.size() > 0 && ColonyConstants.rand.nextDouble() < FEEDING_CHANCE)
        {
            return AnimalTrainerStates.GATHER_PET_FOOD;
        }

        if (ColonyConstants.rand.nextDouble() < EMPTY_CHANCE)
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
     * Iterates over all the pets in the building and checks if they are hungry
     * or need to be fed. If a pet is hungry, it is added to the
     * {@link #hungryPets} set. Additionally, it checks if each pet is
     * currently working and if so, adds their work location to the
     * {@link #workStations} map. Finally, it removes any entries from
     * the {@link #workStations} map that are not in the current set of
     * work locations and adds any missing entries with a default value of 0L.
     */
    private void checkOnPets()
    {
        hungryPets.clear();

        Set<BlockPos> currentWorkStations = new HashSet<>();

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

            if (pet.getWorkLocation() != null && !BlockPos.ZERO.equals(pet.getWorkLocation()))
            {
                currentWorkStations.add(pet.getWorkLocation());
            }
        }

        // Remove anything not in the current set
        workStations.keySet().retainAll(currentWorkStations);

        // Add any missing entries with default 0L
        for (BlockPos pos : currentWorkStations) 
        {
            workStations.putIfAbsent(pos, 0L);
        }
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

        if (!walkToBuilding())
        {
            return getState();
        }

        boolean hasFoodToDistribute = false;

        for (ITradePostPet pet : hungryPets)
        {

            if (pet.getPetData() == null)
            {
                continue;
            }
                    
            currentTargetPet = pet;

            Item food = PetTypes.foodForPet(currentTargetPet.getPetData().getAnimal().getClass());

            int onHand = InventoryUtils.getItemCountInItemHandler(worker.getCitizenData().getInventory(), food);

            if (onHand < PETFOOD_SIZE)
            {
                int inStock = InventoryUtils.getItemCountInItemHandler(building.getItemHandlerCap(), food);

                if (inStock >= PETFOOD_SIZE)
                {
                     if (this.checkAndTransferFromHut(new ItemStack(food, PETFOOD_SIZE)))
                     {
                         hasFoodToDistribute = true;
                     }
                }
                else
                {
                    boolean alreadyRequested = false;
                    final ImmutableList<IRequest<? extends Stack>> openRequests = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
                    for (final IRequest<? extends Stack> request : openRequests)
                    {
                        if (request.getRequest().getStack().is(food))
                        {
                            alreadyRequested = true;
                            break;
                        }
                    }

                    if (!alreadyRequested)
                    {
                        final ItemStack requestStack = new ItemStack(food);
                        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Requesting {} from warehouse.", requestStack.getHoverName()));

                        worker.getCitizenData().createRequestAsync(new Stack(requestStack, PETFOOD_SIZE, 2));
                    }
                }
            }
            else
            {
                hasFoodToDistribute = true;
            }
        }

        if (hasFoodToDistribute)
        {
            return AnimalTrainerStates.FEED_PET;
        }

        return DECIDE;
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

        Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(worker.level(), currentTargetPet.getWorkLocation(), null);
        if (!optProvider.isPresent())
        {
            currentTargetPet = null;
            return DECIDE;
        }

        if (!walkToSafePos(currentTargetPet.getWorkLocation()))
        {
            setDelay(2);
            return getState();
        }
    
        IItemHandlerCapProvider chestHandlerOpt = optProvider.get();
        unloadWorkLocation(currentTargetPet.getWorkLocation());
        
        // Put the food into the work location
        ItemStorage food = new ItemStorage(PetTypes.foodForPet(currentTargetPet.getPetData().getAnimal().getClass()), PETFOOD_SIZE);
        int foodslot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(worker, stack -> Objects.equals(new ItemStorage(stack), food));

        if (foodslot >= 0)
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Delivering pet food to {}.", currentTargetPet.getWorkLocation()));
            InventoryUtils.transferItemStackIntoNextBestSlotFromProvider(worker, foodslot, chestHandlerOpt.getItemHandlerCap());

            StatsUtil.trackStat(building, PETS_FED, 1);
            worker.getCitizenExperienceHandler().addExperience(1.0);

            BlockEntity be = worker.level().getBlockEntity(currentTargetPet.getWorkLocation());
            if (be != null)
            {
                be.setChanged();
            } 
        }

        currentTargetPet = null;

        return DUMPING;
    }


    /**
     * Walk to a pet's work location and unload it.
     * @return the next AI state to transition to, which is either DUMPING or DECIDE.
     */
    public IAIState emptyWorkSites()
    {
        if (currentWorkLocation == null)
        {
            currentWorkLocation = getOldestUnload();
            if (currentWorkLocation == null) return DECIDE;
        }

        if (!walkToSafePos(currentWorkLocation))
        {
            setDelay(2);
            return getState();
        }

        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Unloading work station at {}.", currentWorkLocation));
        unloadWorkLocation(currentWorkLocation);
        workStations.put(currentWorkLocation, worker.level().getGameTime());

        currentWorkLocation = getOldestUnload();

        return DUMPING;
        
    }


    /**
     * Finds the oldest BlockPos in the workStations map, which is the key with the smallest value (oldest timestamp).
     * If the map is empty, returns null.
     *
     * @return the oldest BlockPos in the workStations map, or null if the map is empty.
     */
    @Nullable
    private BlockPos getOldestUnload()
    {
        if (workStations.isEmpty()) {
            return null;
        }

        long now = worker.level().getGameTime();
        long threshold = now - (5 * 60 * 20);           // 5 minutes in ticks (20 ticks per second)

        return workStations.entrySet()
            .stream()
            .filter(e -> e.getValue() <= threshold)     // only those untouched for >= 5 min
            .min(Map.Entry.comparingByValue())          // oldest timestamp among them
            .map(Map.Entry::getKey)
            .orElse(null);
    }


    /**
     * Unload everything from the work location into the worker's inventory.
     * If the worker's inventory is full, stop unloading.
     */
    private void unloadWorkLocation(BlockPos workLocation)
    {

        Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(worker.level(), workLocation, null);
        if (optProvider.isEmpty())
        {
            return;
        }

        IItemHandlerCapProvider chestHandlerOpt = optProvider.get();

        // Unload everything in the work location EXCEPT pet food.
        for (int i = 0; i < chestHandlerOpt.getItemHandlerCap().getSlots(); i++)
        {
            ItemStack stack = chestHandlerOpt.getItemHandlerCap().getStackInSlot(i);
            if (!stack.isEmpty() && !PetTypes.isPetFood(stack))
            {
                boolean canTake = InventoryUtils.transferItemStackIntoNextBestSlotFromProvider(chestHandlerOpt, i, worker.getItemHandlerCap());

                if (!canTake)
                {
                    break;
                }
            }
        }
        
        BlockEntity be = worker.level().getBlockEntity(workLocation);
        if (be != null)
        {
            be.setChanged();
        }

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
