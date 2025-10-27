package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.OutpostExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.OutpostShipmentTracking;
import com.deathfrog.mctradepost.core.colony.jobs.JobScout;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.OutpostRequestResolver;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.mojang.logging.LogUtils;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.Collection;
import static com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer.DISCONNECTED_OUTPOST;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;

public class EntityAIWorkScout extends AbstractEntityAICrafting<JobScout, BuildingOutpost>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static final int OUTPOST_COOLDOWN_TIMER = 10;
    protected static int outpostCooldown = OUTPOST_COOLDOWN_TIMER;

    protected static final int SMALL_SCOUT_XP = 1;
    protected static final int DEFAULT_SCOUT_XP = 2;
    protected static final int FOOD_ORDERING_THRESHOLD = 3;
    protected static final int FOOD_ORDERING_SIZE = 8;

    protected long lastFoodCheck = 0;
    protected long lastReturnProductsCheck = 0;

    public enum ScoutStates implements IAIState
    {
        HANDLE_OUTPOST_REQUESTS,
        MAKE_DELIVERY,
        ORDER_FOOD,
        RETURN_PRODUCTS,
        UNLOAD_INVENTORY;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    Collection<IToken<?>> resolverBlacklist = null;
    protected ItemStack currentDeliverableSatisfier = null;
    protected IRequest<?> currentDeliverableRequest = null;


    @SuppressWarnings("unchecked")
    public EntityAIWorkScout(@NotNull final JobScout job)
    {
        super(job);

        super.registerTargets(
            new AITarget<IAIState>(IDLE, START_WORKING, 1),
            new AITarget<IAIState>(DECIDE, this::decide, 10),
            new AITarget<IAIState>(ScoutStates.HANDLE_OUTPOST_REQUESTS, this::handleOutpostRequests, 10),
            new AITarget<IAIState>(ScoutStates.UNLOAD_INVENTORY, this::unloadInventory, 10),
            new AITarget<IAIState>(ScoutStates.MAKE_DELIVERY, this::makeDelivery, 10),
            new AITarget<IAIState>(ScoutStates.ORDER_FOOD, this::orderFood, 10),
            new AITarget<IAIState>(ScoutStates.RETURN_PRODUCTS, this::returnProducts, 10),
            new AITarget<IAIState>(WANDER, this::wander, 10)

            // TODO: Order Food
            // TODO: Find child delivery requests and turn them into station requests.
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingOutpost> getExpectedBuildingClass()
    {
        Class<BuildingOutpost> buildingClass = BuildingOutpost.class;
        return buildingClass;
    }

    public IAIState decide()
    {
        // LOGGER.info("Scout deciding what to do.");

        if (currentDeliverableSatisfier != null)
        {
            return ScoutStates.MAKE_DELIVERY;
        }

        if (building.isDisconnected())
        {
            worker.getCitizenData().triggerInteraction(
                new StandardInteraction(Component.translatable(DISCONNECTED_OUTPOST, Component.translatable(building.getBuildingDisplayName())),
                    Component.translatable(DISCONNECTED_OUTPOST),
                    ChatPriority.BLOCKING));

        }

        if (outpostCooldown-- <= 0)
        {
            outpostCooldown = OUTPOST_COOLDOWN_TIMER;
            return ScoutStates.HANDLE_OUTPOST_REQUESTS;
        }

        if (currentDeliverableRequest == null && !getInventory().isEmpty())
        {
            return ScoutStates.UNLOAD_INVENTORY;
        }

        long today = building.getColony().getDay();
        if (today > lastFoodCheck)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Checking outpost food status for day {} in colony {}.", today, building.getColony().getName()));
            return ScoutStates.ORDER_FOOD;
        }


        if (today > lastReturnProductsCheck)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Checking items to ship back on day {} in colony {}.", today, building.getColony().getName()));
            return ScoutStates.RETURN_PRODUCTS;
        }


        if (worker.getRandom().nextInt(100) < Constants.WANDER_CHANCE)
        {
            return WANDER;
        }

        return DECIDE;
    }

    /**
     * Orders food for the outpost if it is needed. The scout will attempt to order food from the nearest warehouse.
     * If the scout is successful in ordering food, the lastFoodCheck variable will be updated to the current day.
     * @return The DECIDE state, which will cause the scout to decide what to do next.
     */
    public IAIState orderFood()
    {
        if (orderFoodForOutpost())
        {
            lastFoodCheck = building.getColony().getDay();
        }

        return DECIDE;
    }


    /**
     * Initiates the return of products from the outpost back to the connected station. This AIState is responsible for checking each
     * outpost building and returning any products that are found to the connected station. If no connected station is found or
     * no outpost export module is found, the AI will transition back to the DECIDE state. The lastReturnProductsCheck variable will be updated
     * to the current day if the AI is successful in returning products.
     *
     * @return The DECIDE state, which will cause the scout to decide what to do next.
     */
    public IAIState returnProducts()
    {
        final OutpostExportModule module = building.getModule(MCTPBuildingModules.OUTPOST_EXPORTS);

        if (module == null)
        {
            LOGGER.error("No outpost export module found!");
            return DECIDE;
        }

        BuildingStation connectedStation = building.getConnectedStation();

        if (connectedStation == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("No connected station found for outpost."));
            return DECIDE;
        }
        
        int returnCount = 0;

        for (BlockPos workLocation : building.getWorkBuildings())
        {
            IBuilding outpostBuilding = building.getColony().getBuildingManager().getBuilding(workLocation);

            if (outpostBuilding != null)
            {
                
                for (int i = 0; i < outpostBuilding.getItemHandlerCap().getSlots(); i++)
                {
                    final IItemHandler handler = outpostBuilding.getItemHandlerCap();
                    ItemStack inSlot = handler.getStackInSlot(i);
                    if (inSlot.isEmpty()) continue;

                    // If your list check shouldn't depend on count, normalize to count=1
                    ItemStorage candidate = new ItemStorage(inSlot.copyWithCount(1));

                    if (module.isItemInList(candidate))
                    {
                        int toTake = inSlot.getCount();
                        // This actually removes from the inventory
                        ItemStack extracted = handler.extractItem(i, toTake, /*simulate*/ false);

                        if (!extracted.isEmpty())
                        {
                            // Pass the *extracted* items forward so counts are exact
                            ItemStorage shipped = new ItemStorage(extracted.copy());
                            connectedStation.initiateReturn(shipped, returnCount);
                            returnCount++;
                        }
                    }
                }
            }
        }

        lastReturnProductsCheck = building.getColony().getDay();

        if (returnCount > 0)
        {
            incrementActionsDoneAndDecSaturation();
            worker.getCitizenExperienceHandler().addExperience(SMALL_SCOUT_XP);
        }

        return DECIDE;
    }

    /**
     * Makes the scout wander around the outpost. The scout will walk to a random position within the outpost's boundaries.
     * Once the scout has reached the random position, it will return to the DECIDE state.
     * @return The DECIDE state, which will cause the scout to decide what to do next.
     */
    public IAIState wander()
    {
        EntityNavigationUtils.walkToRandomPos(worker, 10, Constants.DEFAULT_SPEED);
        return DECIDE;
    }

    /**
     * Unloads the worker's inventory into the outpost's inventory.
     * If the outpost's inventory is full, it will not unload the chest.
     * If the outpost's inventory is not full, it will unload the chest and return to the DECIDE state.
     * If the outpost's inventory is full, it will return to the INVENTORY_FULL state.
     * @return The next AI state to transition to.
     */
    public IAIState unloadInventory()
    {
        boolean canhold = true;

        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unloading junk from my inventory."));

        if (!walkToSafePos(building.getPosition()))
        {
            return getState();
        }

        IItemHandler itemHandler = building.getItemHandlerCap();
        if (itemHandler != null)
        {
            IItemHandler workerInventory = getInventory();

            for (int i = 0; i < workerInventory.getSlots(); i++)
            {
                ItemStack stackInChest = workerInventory.getStackInSlot(i);

                if (!stackInChest.isEmpty() && canhold)
                {
                    canhold = InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(workerInventory, i, building);
                }
            }

            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        else
        {
            LOGGER.warn("No inventory handling found on building Scout is attempting to unload into.");
        }

        if (canhold)
        {
            incrementActionsDoneAndDecSaturation();
            return DECIDE;
        }
        else
        {
            return INVENTORY_FULL;
        }
    }

    /**
     * Analyzes all requests from connected outposts and attempts to resolve them.
     * If a request is in the CREATED or ASSIGNED state, it checks if the outpost has a qualifying item to ship.
     * If so, it initiates a shipment and sets the request state to RESOLVED.
     * If no qualifying item is found, it echoes the request to the stationmaster and sets the request state to ASSIGNED.
     * If the request is already in progress, it does nothing.
     * This method is called every tick by the outpost's AI.
     *
     * @return the next AI state to transition to.
     */
    public IAIState handleOutpostRequests()
    {

        final IStandardRequestManager requestManager = (IStandardRequestManager) building.getColony().getRequestManager();
        IRequestResolver<?> currentlyAssignedResolver = null;
 
        final Collection<IRequest<?>> openRequests = building.getOutpostRequests();

        if (openRequests == null || openRequests.isEmpty())
        {
            return DECIDE;
        }

        for (final IRequest<?> request : openRequests)
        {
            try
            {
                currentlyAssignedResolver = requestManager.getResolverForRequest(request.getId());   
            } catch (IllegalArgumentException e)
            {
                LOGGER.warn("Unable to get resolver for request {} ({})", request, e.getLocalizedMessage());
                // request.setState(requestManager, RequestState.CANCELLED);
                building.getColony().getRequestManager().updateRequestState(request.getId(), RequestState.CANCELLED);
                continue;
            }


            if (currentlyAssignedResolver instanceof OutpostRequestResolver && currentlyAssignedResolver.getLocation().equals(building.getLocation()))
            {
                OutpostShipmentTracking shipmentTracking = building.trackingForRequest(request);

                TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Checking if satisfiable from the building: {} with state {}", request.getLongDisplayString(), request.getState()));
                // Check if the building has a qualifying item and ship it if so. Determine state change of request status.
                ItemStorage satisfier = building.inventorySatisfiesRequest(request);

                if (satisfier != null)
                {
                    TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("We have something to deliver: {}", satisfier));
                    
                    currentDeliverableSatisfier = satisfier.getItemStack().copy();
                    currentDeliverableRequest = request;
                    shipmentTracking.setState(OutpostOrderState.RECEIVED);

                    // Once we have the necessary thing in the outpost, we can mark all remaining outstanding children as no longer needed, and remove them.
                    /*
                    for (IToken<?> child : request.getChildren())
                    {
                        // IRequest<?> childRequest = requestManager.getRequestForToken(child);
                        requestManager.updateRequestState(child, RequestState.RESOLVED);
                        // request.removeChild(child);
                    }
                    */

                    return ScoutStates.MAKE_DELIVERY;
                }
            }
        }

        currentDeliverableSatisfier = null;
        currentDeliverableRequest   = null;

        return DECIDE;
    }

    /**
     * Attempt to deliver the item stored in currentDeliverableSatisfier to the connected outpost.
     * If the outpost is not accessible, the scout will wait for 2 ticks and then retry.
     * If the outpost is accessible, the scout will attempt to transfer the item into the outpost's inventory.
     * If the transfer is successful, the scout will then walk to the outpost and unload the item.
     * @return the next AI state to transition to.
     */
    public IAIState makeDelivery()
    {

        if (currentDeliverableSatisfier == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("No current deliverable satisfier in makeDelivery."));

            currentDeliverableRequest = null;
            return DECIDE;
        }

        OutpostShipmentTracking tracking = building.trackingForRequest(currentDeliverableRequest);
        IBuilding deliveryTarget = targetBuildingForRequest(currentDeliverableRequest);

        if (tracking == null)
        {
            // Somewhere along the line something got cancelled, and we aren't expecting this any more...
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("No tracking for delivery: {} - repairing.", currentDeliverableSatisfier));
            
            if (!getInventory().isEmpty())
            {
                tracking = new  OutpostShipmentTracking(OutpostOrderState.READY_FOR_DELIVERY);    
            }
            else
            {
                tracking = new  OutpostShipmentTracking(OutpostOrderState.DELIVERED);
            }
        }

        final OutpostShipmentTracking trackingForLog = tracking;
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Making delivery of {} to {} (tracking status {})", currentDeliverableSatisfier, deliveryTarget.getBuildingDisplayName(), trackingForLog.getState()));

        // Walk to the destination building with the needed item.
        if (tracking.getState() == OutpostOrderState.READY_FOR_DELIVERY)
        {
            if (!EntityNavigationUtils.walkToBuilding(this.worker, deliveryTarget) && !getInventory().isEmpty())
            {
                TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Walking to {} with delivery: {}", deliveryTarget.getBuildingDisplayName(), currentDeliverableSatisfier));
                return getState();
            }


            // Transfer the item into the target building's inventory once we've arrived.
            int slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), stack -> ItemStack.isSameItem(stack, currentDeliverableSatisfier));

            if (slot >= 0)
            {
                try
                {
                    InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, deliveryTarget);
                }
                catch (NullPointerException e)
                {
                    LOGGER.warn("Error inserting into target building {}: {}", deliveryTarget.getBuildingDisplayName(), e.getLocalizedMessage());
                }

                worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                tracking.setState(OutpostOrderState.DELIVERED);
                
                // IRequestManager requestManager = building.getColony().getRequestManager();
                // requestManager.updateRequestState(currentDeliverableRequest.getId(), RequestState.COMPLETED);

                incrementActionsDoneAndDecSaturation();
                worker.getCitizenExperienceHandler().addExperience(DEFAULT_SCOUT_XP);

                // currentDeliverableRequest.setState(requestManager, RequestState.COMPLETED);
                currentDeliverableSatisfier = null;
                currentDeliverableRequest = null;
            }
            else
            {
                // Somewhere along the way we lost the thing we were supposed to transfer. Reset and retry.
                tracking.setState(OutpostOrderState.RECEIVED);
                currentDeliverableSatisfier = null;
                currentDeliverableRequest = null;
            }
            return DECIDE;
        }

        // Walk to our home buliding to pick up an item.
        if (!EntityNavigationUtils.walkToBuilding(this.worker, building))
        {
            setDelay(2);
            return getState();
        }

        // Pick up the item.
        int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(building, stack -> ItemStack.matches(stack, currentDeliverableSatisfier));

        if (slot >= 0)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Item to deliver found in slot {}.", slot));


            ItemStorage deliveryItem = new ItemStorage(building.getItemHandlerCap().getStackInSlot(slot).copy());
            boolean moved = InventoryUtils.transferItemStackIntoNextFreeSlotFromProvider(building, slot, worker.getInventoryCitizen());

            if (moved)
            {
                int held = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(),
                    s -> ItemStack.matches(s, currentDeliverableSatisfier));
                if (held >= 0)
                {
                    worker.setItemInHand(InteractionHand.MAIN_HAND, worker.getInventoryCitizen().getStackInSlot(held));
                }
                else
                {
                    worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }

                tracking.setState(OutpostOrderState.READY_FOR_DELIVERY);
            }

            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Scout delivering item (final leg) {}.", deliveryItem));
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Scout unable to find {} to deliver (final leg), despite a reported match.", currentDeliverableSatisfier));
            currentDeliverableSatisfier = null;
            currentDeliverableRequest   = null;
        }

        return DECIDE;
    }

    /**
     * Returns the building associated with the given resolver token.
     * If the request manager is null or the resolver is null, returns null.
     * Otherwise, returns the building associated with the location of the given resolver token.
     * @param token The token of the resolver to find the building for.
     * @return The building associated with the given resolver token, or null if the request manager or resolver is null.
     */
    public IBuilding targetBuildingForRequest(IRequest<?> request)
    {

        if (request == null)
        {
            LOGGER.error("Null request? C'mon, man.");
            return null;
        }
        
        if (request.getRequester() == null)
        {
            LOGGER.error("Unable to obtain requester from the request manager.");
            return null;
        }

        if (request.getRequester().getLocation() == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("No target building for request can be found - no request location is associated with this request."));
            return null;
        }

        return building.getColony().getBuildingManager().getBuilding(request.getRequester().getLocation().getInDimensionLocation());
    }

    /**
     * Attempts to order food for the outpost workers using the nearest restaurant.
     * @return true if food was ordered, false otherwise.
     */
    public boolean orderFoodForOutpost()
    {
        BlockPos restaurantPos = building.getColony().getBuildingManager().getBestBuilding(building.getPosition(), BuildingCook.class);
        boolean didOrder = false;

        if (restaurantPos == null || restaurantPos.equals(BlockPos.ZERO))
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unable to find a restaurant in the colony for food ordering."));
            return false;
        }

        IBuilding restaurant = building.getColony().getBuildingManager().getBuilding(restaurantPos);

        if (restaurant == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unable to find a restaurant at position {}in the colony for food ordering.", restaurantPos));
            return false;
        }

        final RestaurantMenuModule module = restaurant.getModule(BuildingModules.RESTAURANT_MENU);

        if (module == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unable to find a restaurant menu module for food ordering."));
            return false;
        }

        for (BlockPos outpostWorkPos : building.getWorkBuildings())
        {
            IBuilding outpostWorksite = building.getColony().getBuildingManager().getBuilding(outpostWorkPos);

            for (ICitizenData citizen : outpostWorksite.getAllAssignedCitizen())
            {
                int foodCount = 0;
                final ItemStorage foodWeHave = FoodUtils.checkForFoodInBuilding(citizen, module.getMenu(), outpostWorksite);
                final ItemStorage foodAvailable = FoodUtils.checkForFoodInBuilding(citizen, module.getMenu(), restaurant);

                if (foodWeHave != null)
                {
                    foodCount = foodWeHave.getAmount();
                }

                if (foodCount <= FOOD_ORDERING_THRESHOLD)
                {
                    if (foodAvailable != null)
                    {
                        ItemStorage toOrder = new ItemStorage(foodAvailable.getItem(), FOOD_ORDERING_SIZE);
                        Stack requestStack = new Stack(toOrder);
                        outpostWorksite.createRequest(citizen, requestStack, true);  
                        didOrder = true; 
                    }
                    else
                    {
                        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unable to find food to request for {}.", citizen.getName()));
                        // TODO: Consider an alert here to warn about food availability.
                    }
                }
                else
                {
                    TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Already have enough food for {}.", citizen.getName()));
                }

            }
        }

        if (didOrder)
        {
            incrementActionsDoneAndDecSaturation();
            worker.getCitizenExperienceHandler().addExperience(SMALL_SCOUT_XP);
        }

        return true;
    }


    @Override
    public void tick()
    {
        super.tick();
    }
}
