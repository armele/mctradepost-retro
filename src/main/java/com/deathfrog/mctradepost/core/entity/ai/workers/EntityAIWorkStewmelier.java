package com.deathfrog.mctradepost.core.entity.ai.workers;

import org.slf4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StewmelierIngredientModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.jobs.JobStewmelier;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.util.AdvancementUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STEWMELIER;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EntityAIWorkStewmelier extends AbstractEntityAIInteract<JobStewmelier, BuildingKitchen>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static final String STEW_SERVED_STAT = "stew_served";
    protected static final String BOWLS_FILLED_STAT = "bowls_filled";
    protected static final String BOWLS_COLLECTED_STAT = "bowls_collected";
    protected static final String INGREDIENTS_USED_STAT = "ingredients_used";
    protected static final String KITCHENS_STOCKED_STAT = "kitchens_stocked";

    protected static final float PERFECT_STEW_SKILL = 50.0f;
    protected static final int FIND_HUNGRY_COOLDOWN = 10;
    protected static final int SERVE_TRY_MAX = 100;
    protected static final double SERVE_TRY_DISTANCE = 500.0;
    protected static final float BASE_STEW_AMOUNT_PER_INGREDIENT = .25f;
    protected static final double STEW_HANDOFF_RANGE = 4.0D;
    protected static final int KITCHEN_STEW_DELIVERY = 16;

    protected int findHungryCounter = 0;
    protected int serveTryCounter = 0;
    protected IBuilding currentBowlPickupBuilding = null;
    protected ICitizenData currentHungryCitizen = null;
    protected IBuilding currentDiningHallToStock = null;

    protected static final @Nonnull ItemStack stewReferenceStack = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.PERPETUAL_STEW.get()));
    protected static final @Nonnull ItemStack bowlReferenceStack = new ItemStack(NullnessBridge.assumeNonnull(Items.BOWL));


    public enum StewmelierState implements IAIState
    {
        FIND_POT, MAKE_STEW, FILL_STEW, GATHER_INGREDIENTS, FIND_HUNGRY, SERVE_STEW, COLLECT_BOWLS, STOCK_DINING_HALL;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    protected Map<Integer, ICitizenData> hungryCitizens = new HashMap<>();

    public EntityAIWorkStewmelier(@NotNull JobStewmelier job)
    {
        super(job);
        @SuppressWarnings("unchecked")
        AITarget<IAIState>[] targets = new AITarget[]
        {
            new AITarget<IAIState>(IDLE, START_WORKING, 2),
            new AITarget<IAIState>(START_WORKING, DECIDE, 2),
            new AITarget<IAIState>(DECIDE, this::decide, 10),
            new AITarget<IAIState>(StewmelierState.FIND_POT, this::findPot, 50),
            new AITarget<IAIState>(StewmelierState.FIND_HUNGRY, this::findHungry, 50),
            new AITarget<IAIState>(StewmelierState.GATHER_INGREDIENTS, this::gatherIngredients, 50),
            new AITarget<IAIState>(StewmelierState.MAKE_STEW, this::makeStew, 50),
            new AITarget<IAIState>(StewmelierState.SERVE_STEW, this::serveStew, 50),
            new AITarget<IAIState>(StewmelierState.COLLECT_BOWLS, this::collectBowls, 50),
            new AITarget<IAIState>(StewmelierState.STOCK_DINING_HALL, this::stockDiningHall, 50),
            new AITarget<IAIState>(WANDER, this::wander, 10),
            new AITarget<IAIState>(StewmelierState.FILL_STEW, this::fillStew, 50)
        };
        super.registerTargets(targets);
        worker.setCanPickUpLoot(true);
    }

    /**
     * Decides what to do based on the state of the cauldron/campfire block
     * and the stewmelier ingredient module.
     *
     * @return the next AI state to transition to.
     */
    private IAIState decide()
    {
        Level world = building.getColony().getWorld();

        if (world == null || world.isClientSide())
        {
            return IDLE;
        }

        StewmelierIngredientModule stewModule = safeStewModule();
        BlockPos stewpotPos = stewModule.getStewpotLocation();

        if (stewpotPos == null || stewpotPos == BlockPos.ZERO)
        {
            return StewmelierState.FIND_POT;
        }

        if (!walkToStewPot())
        {
            return DECIDE;
        }

        if (stewModule.ingredientCount() == 0)
        {
            worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_INGREDIENTS), ChatPriority.BLOCKING));
            return WANDER;
        }

        // The cauldron or campfire was destroyed or changed and it is no longer valid.
        if (!isCauldronCandidate(world, stewpotPos))
        {
            stewModule.setStewpotLocation(BlockPos.ZERO);
            return StewmelierState.FIND_POT;
        }

        // The seasoning request puts in an order for seasoning if there isn't any on hand.
        boolean hasSeasoning = checkForSeasoning();
        float stewQuantity = stewModule.getStewQuantity();
        int stewInInventory = stewInInventory();

        // No seasoning, so we can't make stew. Serve what we have!
        if (stewQuantity > 0 && !hasSeasoning)
        {
            if (stewInInventory < 16) 
            {
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Stew is in the pot, but not enough portioned into bowls; let's fill the bowls.", building.getColony().getID()));
                return StewmelierState.FILL_STEW;
            }

            if (findHungryCounter++ > FIND_HUNGRY_COOLDOWN)
            {
                findHungryCounter = 0;
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has stew but no seasoning; let's find hungry people to serve while we wait for seasoning.", building.getColony().getID()));
                return StewmelierState.FIND_HUNGRY;
            }

            if (!hungryCitizens.isEmpty() && stewInInventory > 0)
            {
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has stew but no seasoning; serving the stew we have.", building.getColony().getID()));
                return StewmelierState.SERVE_STEW;
            }
        }

        int ingredientsOnHand = checkForIngredientsInInventory();
        int bowlsInInventory = bowlsInInventory();

        if (bowlsInInventory <= 0)
        {
            job.tickNoBowls();

            if (job.checkForBowlInteraction())
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_BOWLS), ChatPriority.BLOCKING));
            }
        }
        else
        {
            job.resetBowlCounter();
        }

        if (stewInInventory >= 32)
        {
            int hungryCitizenCount = hungryCitizens.size();
            int choices = worker.getRandom().nextInt(100);

            if (hungryCitizenCount < 8 && choices < 50)
            {
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has plenty of stew and only {} hungry citizens. Let's stock the dining halls.", building.getColony().getID(), hungryCitizenCount));
                return StewmelierState.STOCK_DINING_HALL;
            }

            if (hungryCitizenCount > 0)
            {
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has plenty of stew; serving hungry citizens.", building.getColony().getID()));
                return StewmelierState.SERVE_STEW;
            }

            if (findHungryCounter++ > FIND_HUNGRY_COOLDOWN)
            {
                findHungryCounter = 0;
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has plenty of stew; let's find hungry people to serve.", building.getColony().getID()));
                return StewmelierState.FIND_HUNGRY;
            }
        }

        if (ingredientsOnHand == 0)
        {
            int ingredientsInWarehouse = checkForIngredientsInWarehouse();

            if (ingredientsInWarehouse > 0)
            {
                return StewmelierState.GATHER_INGREDIENTS;
            }
            else
            {
                if (bowlsInInventory <= 0)
                {

                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: No ingredients, and needs bowls.", building.getColony().getID()));
                    return StewmelierState.COLLECT_BOWLS;
                }
                
                if (stewInInventory < 16 && stewModule.getStewQuantity() > 0)
                {
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: No ingredients, needs more stew bowls to serve.", building.getColony().getID()));
                    return StewmelierState.FILL_STEW;
                }

                if (stewInInventory > 0)
                {
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: No ingredients, serving the stew we have.", building.getColony().getID()));
                    return StewmelierState.SERVE_STEW;
                }

                return IDLE;
            }
        }

        if (bowlsInInventory <= 0)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, and needs bowls.", building.getColony().getID()));
            return StewmelierState.COLLECT_BOWLS;
        }

        if (stewQuantity < STACKSIZE)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, and needs more stew (current quantity: {}).", building.getColony().getID(), stewQuantity));
            return StewmelierState.MAKE_STEW;
        }

        if (bowlsInInventory > 0 && stewInInventory < 16)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, and needs more filled bowls to serve (current quantity: {}).", building.getColony().getID(), bowlsInInventory));
            return StewmelierState.FILL_STEW;
        }

        int choices = worker.getRandom().nextInt(100);

        if (choices < 25)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, and chooses to make more stew. Choice: {}", building.getColony().getID(), choices));
            return StewmelierState.MAKE_STEW;
        }
        else if (choices < 45)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, and chooses to serve stew. Choice: {}", building.getColony().getID(), choices));
            return StewmelierState.SERVE_STEW;
        }
        else if (choices < 65)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, and chooses to find hungry people to serve. Choice: {}", building.getColony().getID(), choices));
            findHungryCounter = 0;
            return StewmelierState.FIND_HUNGRY;
        }
        else if (choices < 80)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, and chooses to stock the dining halls. Choice: {}", building.getColony().getID(), choices));
            return StewmelierState.STOCK_DINING_HALL;
        }
        else if (choices < 90)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, and chooses to collect bowls. Choice: {}", building.getColony().getID(), choices));
            return StewmelierState.COLLECT_BOWLS;
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Has ingredients, but is stepping away for a moment. Choice: {}", building.getColony().getID(), choices));
            return WANDER;
        }

    }

    /**
     * Finds citizens that are hungry and updates the hungryCitizens list.
     * If there are no hungry citizens or no stew to serve, returns DECIDE.
     * Otherwise, returns StewmelierState.SERVE_STEW.
     *
     * @return the next AI state to transition to.
     */
    protected IAIState findHungry()
    {
        if (!walkToStewPot())
        {
            return StewmelierState.FIND_HUNGRY;
        }

        TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Finding hungry people.", building.getColony().getID()));

        hungryCitizens.clear();
        List<ICitizenData> citizens = building.getColony().getCitizenManager().getCitizens();

        for (ICitizenData citizen : citizens) 
        {
            if (!citizen.getEntity().isPresent()) 
            {
                continue;
            }

            AbstractEntityCitizen entity = citizen.getEntity().get();

            if (entity == null) 
            {
                continue;
            }

            if (citizen.getSaturation() < ((float)ICitizenData.MAX_SATURATION / 3.0f)) 
            {
                hungryCitizens.put(citizen.getId(), citizen);
            }
        }

        if (hungryCitizens.isEmpty())
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Nobody is hungry for stew.", building.getColony().getID()));

            return DECIDE;
        }

        return StewmelierState.SERVE_STEW;
    }

    /**
     * Returns the citizen with the lowest saturation from the list of hungry citizens.
     * If the list of hungry citizens is empty, returns null.
     * 
     * @return the citizen with the lowest saturation, or null if there are no hungry citizens.
     */
    @Nullable
    protected ICitizenData getHungriestCitizen()
    {
        if (hungryCitizens.isEmpty())
        {
            return null;
        }

        ICitizenData hungriest = null;
        double lowestSaturation = Double.MAX_VALUE;

        for (ICitizenData citizen : hungryCitizens.values())
        {
            // Defensive: saturation *should* be valid, but this keeps us robust
            double saturation = citizen.getSaturation();

            if (hungriest == null || saturation < lowestSaturation)
            {
                hungriest = citizen;
                lowestSaturation = saturation;
            }
        }

        return hungriest;
    }

    /**
     * Attempts to fill the stewpot with the given amount of stew.
     * Walks to the stewpot if it isn't already there, then attempts to reduce the number of empty BOWLs in the inventory by the given amount.
     * If the reduction is successful, adds the given amount of stew to the module and adds the filled BOWLs to the inventory.
     * 
     * @return the next state to transition to, or DECIDE to stay in the current state.
     */
    protected IAIState fillStew()
    {
        if (!walkToStewPot())
        {
            return StewmelierState.FILL_STEW;
        }    

        fillUpToXBowls(STACKSIZE);

        return DECIDE;
    }


    protected int fillUpToXBowls(int requestedBowls)
    {
        StewmelierIngredientModule stewModule = safeStewModule();

        float stewQuantity = stewModule.getStewQuantity();
        int bowlsInInventory = bowlsInInventory();
        int bowlsToFill = Math.min(requestedBowls, bowlsInInventory);
        bowlsToFill = Math.min(bowlsToFill, (int) Math.floor(stewQuantity));

        ItemStack bowlStack = new ItemStack(NullnessBridge.assumeNonnull(Items.BOWL));
        ItemStack stewStack = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.PERPETUAL_STEW.get()), bowlsToFill);

        boolean didReduce = InventoryUtils.attemptReduceStackInItemHandler(getInventory(), bowlStack, bowlsToFill);

        if (didReduce)
        {
            stewModule.addStew((float) -bowlsToFill);
            InventoryUtils.addItemStackToItemHandler(getInventory(), stewStack);
            incrementActionsDone();
            StatsUtil.trackStat(building, BOWLS_FILLED_STAT, bowlsToFill);
            worker.getCitizenExperienceHandler().addExperience(bowlsToFill / 4);

            return bowlsToFill;
        }

        return 0;
    }

    /**
     * Checks if the worker has any seasoning in their inventory or the building's inventory.
     * If no seasoning is found in either, makes a request for seasoning (if no such request is 
     * outstanding) and returns false.
     * 
     * @return true if the worker has seasoning, false otherwise.
     */
    private boolean checkForSeasoning()
    {
        ItemStack seasoningStack = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.STEW_SEASONING.get()));
        
        if (InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), stack -> stack != null && ItemStack.isSameItem(stack, seasoningStack)))
        {
            return true;
        }

        // It is ok to take seasoning from the building's inventory.
        if (InventoryUtils.hasItemInProvider(building, stack -> stack != null && ItemStack.isSameItem(stack, seasoningStack)))
        {
            boolean gotSome = InventoryUtils.transferItemStackIntoNextFreeSlotFromProvider(building,
                InventoryUtils.findFirstSlotInProviderNotEmptyWith(building,
                    stack -> stack != null && ItemStack.isSameItem(stack, seasoningStack)),
                worker.getInventoryCitizen());

            if (gotSome)
            {
                return true;
            }
        }

        // Check to see if we've already asked for more seasoning. If not, request more.
        final ImmutableList<IRequest<? extends Stack>> openRequests = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
        boolean outstandingRequest = false;

        for (IRequest<? extends Stack> request : openRequests)
        {
            if (request.getRequest().getStack().is(NullnessBridge.assumeNonnull(seasoningStack.getItem())))
            {
                outstandingRequest = true;
            }
        }

        if (!outstandingRequest)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: No outstanding requests for seasoning - making one now.", building.getColony().getID()));

            // Make a seasoning request.
            worker.getCitizenData()
                .createRequestAsync(new Stack(seasoningStack,
                    Constants.STACKSIZE,
                    1));
        }

        return false;
    }


    /**
     * Returns the number of empty BOWLs in the worker's inventory.
     * 
     * @return the number of empty BOWLs in the worker's inventory.
     */
    private int bowlsInInventory()
    {
        ItemStack bowlStack = new ItemStack(NullnessBridge.assumeNonnull(Items.BOWL));
        return InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), stack -> stack != null && ItemStack.isSameItem(stack, bowlStack));
    }

    /**
     * Picks up stew from the kitchen if there is any, then
     * returns the number of stew in the worker's inventory.
     * 
     * @return the number of stew in the worker's inventory.
     */
    private int stewInInventory()
    {
        ItemStack stewStack = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.PERPETUAL_STEW.get()));

        // It is ok to take stew from the building's inventory.
        if (InventoryUtils.hasItemInProvider(building, stack -> stack != null && ItemStack.isSameItem(stack, stewStack)))
        {
            boolean gotSome = InventoryUtils.transferItemStackIntoNextFreeSlotFromProvider(building,
                InventoryUtils.findFirstSlotInProviderNotEmptyWith(building,
                    stack -> stack != null && ItemStack.isSameItem(stack, stewStack)),
                worker.getInventoryCitizen());

            if (gotSome)
            {
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Picked up stew from the building.", building.getColony().getID()));
            }
        }

        return InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), stack -> stack != null && ItemStack.isSameItem(stack, stewStack));
    }

    /**
     * Checks if the worker has any seasoning in their inventory or the building's inventory.
     * If no seasoning is found in either, makes a request for seasoning (if no such request is 
     * outstanding) and returns false.
     * 
     * Remember that the ItemStorage.getAmount() method returns the number of stacks to protect in the warehouse inventory.
     * 
     * @return true if the worker has seasoning, false otherwise.
     */
    private int checkForIngredientsInInventory()
    {
        StewmelierIngredientModule stewModule = safeStewModule(); 
        Set<ItemStorage> ingredients = stewModule.getIngredients();
        int ingredientCount = 0;

        for (ItemStorage ingredient : ingredients)
        {
            ItemStack ingredientStack = ingredient.getItemStack();

            if (ingredientStack == null || ingredientStack.isEmpty())
            {
                continue;
            }

            if (InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), stack -> stack != null && ItemStack.isSameItem(stack, ingredientStack)))
            {
                ingredientCount++;
            }
        }

        return ingredientCount;
    }


    /**
     * Checks if the worker has any stew ingredients in the closest warehouse to the stewpot location.
     * Respects the amount of each ingredient to protect in the warehouse inventory.
     * 
     * @return number of ingredients in the warehouse.
     */
    private int checkForIngredientsInWarehouse()
    {
        StewmelierIngredientModule stewModule = safeStewModule();
        BlockPos stewPotLocation = stewModule.getStewpotLocation();   
        Set<ItemStorage> ingredients = stewModule.getIngredients();
        int ingredientCount = 0;

        IWareHouse warehouse = building.getColony().getBuildingManager().getClosestWarehouseInColony(stewPotLocation);

        for (ItemStorage ingredient : ingredients)
        {
            ItemStack ingredientStack = ingredient.getItemStack();

            if (ingredientStack == null || ingredientStack.isEmpty())
            {
                continue;
            }

            if (warehouse == null)
            {
                continue;
            }

            int warehouseCount = InventoryUtils.getItemCountInProvider(warehouse, stack -> stack != null && ItemStack.isSameItem(stack, ingredientStack));
            int spare = warehouseCount - (ingredient.getAmount() * ingredientStack.getMaxStackSize());

            final int toTake = Math.min(spare, ingredientStack.getMaxStackSize());

            if (toTake > 0)
            {
                ingredientCount++;
            }
        }

        return ingredientCount;
    }


    /**
     * Tries to take the required ingredients from the closest warehouse to the stew pot's location.
     * If all the required ingredients are found in the warehouse, the method will return true.
     * If any of the required ingredients are not found in the warehouse, the method will return false.
     * If the method returns false, it will not attempt to take the ingredients again until the AI is reset.
     * @return true if all the required ingredients are found in the warehouse, false otherwise.
     */
    private boolean takeIngredientsFromWarehouse()
    {
        StewmelierIngredientModule stewModule = safeStewModule();
        BlockPos stewPotLocation = stewModule.getStewpotLocation();   
        Set<ItemStorage> ingredients = stewModule.getIngredients();
        int ingredientCount = 0;

        IWareHouse warehouse = building.getColony().getBuildingManager().getClosestWarehouseInColony(stewPotLocation);

        for (ItemStorage ingredient : ingredients)
        {
            ItemStack ingredientStack = ingredient.getItemStack();

            if (ingredientStack == null || ingredientStack.isEmpty())
            {
                continue;
            }

            if (warehouse == null)
            {
                continue;
            }

            int warehouseCount = InventoryUtils.getItemCountInProvider(warehouse, stack -> stack != null && ItemStack.isSameItem(stack, ingredientStack));
            int spare = warehouseCount - (ingredient.getAmount() * ingredientStack.getMaxStackSize());

            final int toTake = Math.min(spare, ingredientStack.getMaxStackSize());

            if (toTake > 0)
            {
                boolean gotSome = InventoryUtils.transferItemStackIntoNextFreeSlotFromItemHandler(warehouse.getItemHandlerCap(),
                    stack -> stack != null && ItemStack.isSameItem(stack, ingredientStack),
                    toTake,
                    worker.getInventoryCitizen());

                if (gotSome)
                {
                    ingredientCount++;
                }
                else
                {
                    break;
                }
            }
        }

        return ingredientCount > 0;
    }

    /**
     * Makes stew by taking the required ingredients from the worker's inventory and adding the stew to the stew module.
     * If all the required ingredients are found in the inventory, the method will return StewmelierState.MAKE_STEW.
     * If any of the required ingredients are not found in the inventory, the method will return DECIDE.
     * If the method returns DECIDE, it will not attempt to make the stew again until the AI is reset.
     * @return StewmelierState.MAKE_STEW if some stew was successfully made, DECIDE otherwise.
     */
    protected IAIState makeStew()
    {
        if (!walkToStewPot())
        {
            return StewmelierState.MAKE_STEW;
        }

        ItemStack seasoningStack = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.STEW_SEASONING.get()));
        int seasoning = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), stack -> stack != null && ItemStack.isSameItem(stack, seasoningStack));

        if (seasoning == 0)
        {
            return DECIDE;
        }

        int ingredientsUsed = 0;
        StewmelierIngredientModule stewModule = safeStewModule();

        for (ItemStorage ingredient : stewModule.getIngredients())
        {
            ItemStack ingredientStack = ingredient.getItemStack();

            if (ingredientStack == null || ingredientStack.isEmpty())
            {
                continue;
            }

            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Stew seasoning level before seasoning: {}", building.getColony().getID(), stewModule.getSeasoningLevel()));

            // Season the stew when necessary!
            if (stewModule.getSeasoningLevel() <= 0)
            {
                boolean seasoned = InventoryUtils.attemptReduceStackInItemHandler(worker.getInventoryCitizen(), seasoningStack, 1);
                if (seasoned)
                {
                    StatsUtil.trackStatByName(building, INGREDIENTS_USED_STAT, seasoningStack.getHoverName(), 1);

                    worker.setItemInHand(InteractionHand.MAIN_HAND, seasoningStack);
                    worker.swing(InteractionHand.MAIN_HAND);
                    worker.playSound(NullnessBridge.assumeNonnull(SoundEvents.LAVA_AMBIENT),
                        (float) SoundUtils.BASIC_VOLUME - .2f,
                        (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(worker.getRandom()));

                    stewModule.setSeasoningLevel(1);
                }
                else
                {
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Out of seasoning.", building.getColony().getID()));
                    return DECIDE;
                }
            }

            worker.setItemInHand(InteractionHand.MAIN_HAND, ingredientStack);
            worker.swing(InteractionHand.MAIN_HAND);
            worker.playSound(NullnessBridge.assumeNonnull(SoundEvents.LAVA_AMBIENT),
                (float) SoundUtils.BASIC_VOLUME - .2f,
                (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(worker.getRandom()));

            boolean used = InventoryUtils.attemptReduceStackInItemHandler(worker.getInventoryCitizen(), ingredientStack, 1);

            if (used)
            {
                int skill = getPrimarySkillLevel();
                stewModule.addStew(BASE_STEW_AMOUNT_PER_INGREDIENT * (1.0f * ((float) skill / PERFECT_STEW_SKILL)));
                
                StatsUtil.trackStatByName(building, INGREDIENTS_USED_STAT, ingredientStack.getHoverName(), ingredientsUsed);

                ingredientsUsed++;
            }
        }

        if (ingredientsUsed > 0 && stewModule.getStewQuantity() < StewmelierIngredientModule.STEW_LEVEL_2)
        {
            incrementActionsDone();
            worker.getCitizenExperienceHandler().addExperience((double)ingredientsUsed * (double)BASE_STEW_AMOUNT_PER_INGREDIENT);

            return StewmelierState.MAKE_STEW;
        }

        worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
        return DECIDE;
    }

    /**
     * Serves stew to a hungry citizen.
     * Picks a hungry citizen and walks to them. If it successfully reaches the citizen, it will attempt to transfer two stew items from the worker's inventory to the citizen's inventory. If the transfer is successful, it will increment the actions done counter, decrement the citizen's saturation, and reset the current hungry citizen.
     * If the transfer is not successful, it will reset the current hungry citizen and return to the DECIDE state.
     * @return the next AI state to transition to.
     */
    protected IAIState serveStew()
    {
        int stewInInventory = stewInInventory();

        // Pick a hungry citizen.
        if (currentHungryCitizen == null)
        {
            currentHungryCitizen = getHungriestCitizen();

            if (currentHungryCitizen == null)
            {
                if (findHungryCounter++ > FIND_HUNGRY_COOLDOWN)
                {
                    findHungryCounter = 0;
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: I am ready to serve stew; let's find hungry people to serve.", building.getColony().getID()));
                    return StewmelierState.FIND_HUNGRY;
                }
                else
                {
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: I am ready to serve stew; but there are no hungry people to serve. Find hungry counter at {}", 
                        building.getColony().getID(), findHungryCounter));

                    return DECIDE;
                }
            }

            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Tracking down {} to give them some stew.", 
                building.getColony().getID(), currentHungryCitizen.getName()));
        }

        if (stewInInventory < 2)
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
            return StewmelierState.FILL_STEW;
        }


        if (currentHungryCitizen.getEntity().isEmpty()) 
        { 
            currentHungryCitizen=null; 
            return DECIDE; 
        }

        // Walk to them
        AbstractEntityCitizen citizenEntity = currentHungryCitizen.getEntity().get();
        double distanceto = -1.0D;

        if (citizenEntity == null)
        {
            currentHungryCitizen = null;
            return DECIDE;
        }

        ItemStack stewHandDisplay = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.PERPETUAL_STEW.get()), 1);
        worker.setItemInHand(InteractionHand.MAIN_HAND, stewHandDisplay);

        // Check for reasonable serving distances and abort if out of range.
        distanceto = BlockPosUtil.getDistance(worker.blockPosition(), citizenEntity.blockPosition());

        if (distanceto > SERVE_TRY_DISTANCE)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Giving up on serving {} because they are too far away.", 
                building.getColony().getID(), currentHungryCitizen.getName()));
            currentHungryCitizen = null;
            return DECIDE;
        }

        // Secondary skill determines walk speed for serving stew.
        double speed = 0.8D + (double)getSecondarySkillLevel() / (double)PERFECT_STEW_SKILL; 

        if (!EntityNavigationUtils.walkToPos(worker, citizenEntity.blockPosition(), 1, false, speed) && distanceto > STEW_HANDOFF_RANGE)
        {
            serveTryCounter++;

            if (serveTryCounter > SERVE_TRY_MAX)
            {
                serveTryCounter = 0;
                currentHungryCitizen = null;
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Giving up on serving {} because I could not reach them after {} tries.", 
                    building.getColony().getID(), currentHungryCitizen.getName(), serveTryCounter));
                return DECIDE;
            }

            return StewmelierState.SERVE_STEW;
        }

        InventoryCitizen workerInventory = worker.getCitizenData().getInventory();

        if (workerInventory == null)
        {
            currentHungryCitizen = null;
            return DECIDE;
        }

        ItemStack twoItemStewStack =
            extractTwoStew(workerInventory);

        if (twoItemStewStack.isEmpty())
        {
            // No stew available — bail out cleanly
            serveTryCounter = 0;
            return DECIDE;
        }

        boolean didTransfer = InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(twoItemStewStack, currentHungryCitizen.getInventory());
        
        if (didTransfer)
        {
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Gave stew to {} after {} tries.", 
                building.getColony().getID(), currentHungryCitizen.getName(), serveTryCounter));

            boolean isStewOnMenu = checkClosestMenu(citizenEntity.blockPosition());

            if (!isStewOnMenu)
            {
                job.tickNoMenu();

                if (job.checkForMenuInteraction())
                {
                    worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NOT_ON_MENU), ChatPriority.BLOCKING));
                }
            }
            else
            {
                job.resetMenuCounter();
            }

            serveTryCounter = 0;
            incrementActionsDoneAndDecSaturation();

            StatsUtil.trackStat(building, STEW_SERVED_STAT, 2);

            AdvancementUtils.TriggerAdvancementPlayersForColony(building.getColony(),
                    player -> {
                        if (player != null)
                        {
                            MCTPAdvancementTriggers.RUNS_ON_STEW.get().trigger(player);
                        }
                    });

            worker.getCitizenExperienceHandler().addExperience(1);
            hungryCitizens.remove(currentHungryCitizen.getId());
            currentHungryCitizen = null;
        }

        return DECIDE;
    }

    /**
     * Finds a PerpetualStew stack with at least 2 items and splits off exactly 2.
     *
     * @param inventory the source inventory
     * @return a stack of size 2, or ItemStack.EMPTY if not available
     */
    @SuppressWarnings("null")
    @Nonnull
    private static ItemStack extractTwoStew(@Nonnull final IItemHandler inventory)
    {
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            ItemStack stack = inventory.getStackInSlot(slot);

            if (stack.isEmpty())
                continue;

            if (!stack.is(MCTradePostMod.PERPETUAL_STEW.get()))
                continue;

            if (stack.getCount() < 2)
                continue;

            // split(2) mutates the original stack safely
            return stack.split(2);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Attempts to stock the dining hall with stew.
     * If the worker does not have enough stew, it will go collect more.
     * If there are no bowls available, it will go collect more.
     * If the worker successfully stocks the dining Hall, it will reset its current target.
     * 
     * @return the next AI state to transition to.
     */
    protected IAIState stockDiningHall()
    {
        if (currentDiningHallToStock != null)
        {
            int stewInInventory = stewInInventory();
            int bowlsInInventory = bowlsInInventory();

            if (stewInInventory < KITCHEN_STEW_DELIVERY)
            {
                if (bowlsInInventory < KITCHEN_STEW_DELIVERY - stewInInventory)
                {
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {} - Needs more empty bowls to stock dining hall {}. Bowls: {}, Stew: {}.", 
                        building.getColony().getID(), currentDiningHallToStock.getBuildingDisplayName(), bowlsInInventory, stewInInventory));

                    return StewmelierState.COLLECT_BOWLS;
                }

                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {} - Needs more filled stew to stock dining hall {}. Bowls: {}, Stew: {}.", 
                    building.getColony().getID(), currentDiningHallToStock.getBuildingDisplayName(), bowlsInInventory, stewInInventory));

                return StewmelierState.FILL_STEW;
            }

            if (!walkToBuilding(currentDiningHallToStock))
            {
                return StewmelierState.STOCK_DINING_HALL;
            }

            // Drop off stew at currentDiningHallToStock.
            if (stewInInventory >= KITCHEN_STEW_DELIVERY)
            {
                boolean gaveStew = InventoryUtils.transferItemStackIntoNextFreeSlotFromItemHandler(worker.getInventoryCitizen(),
                    stack -> stack != null && ItemStack.isSameItem(stack, stewReferenceStack),
                    KITCHEN_STEW_DELIVERY,
                    currentDiningHallToStock.getItemHandlerCap());

                if (gaveStew)
                {
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {} - Stocked dining hall: {}.", building.getColony().getID(), currentDiningHallToStock.getBuildingDisplayName()));
                    StatsUtil.trackStat(building, KITCHENS_STOCKED_STAT, 1);
                    worker.getCitizenExperienceHandler().addExperience(1);
                    currentDiningHallToStock = null;
                    return StewmelierState.STOCK_DINING_HALL;
                }
                else
                {
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {} - Could not stock dining hall (might be full): {}.", building.getColony().getID(), currentDiningHallToStock.getBuildingDisplayName()));
                    currentDiningHallToStock = null;
                    return DECIDE;
                }
            }
        }

        ItemStack stewStack = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.PERPETUAL_STEW.get()));

        for (Map.Entry<BlockPos, IBuilding> buildingEntry : building.getColony().getBuildingManager().getBuildings().entrySet())
        {
            if (!(buildingEntry.getValue() instanceof BuildingCook))
            {
                continue;
            }

            final boolean isStewOnMenu = isStewOnMenu(buildingEntry.getValue());
                    
            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {} - Checking dining hall {} with stew on menu: {}.", building.getColony().getID(), buildingEntry.getValue(), isStewOnMenu));

            if (isStewOnMenu)
            {
                int stewCount = InventoryUtils.getItemCountInProvider(buildingEntry.getValue(), stack -> stack != null && ItemStack.isSameItem(stack, stewStack));

                if (stewCount < STACKSIZE)
                {
                    currentDiningHallToStock = buildingEntry.getValue();
                    TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {} - We found a dining hall that needs stew: {}.", building.getColony().getID(), currentDiningHallToStock.getBuildingDisplayName()));
                    return StewmelierState.STOCK_DINING_HALL;
                }
            }
        }

        TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {} - No dining hall needs stew.", building.getColony().getID()));

        return DECIDE;
        
    }

    /**
     * Walk to a building and collect all the bowls in it.
     * This state is entered when the Stewmelier needs to collect more bowls.
     * The AI will walk to the nearest building with bowls, pick up all the bowls,
     * and then return to the DECIDE state.
     * @return the next AI state to transition to, which is either DECIDE or COLLECT_BOWLS.
     */
    protected IAIState collectBowls()
    {
        if (currentBowlPickupBuilding != null)
        {
            if (!walkToBuilding(currentBowlPickupBuilding))
            {
                return StewmelierState.COLLECT_BOWLS;
            }

            TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Collecting bowls from {}.", building.getColony().getID(), currentBowlPickupBuilding.getBuildingDisplayName()));

            boolean gotSomeThisPass = false;
            boolean gotSomeEver = false;
            int trylimit = 3;
            int bowlCountBefore = bowlsInInventory();
            
            // Take all the bowls in this building.
            do
            {
                int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(currentBowlPickupBuilding,
                        stack -> stack != null && ItemStack.isSameItem(stack, bowlReferenceStack));
                
                if (slot >= 0)
                {
                    gotSomeThisPass = InventoryUtils.transferItemStackIntoNextFreeSlotFromProvider(currentBowlPickupBuilding,
                        slot,
                        worker.getInventoryCitizen());    
                    gotSomeEver = true; 
                }
                else
                {
                    gotSomeThisPass = false;
                }

                trylimit--;
            } while (gotSomeThisPass && trylimit > 0);

            if (gotSomeEver)
            {
                int bowlCountAfter = bowlsInInventory();
                int bowlsCollected = bowlCountAfter - bowlCountBefore;

                if (bowlsCollected > 0)
                {
                    StatsUtil.trackStatByName(building, BOWLS_COLLECTED_STAT, currentBowlPickupBuilding.getBuildingDisplayName(), bowlsCollected);
                }

                currentBowlPickupBuilding = null;
                worker.setItemInHand(InteractionHand.MAIN_HAND, bowlReferenceStack);
                return DECIDE;
            }

            currentBowlPickupBuilding = null;
        }

        boolean diningHallFound = false;
        boolean someDiningHallHasStew = false;

        for (Map.Entry<BlockPos, IBuilding> buildingEntry : building.getColony().getBuildingManager().getBuildings().entrySet())
        {
            BlockPos parentLoc = buildingEntry.getValue().getParent();
            IBuilding parent = parentLoc != null && !BlockPos.ZERO.equals(parentLoc) ? building.getColony().getBuildingManager().getBuilding(parentLoc) : null;

            // Don't take bowls from these buildings.
            if (buildingEntry.getValue() instanceof BuildingKitchen 
                || buildingEntry.getValue() instanceof IWareHouse
                || buildingEntry.getValue() instanceof BuildingOutpost
                || (parentLoc != null && parent instanceof BuildingOutpost)
            ) {
                continue;
            }

            if (buildingEntry.getValue() instanceof BuildingCook)
            {
                diningHallFound = true;
                if (!someDiningHallHasStew)
                {
                    someDiningHallHasStew = isStewOnMenu(buildingEntry.getValue());
                }
            }

            // LOGGER.info("Checking building at position {} of type {} for bowls.", buildingEntry.getKey(), buildingEntry.getValue().getClass().getSimpleName());

            if (InventoryUtils.hasItemInProvider(buildingEntry.getValue(), stack -> stack != null && ItemStack.isSameItem(stack, bowlReferenceStack)))
            {
                job.resetBowlCounter();
                currentBowlPickupBuilding = buildingEntry.getValue();
                
                TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Colony {}: Going to {} to collect bowls.", building.getColony().getID(), currentBowlPickupBuilding.getBuildingDisplayName()));

                return StewmelierState.COLLECT_BOWLS;
            }
        }

        if (!diningHallFound || !someDiningHallHasStew)
        {
            job.tickNoMenu();

            if (job.checkForMenuInteraction())
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NOT_ON_MENU), ChatPriority.BLOCKING));
            }
        }
        else
        {
            job.resetMenuCounter();
        }

        int bowlsInInventory = bowlsInInventory();

        // We don't need more bowls from our backup locations if we have over a stack.
        if (bowlsInInventory >= STACKSIZE)
        {
            return DECIDE;
        }

        // If there are no bowls to pick up from other buildings, try to get bowls from a warehouse.
        if (currentBowlPickupBuilding == null)
        {
            IWareHouse warehouse = building.getColony().getBuildingManager().getClosestWarehouseInColony(building.getPosition());
            int bowlsInWarehouse = InventoryUtils.getItemCountInProvider(warehouse, stack -> stack != null && ItemStack.isSameItem(stack, bowlReferenceStack));

            if (bowlsInWarehouse > 0)
            {
                currentBowlPickupBuilding = warehouse;
                return StewmelierState.COLLECT_BOWLS;
            }
        }

        // If there are no bowls to pick up from other buildings, try to get bowls from the kitchen itself (if there are more than a stack available).
        if (currentBowlPickupBuilding == null)
        {
            int bowlsInKitchen = InventoryUtils.getItemCountInProvider(building, stack -> stack != null && ItemStack.isSameItem(stack, bowlReferenceStack));

            if (bowlsInKitchen > bowlReferenceStack.getMaxStackSize())
            {
                int bowlsTakenFromKitchen = InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandlerWithResult(building.getItemHandlerCap(), 
                    stack -> stack != null && ItemStack.isSameItem(stack, bowlReferenceStack), STACKSIZE, getInventory());

                if (bowlsTakenFromKitchen > 0)
                {
                    job.resetBowlCounter();
                    return DECIDE;
                }
            }
        }

        return DECIDE;
    }


    /**
     * Checks if the stew is on the menu of restaurant closest to the given position.
     * If it is on the menu, it will not trigger any interactions. If it is not on the menu, it will attempt to trigger a menu interaction.
     * If the menu interaction check succeeds, it will trigger a NOT_ON_MENU interaction.
     * @param building the building to check the menu for, or null if it should check the building at the given position
     * @param pos the position of the building to check the menu for, or null if it should check the given building
     */
    protected boolean checkClosestMenu(BlockPos pos)
    {
        boolean isStewOnMenu = false;

        if (pos != null && !BlockPos.ZERO.equals(pos))
        {
            BlockPos diningHallPos = building.getColony().getBuildingManager().getBestBuilding(pos, BuildingCook.class);
            IBuilding diningHallBuilding = diningHallPos != null ? building.getColony().getBuildingManager().getBuilding(diningHallPos) : null;
            isStewOnMenu = isStewOnMenu(diningHallBuilding);
        }

        return isStewOnMenu;
    }


    /**
     * Checks if the stew is on the menu of the given building (assumed to be a restaurant building).
     * If it is on the menu, it will not trigger any interactions. If it is not on the menu, it will attempt to trigger a menu interaction.
     * If the menu interaction check succeeds, it will trigger a NOT_ON_MENU interaction.
     * @param cookBuilding the restaurant building to check the menu of.
     * @return true if the stew is on the menu, false otherwise.
     */
    protected boolean isStewOnMenu(IBuilding cookBuilding)
    {
        if (cookBuilding == null || !(cookBuilding instanceof BuildingCook))
        {
            return false;
        }

        boolean stewOnMenu = false;
        Set<ItemStorage> menu = ((RestaurantMenuModule)cookBuilding.getModule(BuildingModules.RESTAURANT_MENU)).getMenu();

        for (ItemStorage menuItem : menu)
        {
            if (menuItem.getItemStack().is(NullnessBridge.assumeNonnull(MCTradePostMod.PERPETUAL_STEW.get())))
            {
                stewOnMenu = true;
                break;
            }
        }

        return stewOnMenu;
    }

    /**
     * Attempts to gather the required ingredients for making stew. It first attempts to walk to the closest warehouse to the
     * stewpot location. If it reaches the warehouse, it will then attempt to take the required ingredients from the
     * warehouse. If it successfully takes all the required ingredients, it will then return to the MAKE_STEW state. If it
     * fails to take all the required ingredients, it will return to the DECIDE state.
     * 
     * @return the next AI state to transition to.
     */
    protected IAIState gatherIngredients()
    {
        StewmelierIngredientModule stewModule = safeStewModule();
        BlockPos stewPotLocation = stewModule.getStewpotLocation();   

        IWareHouse warehouse = building.getColony().getBuildingManager().getClosestWarehouseInColony(stewPotLocation);

        if (!walkToBuilding(warehouse))
        {
            return StewmelierState.GATHER_INGREDIENTS;
        }

        boolean gotIngredients = takeIngredientsFromWarehouse();

        if (gotIngredients)
        {
            return StewmelierState.MAKE_STEW;
        }

        return DECIDE;
    }

    /**
     * Attempts to walk to the location of the stewpot in the stewmelier ingredient module.
     * If the path to the location is found to be valid and walkable, this function will return true.
     * If the path is not valid or walkable, this function will return false.
     *
     * @return true if the worker has reached the location of the stewpot, false otherwise.
     */
    private boolean walkToStewPot()
    {
        Level level = building.getColony().getWorld();
        BlockPos potLocation = safeStewModule().getStewpotLocation();

        if (level == null || level.isClientSide())
        {
            return true;
        }

        // If our pot location has been reset or the cauldron is no longer valid, we can't walk to it... return that we are done with walking.
        if (potLocation == null || potLocation == BlockPos.ZERO || !isCauldronCandidate(level, potLocation))
        {
            return true;
        } 

        return walkToSafePos(potLocation);
    }

    /**
     * Finds a filled stewpot block within 30 blocks of the worker's position,
     * 5 blocks above/below the worker's y-coordinate, and sets the location of the
     * stewpot in the stewmelier ingredient module.
     *
     * If no filled stewpot block is found, sets the AI state to DECIDE.
     *
     * @return the next AI state, or DECIDE if no filled stewpot block is found.
     */
    private IAIState findPot()
    {
        TraceUtils.dynamicTrace(TRACE_STEWMELIER, () -> LOGGER.info("Attempting to find a filled stewpot block within 30 blocks of the worker's work building position."));

        Level level = building.getColony().getWorld();

        if (level == null || level.isClientSide())
        {
            return IDLE;
        }

        BlockPos potPos = findCauldronOverCampfire(level, worker.blockPosition(), 30, 5, 5);

        if (potPos == null || BlockPos.ZERO.equals(potPos))
        {
            EntityNavigationUtils.walkToRandomPosAround(worker, building.getPosition(), 30, 1);
            job.tickNoStewpot();

            if (job.checkForStewpotInteraction())
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_STEWPOT), ChatPriority.BLOCKING));
            }
            return DECIDE;
        }

        job.resetStewpotCounter();

        EntityNavigationUtils.walkToPos(worker, potPos, true);

        building.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS).setStewpotLocation(potPos);

        return StewmelierState.MAKE_STEW;
    }

    /**
     * Finds a cauldron over a lit campfire within {@code radius} blocks of {@code origin}.
     *
     * Matches either:
     * - vanilla empty cauldron (Blocks.CAULDRON), OR
     * - your filled stewpot block (e.g., ModBlocks.STEWPOT_FILLED.get()) if you pass it
     *
     * @param level the world
     * @param origin center of the search
     * @param radius max horizontal distance to search (30 requested)
     * @param yBelow number of blocks below origin.y to scan (terrain variance)
     * @param yAbove number of blocks above origin.y to scan
     * @param filledStewpotBlock optional: your filled stewpot block, or null to ignore
     */
    public static @Nullable BlockPos findCauldronOverCampfire(
        final @Nonnull Level level,
        final BlockPos origin,
        final int radius,
        final int yBelow,
        final int yAbove)
    {
        final int ox = origin.getX();
        final int oy = origin.getY();
        final int oz = origin.getZ();

        // Scan a square: (2r+1)^2 * (yBelow+yAbove+1). With r=30 and y band ~5, this is reasonable if gated.
        for (int dy = -yBelow; dy <= yAbove; dy++)
        {
            final int y = oy + dy;

            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    // Optional: enforce circular radius instead of square
                    if ((dx * dx + dz * dz) > (radius * radius))
                    {
                        continue;
                    }

                    final BlockPos pos = new BlockPos(ox + dx, y, oz + dz);

                    if (!isCauldronCandidate(level, pos))
                    {
                        continue;
                    }

                    BlockPos below = pos.below();
                    if (below != null && isLitCampfire(level, below))
                    {
                        return pos;
                    }
                }
            }
        }

        return null;
    }


    /**
     * Wander around.
     *
     * This AI state will cause the Stewmelier to wander around by walking to a random position within a 30-block radius.
     * Once the Stewmelier has reached the random position, it will return to the DECIDE state.
     * 
     * @return the DECIDE state, which will cause the Stewmelier to decide what to do next.
     */
    public IAIState wander()
    {
        EntityNavigationUtils.walkToRandomPos(worker, 30, Constants.DEFAULT_SPEED);
        return DECIDE;
    }

    /**
     * Determines if the given block position is a valid cauldron candidate.
     * A valid candidate is either the vanilla empty cauldron (Blocks.CAULDRON),
     * or the provided filled stewpot block (if not null).
     * If you want to accept any modded cauldron blocks, you can broaden this:
     * return block instanceof AbstractCauldronBlock;
     * But I'd keep it strict unless you explicitly want compatibility.
     * @param level the world
     * @param pos the block position to check
     * @return true if the block position is a valid cauldron candidate, false otherwise
     */
    private static boolean isCauldronCandidate(final @Nonnull Level level, final @Nonnull BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        final Block block = state.getBlock();
        final Block filledStewpotBlock = MCTradePostMod.STEWPOT_FILLED.get();

        // Vanilla empty cauldron
        if (block == Blocks.CAULDRON)
        {
            return true;
        }

        // Your filled stewpot, if provided
        if (filledStewpotBlock != null && block == filledStewpotBlock)
        {
            return true;
        }

        // If you want to accept any modded cauldron blocks, you can broaden this:
        // return block instanceof AbstractCauldronBlock;
        // But I'd keep it strict unless you explicitly want compatibility.
        if (block instanceof CauldronBlock)
        {
            return true;
        }

        return false;
    }

    /**
     * Determines if the given block position is a lit campfire.
     * A lit campfire is a block of type Blocks.CAMPFIRE with its lit property set to true.
     * @param level the world
     * @param pos the block position to check
     * @return true if the block position is a lit campfire, false otherwise
     */
    private static boolean isLitCampfire(final @Nonnull Level level, final @Nonnull BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        if (!state.is(NullnessBridge.assumeNonnull(Blocks.CAMPFIRE)))
        {
            return false;
        }
        return state.getValue(NullnessBridge.assumeNonnull(CampfireBlock.LIT));
    }
    

    /**
     * Returns the building class that this AI is intended to be used with.
     * For the EntityAIWorkStewmelier, this is the BuildingKitchen class.
     * @return the building class that this AI is intended to be used with.
     */
    @Override
    public Class<BuildingKitchen> getExpectedBuildingClass()
    {
        return BuildingKitchen.class;
    }


    /**
     * Retrieves the StewmelierIngredientModule associated with the building.
     * This method is guaranteed to return a non-null value.
     * If the module is not found, an IllegalStateException is thrown.
     * This should never happen, so if you encounter this exception, please report it as a bug.
     * @return the StewmelierIngredientModule associated with the building
     */
    protected @Nonnull StewmelierIngredientModule safeStewModule()
    {
        StewmelierIngredientModule stewModule = building.getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS);
        
        if (stewModule == null)
        {
            throw new IllegalStateException("Stewmelier ingredient module not found in building. This should never happen. Please report this bug.");
        }

        return stewModule;
    }
}
