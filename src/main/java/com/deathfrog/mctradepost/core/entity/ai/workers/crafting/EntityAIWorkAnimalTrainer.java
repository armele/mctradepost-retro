package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.CRAFT;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DUMPING;

import java.util.ArrayList;
import java.util.Collection;
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
import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.ItemHandlerHelpers;
import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingEconModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.deathfrog.mctradepost.core.colony.jobs.JobAnimalTrainer;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.constant.ColonyConstants;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.util.AdvancementUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

public class EntityAIWorkAnimalTrainer extends AbstractEntityAICrafting<JobAnimalTrainer, BuildingPetshop>
{

    /**
     * How many times the AI should attempt to find an allegedly delivered item before giving up on it.
     */
    protected int deliverAcceptanceCounter = 0;
    protected static final int SOFT_DELIVERY_ACCEPTANCE_COUNTER = 10;
    protected static final int HARD_DELIVERY_ACCEPTANCE_COUNTER = 20;

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String PETS_TRAINED = "pets_trained";
    public static final String OTHER_ACQUIRED = "other_acquired";
    public static final String PETS_FED = "pets_fed";
    public static final String WORKSTATIONS_EMPTIED = "workstations_emptied";
    public static final double EMPTY_CHANCE = .10;
    public static final double FEEDING_CHANCE = .25;

    public static final int PETCHECK_FREQUENCY = 20;
    public static final int RAISE_FREQUENCY = 20;
    public static final int PETFOOD_SIZE = 8;

    private List<ITradePostPet> hungryPets = new ArrayList<>();
    private Map<BlockPos, Long> workStations = new HashMap<BlockPos, Long>();

    private ITradePostPet currentTargetPet = null;
    private int petCheckCooldown = PETCHECK_FREQUENCY;
    protected int raiseCooldown = RAISE_FREQUENCY;
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


    @Override
    public IAIState afterRequestPickUp() 
    {
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} - Animal Trainer afterRequestPickUp: {}", building.getColony().getID()));
        return super.afterRequestPickUp();
    }

    /**
     * Waits for the AI to receive new requests from the building. If the AI needs an item, but there are no open requests, the AI will
     * transition to the DECIDE state to decide what to do next. If the AI does not need an item, the AI will transition back to the
     * IDLE state.
     * 
     * @return The next AI state to transition to.
     */
    @Override
    protected @NotNull IAIState waitForRequests() 
    {
        IAIState state = super.waitForRequests();

        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} - Animal Trainer waitForRequests() has open sync request? {} Has completed reqeusts to pick up? {} state: {}. deliverAcceptanceCounter: {}", 
            building.getColony().getID(), building.hasOpenSyncRequest(worker.getCitizenData()), building.hasCitizenCompletedRequestsToPickup(worker.getCitizenData()), state, deliverAcceptanceCounter));

        if (state != AIWorkerState.NEEDS_ITEM) 
        {
            deliverAcceptanceCounter = 0;
            return state;
        }

        if (deliverAcceptanceCounter++ < SOFT_DELIVERY_ACCEPTANCE_COUNTER || building.hasOpenSyncRequest(worker.getCitizenData())) 
        {
            return state;
        }

        boolean clearedSomething = cleanStuckRequests(deliverAcceptanceCounter);

        if (clearedSomething)
        {
            deliverAcceptanceCounter = 0;
        }

        // If we didn't clear anything, staying in NEEDS_ITEM is more honest than DECIDE.
        return clearedSomething ? AIWorkerState.DECIDE : AIWorkerState.NEEDS_ITEM;
    }

    /**
     * Cleans stuck requests from the building's request queue that are not deliverable anymore (for example, if a request is async, but the
     * citizen is not available to pick it up anymore).
     * 
     * @return true if any requests were cleared, false otherwise.
     */
    protected boolean cleanStuckRequests(int tryCounter)
    {
        ICitizenData citizen = worker.getCitizenData();
        Collection<IRequest<?>> completed = building.getCompletedRequestsOfCitizenOrBuilding(citizen);

        boolean cleared = false;

        // Copy IDs to avoid concurrent modification surprises.
        List<IRequest<?>> snapshot = new ArrayList<>(completed);

        for (IRequest<?> request : snapshot)
        {
            IToken<?> id = request.getId();
            if (!request.canBeDelivered() || citizen.isRequestAsync(id) || tryCounter > HARD_DELIVERY_ACCEPTANCE_COUNTER)
            {

                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} - Animal Trainer cleanStuckRequests() clearing stuck request: {}", 
                    building.getColony().getID(), request.getLongDisplayString()));

                building.markRequestAsAccepted(citizen, id);
                cleared = true;
            }
        }

        return cleared;
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

        ImmutableList<ITradePostPet> pets = building.getPets();
        if (pets != null && pets.size() == 0)
        {
            raiseCooldown = RAISE_FREQUENCY;
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} - Animal Trainer: No pets exist.  Let's try to raise one.", building.getColony().getID()));
            return AnimalTrainerStates.RAISE_PET;
        }

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

        if (raiseCooldown-- <= 0)
        {
            raiseCooldown = RAISE_FREQUENCY;
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} - Animal Trainer: Check on requests to raise pets.", building.getColony().getID()));
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

            BlockPos pwBlockPos = pet.getWorkLocation();

            if (pwBlockPos != null && !BlockPos.ZERO.equals(pwBlockPos))
            {
                if (!isHaulerWorkLocation(pwBlockPos))
                {
                    currentWorkStations.add(pwBlockPos);
                }
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
        final @Nullable IWareHouse warehouse = building.getColony().getServerBuildingManager().getClosestWarehouseInColony(worker.getOnPos());
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

            if (food == null)
            {
                continue;
            }

            if (hasEnoughFoodAtWorkLocation(pet, food))
            {
                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Skipping pet food delivery for {}; {} already has at least {} food at {}.",
                    pet.getPetData().getAnimal(), food, PETFOOD_SIZE, pet.getWorkLocation()));
                continue;
            }

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

        Level level = worker.level();

        if (level == null)
        {
            currentTargetPet = null;
            return DECIDE;
        }

        BlockPos currentTargetPetWorkLocation =  currentTargetPet.getWorkLocation();

        if (currentTargetPetWorkLocation == null || BlockPos.ZERO.equals(currentTargetPetWorkLocation))
        {
            currentTargetPet = null;
            return DECIDE;
        }

        Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(level, currentTargetPetWorkLocation, null);
        
        if (!optProvider.isPresent())
        {
            currentTargetPet = null;
            return DECIDE;
        }

        if (!walkToSafePos(currentTargetPetWorkLocation))
        {
            setDelay(2);
            return getState();
        }
    
        IItemHandlerCapProvider chestHandlerOpt = optProvider.get();
        unloadWorkLocation(currentTargetPetWorkLocation);
        
        // Put the food into the work location
        Item foodItem = PetTypes.foodForPet(currentTargetPet.getPetData().getAnimal().getClass());
        if (foodItem == null)
        {
            currentTargetPet = null;
            return DECIDE;
        }

        ItemStorage food = new ItemStorage(foodItem, PETFOOD_SIZE);
        int foodAlreadyPresent = getFoodCountAtWorkLocation(currentTargetPetWorkLocation, foodItem);
        int foodToDeliver = Math.max(0, PETFOOD_SIZE - foodAlreadyPresent);

        if (foodToDeliver <= 0)
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Skipping pet food delivery to {}; {} food already staged.",
                currentTargetPetWorkLocation, foodAlreadyPresent));
            currentTargetPet = null;
            return DECIDE;
        }

        int foodslot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(worker, stack -> Objects.equals(new ItemStorage(stack), food));
        IItemHandler chestHandler = chestHandlerOpt.getItemHandlerCap();

        if (foodslot >= 0 && chestHandler != null)
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Delivering pet food to {}.", currentTargetPet.getWorkLocation()));
            
            boolean delivered = transferFoodToWorkLocation(foodslot, chestHandler, foodToDeliver);

            if (delivered)
            {
                StatsUtil.trackStat(building, PETS_FED, 1);
                worker.getCitizenExperienceHandler().addExperience(1.0);
            }

            BlockEntity be = worker.level().getBlockEntity(currentTargetPetWorkLocation);
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
        }

        BlockPos localCurrentWorkPos = currentWorkLocation;

        if (localCurrentWorkPos == null)
        {
            return DECIDE;
        }

        if (!walkToSafePos(localCurrentWorkPos))
        {
            setDelay(2);
            return getState();
        }

        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Unloading work station at {}.", localCurrentWorkPos));
        unloadWorkLocation(localCurrentWorkPos);
        workStations.put(localCurrentWorkPos, worker.level().getGameTime());

        currentWorkLocation = getOldestUnload();

        return DUMPING;
        
    }


    /**
     * Finds the oldest BlockPos in the workStations map, which is the key with the smallest value (oldest timestamp).
     * If the map is empty, returns null.
     *
     * @return the oldest BlockPos in the workStations map, or null if the map is empty.
     */
    @SuppressWarnings("null")
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
            .filter(e -> !isHaulerWorkLocation(e.getKey()))
            .filter(e -> e.getValue() <= threshold)     // only those untouched for >= 5 min
            .min(Map.Entry.comparingByValue())          // oldest timestamp among them
            .map(Map.Entry::getKey)
            .orElse(null);
    }


    /**
     * Unload everything from the work location into the worker's inventory.
     * If the worker's inventory is full, stop unloading.
     */
    private void unloadWorkLocation(@Nonnull BlockPos workLocation)
    {
        Level level = worker.level();

        if (level == null)
        {
            return;
        }

        if (isHaulerWorkLocation(workLocation))
        {
            return;
        }

        
        Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(level, workLocation, null);
        if (optProvider.isEmpty())
        {
            return;
        }

        IItemHandlerCapProvider chestHandlerOpt = optProvider.get();
        Item expectedFood = getAssignedPetFood(workLocation);
        int keptFood = 0;

        // Unload everything in the work location except one staged batch of the assigned pet's food.
        for (int i = 0; i < chestHandlerOpt.getItemHandlerCap().getSlots(); i++)
        {
            ItemStack stack = chestHandlerOpt.getItemHandlerCap().getStackInSlot(i);
            if (stack.isEmpty())
            {
                continue;
            }

            if (expectedFood != null && stack.is(expectedFood))
            {
                int foodToKeepFromStack = Math.max(0, PETFOOD_SIZE - keptFood);
                if (foodToKeepFromStack >= stack.getCount())
                {
                    keptFood += stack.getCount();
                    continue;
                }

                keptFood += foodToKeepFromStack;
                int excessFood = stack.getCount() - foodToKeepFromStack;
                if (!InventoryUtils.transferXOfItemStackIntoNextFreeSlotInItemHandler(chestHandlerOpt.getItemHandlerCap(), i, excessFood, worker.getItemHandlerCap()))
                {
                    break;
                }
                continue;
            }

            boolean canTake = InventoryUtils.transferItemStackIntoNextBestSlotFromProvider(chestHandlerOpt, i, worker.getItemHandlerCap());

            if (!canTake)
            {
                break;
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

    private boolean isHaulerWorkLocation(@Nonnull BlockPos workLocation)
    {
        Level level = worker.level();
        return level != null && PetRoles.HAULING.equals(PetData.roleFromPosition(level, workLocation));
    }

    /**
     * Checks whether the pet's assigned work location already has a full staged batch of the requested food.
     *
     * @param pet the pet whose work location should be inspected.
     * @param food the food item expected by the pet.
     * @return true when the work location contains at least {@link #PETFOOD_SIZE} matching food items.
     */
    private boolean hasEnoughFoodAtWorkLocation(@Nonnull ITradePostPet pet, @Nonnull Item food)
    {
        BlockPos workLocation = pet.getWorkLocation();
        return workLocation != null && !BlockPos.ZERO.equals(workLocation) && getFoodCountAtWorkLocation(workLocation, food) >= PETFOOD_SIZE;
    }

    /**
     * Counts matching food items currently staged in a pet work location inventory.
     *
     * @param workLocation the block position of the pet work location.
     * @param food the food item to count, or null when no valid pet food is known.
     * @return the number of matching food items present, or 0 if the location has no accessible inventory.
     */
    private int getFoodCountAtWorkLocation(@Nonnull BlockPos workLocation, @Nullable Item food)
    {
        if (food == null)
        {
            return 0;
        }

        Level level = worker.level();
        if (level == null)
        {
            return 0;
        }

        Optional<IItemHandlerCapProvider> optProvider = ItemHandlerHelpers.getProvider(level, workLocation, null);
        if (optProvider.isEmpty())
        {
            return 0;
        }

        return InventoryUtils.getItemCountInItemHandler(optProvider.get().getItemHandlerCap(), food);
    }

    /**
     * Resolves the food item expected by the pet currently assigned to a work location.
     *
     * @param workLocation the work location to match against the building's registered pets.
     * @return the assigned pet's food item, or null when no assigned pet or food mapping can be found.
     */
    @Nullable
    private Item getAssignedPetFood(@Nonnull BlockPos workLocation)
    {
        for (ITradePostPet pet : building.getPets())
        {
            if (pet == null || !workLocation.equals(pet.getWorkLocation()) || pet.getPetData() == null)
            {
                continue;
            }

            return PetTypes.foodForPet(pet.getPetData().getAnimal().getClass());
        }

        return null;
    }

    /**
     * Transfers up to the requested number of food items from the trainer inventory to a work location inventory.
     *
     * @param workerSlot the trainer inventory slot containing the food.
     * @param targetInventory the work location inventory receiving the food.
     * @param maxCount the maximum number of items to transfer.
     * @return true if the requested amount was transferred.
     */
    private boolean transferFoodToWorkLocation(int workerSlot, @Nonnull IItemHandler targetInventory, int maxCount)
    {
        return InventoryUtils.transferXOfItemStackIntoNextFreeSlotInItemHandler(worker.getItemHandlerCap(), workerSlot, maxCount, targetInventory);
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

        BuildingEconModule econ = BuildingMarketplace.getBestEconModuleFor(building);
        if (econ == null)
        {
            int complaints = job.tickNoMarketplace();

            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} animal trainer: Has no access to a marketplace ({} tries).", building.getColony().getID(), complaints));

            if (job.checkNoMarketplaceInteraction())
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_MARKETPLACE), ChatPriority.BLOCKING));
            }

            return DECIDE;
        }

        job.resetNoMarketplaceCounter();

        int coinValue = MCTPConfig.tradeCoinValue.get();

        List<PetTypes> activePetTypes = new ArrayList<PetTypes>();
        int husbandryResearch = (int) building.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.HUSBANDRY);  

        // Make the list of animals to raise smart enough to be research-aware.
        for (PetTypes type : PetTypes.values())
        {
            if (type.isPet() == true || husbandryResearch > 0)
            {
                activePetTypes.add(type);
            }
        }

        for (PetTypes petType : activePetTypes)
        {
            ItemStack trainingStack = petType.getTrainingItem();

            int count = MCTPInventoryUtils.combinedInventoryCount(building, new ItemStorage(trainingStack));
            if (count < trainingStack.getCount())
            {
                continue;
            }
            
            int costNeeded = petType.getCoinCost() * coinValue;
            if (econ.getTotalBalance() < costNeeded)
            {
                int complaints = job.tickNSF();

                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} animal trainer: Has insufficient funds ({} tries).", building.getColony().getID(), complaints));

                if (job.checkNSF())
                {
                    worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.ANIMAL_NSF), ChatPriority.BLOCKING));
                }
                continue;
            }

            job.resetNSFCounter();

            if (petType.isPet())
            {
                if (building.getPets().size() >= (MCTPConfig.petsPerLevel.get() * building.getBuildingLevel()))
                {
                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} animal trainer: No room for more pets at this time - skipping request for {}.", building.getColony().getID(), petType.getTypeName()));

                    continue;
                }
            }

            if (!econ.tryWithdraw(costNeeded))
            {
                int complaints = job.tickNSF();

                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Colony {} animal trainer: Has insufficient funds ({} tries).", building.getColony().getID(), complaints));

                if (job.checkNSF())
                {
                    worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.ANIMAL_NSF), ChatPriority.BLOCKING));
                }
                continue;
            }

            MCTPInventoryUtils.combinedInventoryRemoval(building, new ItemStorage(trainingStack), trainingStack.getCount());
            
            try
            {
                // Instantiate the pet
                Animal pet = petType.getPetClass()
                    .getConstructor(EntityType.class, Level.class)
                    .newInstance(petType.getEntityType(), building.getColony().getWorld());

                TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER,
                    () -> LOGGER.info("Raising a pet: {}", petType.getPetClass().getSimpleName()));

                if (pet instanceof ITradePostPet)
                {
                    AdvancementUtils.TriggerAdvancementPlayersForColony(building.getColony(),
                            player -> MCTPAdvancementTriggers.PET_TRAINED.get().trigger(NullnessBridge.assumeNonnull(player)));

                    StatsUtil.trackStat(building, PETS_TRAINED, 1);
                    worker.getCitizenExperienceHandler().addExperience(2.0);

                    @SuppressWarnings({"rawtypes", "unchecked"})
                    PetHelper helper = new PetHelper(pet);
                    helper.doRegistration(building);
                }
                else
                {
                    Optional<BlockPos> spawnPos = PetHelper.findNearbyValidSpawn(pet, building.getPosition(), 3);
                    if (spawnPos.isEmpty())
                    {
                        MCTradePostMod.LOGGER.warn("Unable to find a safe spawn position near pet shop {} for animal {}.",
                            building.getPosition(), pet);
                        return DECIDE;
                    }

                    BlockPos safeSpawnPos = spawnPos.get();
                    pet.setPos(safeSpawnPos.getX() + 0.5, safeSpawnPos.getY(), safeSpawnPos.getZ() + 0.5);
                    building.getColony().getWorld().addFreshEntity(pet);
                    
                    StatsUtil.trackStat(building, OTHER_ACQUIRED, 1);
                    worker.getCitizenExperienceHandler().addExperience(1.0);
                }
                
            }
            catch (ReflectiveOperationException e)
            {
                MCTradePostMod.LOGGER.error("Failed to instantiate pet of type: " + petType.name(), e);
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
