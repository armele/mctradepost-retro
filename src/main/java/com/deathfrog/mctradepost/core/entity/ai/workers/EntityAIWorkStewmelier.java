package com.deathfrog.mctradepost.core.entity.ai.workers;

import org.slf4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.SoundUtils;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StewmelierIngredientModule;
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
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
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

    protected static final float PERFECT_STEW_SKILL = 50.0f;
    protected static final int FIND_HUNGRY_COOLDOWN = 100;
    protected int findHungryCounter = 0;

    protected IBuilding currentBowlPickupBuilding = null;
    protected ICitizenData currentHungryCitizen = null;

    public enum StewmelierState implements IAIState
    {
        FIND_POT, MAKE_STEW, GATHER_INGREDIENTS, FIND_HUNGRY, SERVE_STEW, COLLECT_BOWLS;

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
            new AITarget<IAIState>(WANDER, this::wander, 10)
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
        // TODO: Add experience and increment actions done.

        Level world = building.getColony().getWorld();
        if (world == null || world.isClientSide())
        {
            return IDLE;
        }

        if (!walkToStewPot())
        {
            return DECIDE;
        }

        StewmelierIngredientModule stewModule = safeStewModule();

        if (stewModule.ingredientCount() == 0)
        {
            worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_INGREDIENTS), ChatPriority.BLOCKING));
            return WANDER;
        }

        BlockPos stewpotPos = stewModule.getStewpotLocation();
        if (stewpotPos == null || stewpotPos == BlockPos.ZERO)
        {
            return StewmelierState.FIND_POT;
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

        // No seasoning, so we can't make stew. Serve what we have!
        if (stewQuantity > 0 && !hasSeasoning)
        {
            if (findHungryCounter++ > FIND_HUNGRY_COOLDOWN)
            {
                findHungryCounter = 0;
                return StewmelierState.FIND_HUNGRY;
            }

            if (!hungryCitizens.isEmpty())
            {
                return StewmelierState.SERVE_STEW;
            }
        }

        int ingredientsOnHand = checkForIngredientsInInventory();
        boolean hasBowls = checkForBowls();

        if (ingredientsOnHand == 0)
        {
            int ingredientsInWarehouse = checkForIngredientsInWarehouse();

            if (ingredientsInWarehouse > 0)
            {
                return StewmelierState.GATHER_INGREDIENTS;
            }
            else
            {
                if (!hasBowls)
                {
                    return StewmelierState.COLLECT_BOWLS;
                }
                
                if (stewModule.getStewQuantity() > 0)
                {
                    return StewmelierState.SERVE_STEW;
                }

                return IDLE;
            }
        }
        else
        {
            int choices = worker.getRandom().nextInt(100);

            if (choices < 30)
            {
                return StewmelierState.MAKE_STEW;
            }
            else if (choices < 60)
            {
                return StewmelierState.SERVE_STEW;
            }
            else if (choices < 90)
            {
                return StewmelierState.COLLECT_BOWLS;
            }
            else
            {
                return WANDER;
            }
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
        StewmelierIngredientModule stewModule = safeStewModule();

        if (!walkToStewPot())
        {
            return StewmelierState.FIND_HUNGRY;
        }

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

        if (hungryCitizens.isEmpty() || stewModule.getStewQuantity() <= 0)
        {
            return DECIDE;
        }

        return StewmelierState.SERVE_STEW;
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

        // It is ok to take stew seasoning from the building.
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
            // Make a seasoning request.
            worker.getCitizenData()
                .createRequestAsync(new Stack(seasoningStack,
                    Constants.STACKSIZE,
                    1));
        }

        return false;
    }


    private boolean checkForBowls()
    {
        ItemStack bowlStack = new ItemStack(NullnessBridge.assumeNonnull(Items.BOWL));
        
        if (InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), stack -> stack != null && ItemStack.isSameItem(stack, bowlStack)))
        {
            return true;
        }

        return false;
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

        int ingredientsUsed = 0;

        for (ItemStorage ingredient : safeStewModule().getIngredients())
        {
            ItemStack ingredientStack = ingredient.getItemStack();

            if (ingredientStack == null || ingredientStack.isEmpty())
            {
                continue;
            }

            worker.setItemInHand(InteractionHand.MAIN_HAND, ingredientStack);
            worker.swing(InteractionHand.MAIN_HAND);
            worker.playSound(NullnessBridge.assumeNonnull(SoundEvents.LAVA_AMBIENT),
                (float) SoundUtils.BASIC_VOLUME,
                (float) com.minecolonies.api.util.SoundUtils.getRandomPentatonic(worker.getRandom()));

            boolean used = InventoryUtils.attemptReduceStackInItemHandler(worker.getInventoryCitizen(), ingredientStack, 1);

            if (used)
            {
                ingredientsUsed++;
            }
        }

        if (ingredientsUsed > 0)
        {
            incrementActionsDone();
            worker.getCitizenExperienceHandler().addExperience(ingredientsUsed);
            int skill = getPrimarySkillLevel();
            safeStewModule().addStew(ingredientsUsed + ((float)ingredientsUsed * ((float) skill / PERFECT_STEW_SKILL)));

            return StewmelierState.MAKE_STEW;
        }

        worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
        return DECIDE;
    }

    protected IAIState serveStew()
    {
        // TODO: Pick a hungry citizen.
        // TODO: walk to them
        // TODO: serve the stew.

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

            boolean gotSome = false;
            int stacklimit = 5;
            
            // Take all the bowls in this building.
            do
            {
                gotSome = InventoryUtils.transferItemStackIntoNextFreeSlotFromProvider(currentBowlPickupBuilding,
                    InventoryUtils.findFirstSlotInProviderNotEmptyWith(currentBowlPickupBuilding,
                        stack -> stack != null && ItemStack.isSameItem(stack, new ItemStack(NullnessBridge.assumeNonnull(Items.BOWL)))),
                    worker.getInventoryCitizen());

                stacklimit--;
            } while (gotSome && stacklimit > 0);

            if (gotSome)
            {
                currentBowlPickupBuilding = null;
                return DECIDE;
            }

            currentBowlPickupBuilding = null;
        }

        for (Map.Entry<BlockPos, IBuilding> buildingEntry : building.getColony().getBuildingManager().getBuildings().entrySet())
        {
            // Don't take bowls from these buildings.
            if (buildingEntry.getValue() instanceof BuildingKitchen 
                || buildingEntry.getValue() instanceof IWareHouse
            ) {
                continue;
            }

            if (InventoryUtils.hasItemInProvider(buildingEntry.getValue(), stack -> stack != null && ItemStack.isSameItem(stack, new ItemStack(NullnessBridge.assumeNonnull(Items.BOWL)))))
            {
                job.resetBowlCounter();
                currentBowlPickupBuilding = buildingEntry.getValue();
                return StewmelierState.COLLECT_BOWLS;
            }
        }

        job.tickNoBowls();

        if (job.checkForBowlInteraction())
        {
            worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(MCTPInteractionInitializer.NO_BOWLS), ChatPriority.BLOCKING));
        }

        return DECIDE;
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
        return walkToSafePos(safeStewModule().getStewpotLocation());
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
