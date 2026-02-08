package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.ModTags;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.OutpostExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.OutpostShipmentTracking;
import com.deathfrog.mctradepost.core.colony.jobs.JobScout;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.OutpostRequestResolver;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.util.WorkerUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer.DISCONNECTED_OUTPOST;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;

public class EntityAIWorkScout extends AbstractEntityAIInteract<JobScout, BuildingOutpost>
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
    protected int exceptionTimer = 1;

    private final static VisibleCitizenStatus SCOUTING = new VisibleCitizenStatus(
        ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/outpost_scouting.png"),
        "com.mctradepost.gui.visiblestatus.scouting");

    public enum ScoutStates implements IAIState
    {
        HANDLE_OUTPOST_REQUESTS, MAKE_DELIVERY, ORDER_FOOD, RETURN_PRODUCTS, UNLOAD_INVENTORY, BUILD;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    Collection<IToken<?>> resolverBlacklist = null;
    protected ItemStack currentDeliverableSatisfier = null;
    protected IRequest<?> currentDeliverableRequest = null;

    /**
     * Current goto path
     */
    PathResult<?> gotoPath = null;

    @SuppressWarnings("unchecked")
    public EntityAIWorkScout(@NotNull final JobScout job)
    {
        super(job);

        super.registerTargets(new AITarget<IAIState>(IDLE, START_WORKING, 10),
            new AITarget<IAIState>(START_WORKING, DECIDE, 10),
            new AITarget<IAIState>(DECIDE, this::decide, 10),
            new AITarget<IAIState>(ScoutStates.HANDLE_OUTPOST_REQUESTS, this::handleOutpostRequests, 10),
            new AITarget<IAIState>(ScoutStates.UNLOAD_INVENTORY, this::unloadInventory, 10),
            new AITarget<IAIState>(ScoutStates.MAKE_DELIVERY, this::makeDelivery, 10),
            new AITarget<IAIState>(ScoutStates.ORDER_FOOD, this::orderFood, 10),
            new AITarget<IAIState>(ScoutStates.RETURN_PRODUCTS, this::returnProducts, 10),
            new AITarget<IAIState>(WANDER, this::wander, 10)
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
            worker.getCitizenData().setVisibleStatus(SCOUTING);
            return ScoutStates.MAKE_DELIVERY;
        }

        if (building.isDisconnected())
        {
            String buildingDisplayName = building.getBuildingDisplayName();

            worker.getCitizenData()
                .triggerInteraction(new StandardInteraction(
                    Component.translatable(DISCONNECTED_OUTPOST, Component.translatable(buildingDisplayName + "")),
                    Component.translatable(DISCONNECTED_OUTPOST),
                    ChatPriority.BLOCKING));
        }

        if (outpostCooldown-- <= 0)
        {
            outpostCooldown = OUTPOST_COOLDOWN_TIMER;
            worker.getCitizenData().setVisibleStatus(SCOUTING);
            return ScoutStates.HANDLE_OUTPOST_REQUESTS;
        }

        if (currentDeliverableRequest == null && !getInventory().isEmpty())
        {
            return ScoutStates.UNLOAD_INVENTORY;
        }

        long today = building.getColony().getDay();
        if (today > lastFoodCheck)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Checking outpost food status for day {} (last checked on {}) in colony {}.",
                    today,
                    lastFoodCheck,
                    building.getColony().getName()));

            worker.getCitizenData().setVisibleStatus(SCOUTING);
            return ScoutStates.ORDER_FOOD;
        }

        if (today > lastReturnProductsCheck)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Checking items to ship back on day {} (last checked on {}) in colony {}.",
                    today,
                    lastReturnProductsCheck,
                    building.getColony().getName()));
            lastReturnProductsCheck = building.getColony().getDay();

            worker.getCitizenData().setVisibleStatus(SCOUTING);
            return ScoutStates.RETURN_PRODUCTS;
        }

        if (worker.getRandom().nextInt(100) < Constants.WANDER_CHANCE)
        {
            return WANDER;
        }

        return DECIDE;
    }

    /**
     * Orders food for the outpost if it is needed. The scout will attempt to order food from the nearest warehouse. If the scout is
     * successful in ordering food, the lastFoodCheck variable will be updated to the current day.
     * 
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
     * outpost building and returning any products that are found to the connected station. If no connected station is found or no
     * outpost export module is found, the AI will transition back to the DECIDE state. The lastReturnProductsCheck variable will be
     * updated to the current day if the AI is successful in returning products.
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

        Set<Item> protectedCrops = new HashSet<>();

        for (BlockPos workLocation : building.getWorkBuildings())
        {
            IBuilding outpostBuilding = building.getColony().getServerBuildingManager().getBuilding(workLocation);

            if (outpostBuilding != null)
            {
                for (int i = 0; i < outpostBuilding.getItemHandlerCap().getSlots(); i++)
                {
                    final IItemHandler handler = outpostBuilding.getItemHandlerCap();
                    ItemStack inSlot = handler.getStackInSlot(i);
                    if (inSlot.isEmpty()) continue;

                    ItemStorage candidate = new ItemStorage(inSlot.copyWithCount(1), 1, true, true);

                    if (module.isItemInList(candidate))
                    {
                        // The first stack of anything plantable in the outpost will not be returned so it remains available to the farmer for planting.
                        if (inSlot.is(ModTags.ITEMS.OUTPOST_CROPS_TAG) && !protectedCrops.contains(inSlot.getItem()))
                        {
                            protectedCrops.add(inSlot.getItem());
                            continue;
                        }

                        int toTake = inSlot.getCount();
                        // This actually removes from the inventory
                        ItemStack extracted = handler.extractItem(i, toTake, /*simulate*/ false);

                        if (!extracted.isEmpty())
                        {
                            // Pass the *extracted* items forward so counts are exact
                            ItemStorage shipped = new ItemStorage(extracted.copy());
                            connectedStation.initiateReturn(new StationData(building), shipped, returnCount);
                            returnCount++;
                        }
                    }
                }
            }
        }

        if (returnCount > 0)
        {
            incrementActionsDoneAndDecSaturation();
            worker.getCitizenExperienceHandler().addExperience(SMALL_SCOUT_XP);
        }

        return DECIDE;
    }

    /**
     * Makes the scout wander around the outpost. The scout will walk to a random position within the outpost's boundaries. Once the
     * scout has reached the random position, it will return to the DECIDE state.
     * 
     * @return The DECIDE state, which will cause the scout to decide what to do next.
     */
    public IAIState wander()
    {
        EntityNavigationUtils.walkToRandomPos(worker, 10, Constants.DEFAULT_SPEED);
        return DECIDE;
    }

    /**
     * Unloads the worker's inventory into the outpost's inventory. If the outpost's inventory is full, it will not unload the chest.
     * If the outpost's inventory is not full, it will unload the chest and return to the DECIDE state. If the outpost's inventory is
     * full, it will return to the INVENTORY_FULL state.
     * 
     * @return The next AI state to transition to.
     */
    public IAIState unloadInventory()
    {
        if (building.getColony().getWorld().isClientSide())
        {
            return getState();
        }

        boolean canhold = true;

        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unloading junk from my inventory. Scout at {}", building.getPosition()));

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
                ItemStack invStack = workerInventory.getStackInSlot(i);

                if (!invStack.isEmpty() && canhold)
                {
                    canhold = InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(workerInventory, i, building);

                    if (!canhold)
                    {
                        break;
                    }
                }
            }
        }
        else
        {
            LOGGER.warn("No inventory handling found on building Scout is attempting to unload into. Scout at {}", building.getPosition());
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
     * Analyzes all requests from connected outposts and attempts to resolve them. If a request is in the CREATED or ASSIGNED state, it
     * checks if the outpost has a qualifying item to ship. If so, it initiates a shipment and sets the request state to RESOLVED. If
     * no qualifying item is found, it echoes the request to the stationmaster and sets the request state to ASSIGNED. If the request
     * is already in progress, it does nothing. This method is called every tick by the outpost's AI.
     *
     * @return the next AI state to transition to.
     */
    public IAIState handleOutpostRequests()
    {
        final IStandardRequestManager requestManager = (IStandardRequestManager) building.getColony().getRequestManager();
        IRequestResolver<?> currentlyAssignedResolver = null;

        final Collection<IRequest<?>> openRequests = building.getOutstandingOutpostRequests(true);

        if (openRequests == null || openRequests.isEmpty())
        {
            return DECIDE;
        }

        for (final IRequest<?> request : openRequests)
        {
            try
            {
                currentlyAssignedResolver = requestManager.getResolverForRequest(request.getId());
            }
            catch (IllegalArgumentException e)
            {
                LOGGER.warn("Colony {} Scout - Unable to get resolver for request {} ({})", building.getColony().getID(), request, e.getLocalizedMessage());
                // request.setState(requestManager, RequestState.CANCELLED);
                // building.getColony().getRequestManager().updateRequestState(request.getId(), RequestState.CANCELLED);
                continue;
            }

            if (currentlyAssignedResolver instanceof OutpostRequestResolver && currentlyAssignedResolver.getLocation()
                .getInDimensionLocation()
                .equals(building.getLocation().getInDimensionLocation()))
            {
                OutpostShipmentTracking shipmentTracking = building.trackingForRequest(request);

                if (shipmentTracking == null || shipmentTracking.getState() == OutpostOrderState.DELIVERED || shipmentTracking.getState() == OutpostOrderState.CANCELLED)
                {
                    continue;
                }

                TraceUtils.dynamicTrace(TRACE_OUTPOST,
                    () -> LOGGER.info("Colony {} Scout - Checking if satisfiable from the building: {} with state {}",
                        building.getColony().getID(),
                        request.getLongDisplayString(),
                        request.getState()));
                // Check if the building has a qualifying item and ship it if so. Determine state change of request status.
                ItemStorage satisfier = building.inventorySatisfiesRequest(request, true);
                
                if (satisfier == null)
                {
                    int deliveries = request.getDeliveries() == null ? 0 : request.getDeliveries().size();
                    TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Colony {} Scout - Nothing in building matches request: {}; {} deliveries in progress.", 
                        building.getColony().getID(), request.getLongDisplayString(), deliveries));
                    continue;
                }

                TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Colony {} Scout - We have something to deliver: {}", building.getColony().getID(), satisfier));
                Item satisfyingItem = satisfier.getItemStack().getItem();

                if (satisfyingItem == null || satisfyingItem.equals(Items.AIR))
                {
                    continue;
                }

                currentDeliverableSatisfier = new ItemStack(satisfyingItem, satisfier.getAmount());
                currentDeliverableRequest = request;
                shipmentTracking.setState(OutpostOrderState.RECEIVED);

                // Once we have the necessary thing in the outpost, we can mark all remaining outstanding children as no longer
                // needed, and remove them.
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

        currentDeliverableSatisfier = null;
        currentDeliverableRequest = null;

        return DECIDE;
    }

    /**
     * Attempt to deliver the item stored in currentDeliverableSatisfier to the connected outpost. If the outpost is not accessible,
     * the scout will wait for 2 ticks and then retry. If the outpost is accessible, the scout will attempt to transfer the item into
     * the outpost's inventory. If the transfer is successful, the scout will then walk to the outpost and unload the item.
     * 
     * @return the next AI state to transition to.
     */
    public IAIState makeDelivery()
    {

        ItemStack localSatisfier = currentDeliverableSatisfier.copy();

        if (localSatisfier == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Colony {} Scout - No current deliverable satisfier in makeDelivery.", building.getColony().getID()));

            currentDeliverableRequest = null;
            currentDeliverableSatisfier = null;
            return DECIDE;
        }

        OutpostShipmentTracking tracking = building.trackingForRequest(currentDeliverableRequest);
        IBuilding deliveryTarget = targetBuildingForRequest(currentDeliverableRequest);

        if (deliveryTarget == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Colony {} Scout - No delivery target for delivery: {} - marking complete.", building.getColony().getID(), localSatisfier));
            // If for some reason we don't have a delivery target, assume the shipment is in its final destination.
            markDeliveryComplete(currentDeliverableRequest, tracking, false);
            return DECIDE;
        }

        if (deliveryTarget != null &&
            deliveryTarget.getLocation().getInDimensionLocation().equals(building.getLocation().getInDimensionLocation()))
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Colony {} Scout - Delivery target is the outpost. No final step needed: {} - marking complete.", building.getColony().getID(), localSatisfier));
            markDeliveryComplete(currentDeliverableRequest, tracking, true);
            deliveryTarget.markDirty();
            return DECIDE;
        }

        if (tracking == null)
        {
            // Somewhere along the line something got cancelled, and we aren't expecting this any more...
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Colony {} Scout - No tracking for delivery: {} - cancelling delivery.", building.getColony().getID(), localSatisfier));
            currentDeliverableSatisfier = null;
            currentDeliverableRequest = null;

            return DECIDE;
        }

        final OutpostShipmentTracking trackingForLog = tracking;
        TraceUtils.dynamicTrace(TRACE_OUTPOST,
            () -> LOGGER.info("Colony {} Scout - Making delivery of {} to {} (tracking status {})",
                building.getColony().getID(),
                localSatisfier,
                deliveryTarget.getBuildingDisplayName(),
                trackingForLog.getState()));

        // Walk to the destination building with the needed item.
        if (tracking.getState() == OutpostOrderState.READY_FOR_DELIVERY)
        {
            if (!EntityNavigationUtils.walkToBuilding(this.worker, deliveryTarget) && !getInventory().isEmpty())
            {
                TraceUtils.dynamicTrace(TRACE_OUTPOST,
                    () -> LOGGER.info("Colony {} Scout - Walking to {} with delivery: {}",
                        building.getColony().getID(),
                        deliveryTarget.getBuildingDisplayName(),
                        localSatisfier));
                return getState();
            }

            // Transfer the item into the target building's inventory once we've arrived.
            int slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(),
                stack -> stack != null && ItemStack.isSameItem(stack, localSatisfier));

            if (slot >= 0)
            {
                try
                {
                    InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, deliveryTarget);
                    deliveryTarget.markDirty();
                }
                catch (NullPointerException e)
                {
                    LOGGER.warn("Error inserting into target building {}: {}",
                        deliveryTarget.getBuildingDisplayName(),
                        e.getLocalizedMessage());
                }

                worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
                
                // markDeliveryComplete(currentDeliverableRequest, tracking, true);
                deliveryTarget.getColony().getRequestManager().overruleRequest(currentDeliverableRequest.getId(), localSatisfier);
                
                deliveryTarget.markDirty();
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
        boolean moved = InventoryUtils.transferItemStackIntoNextFreeSlotFromItemHandler(building.getItemHandlerCap(), stack -> stack != null && ItemStack.isSameItem(stack, localSatisfier), localSatisfier.getCount(), worker.getInventoryCitizen());

        if (moved)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Item to deliver ({}) found and retrieved.", localSatisfier));

            int held = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(),
                s -> s != null && ItemStack.isSameItem(s, localSatisfier));

            if (held >= 0)
            {
                ItemStack stackInSlot = worker.getInventoryCitizen().getStackInSlot(held);

                if (!stackInSlot.isEmpty())
                {
                    worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(stackInSlot.copy()));
                }
            }
            else
            {
                worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
            }

            tracking.setState(OutpostOrderState.READY_FOR_DELIVERY);

            final ItemStorage deliveryItem = new ItemStorage(localSatisfier, localSatisfier.getCount());
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Scout delivering item (final leg) {}.", deliveryItem));
        }
        else
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Scout unable to find {} to deliver (final leg), despite a reported match.",
                    currentDeliverableSatisfier));
            currentDeliverableSatisfier = null;
            currentDeliverableRequest = null;
        }

        return DECIDE;
    }

    /**
     * Marks the given shipment tracking as having been successfully delivered. Resets the current deliverable satisfier and request.
     * Increments the actions done and decrements the saturation. Awards the scout experience for successfully delivering the item.
     * 
     * @param tracking the shipment tracking to mark as delivered
     */
    protected void markDeliveryComplete(IRequest<?> request, OutpostShipmentTracking tracking, boolean successful)
    {
        tracking.setState(successful ? OutpostOrderState.DELIVERED : OutpostOrderState.CANCELLED);

        TraceUtils.dynamicTrace(TRACE_OUTPOST,
            () -> LOGGER.info("Delivery of {} (tracking status {}, request state {}) has reached its destination.",
                currentDeliverableSatisfier,
                tracking.getState(),
                request.getState()
            ));

        if (request != null)
        {
            if (request.getState() == RequestState.IN_PROGRESS || request.getState() == RequestState.FOLLOWUP_IN_PROGRESS)
            {
                building.getColony().getRequestManager().updateRequestState(request.getId(), successful ? RequestState.RESOLVED : RequestState.FAILED);
            }
        }

        incrementActionsDoneAndDecSaturation();
        worker.getCitizenExperienceHandler().addExperience(DEFAULT_SCOUT_XP);

        currentDeliverableSatisfier = null;
        currentDeliverableRequest = null;
    }

    /**
     * Returns the building associated with the given resolver token. If the request manager is null or the resolver is null, returns
     * null. Otherwise, returns the building associated with the location of the given resolver token.
     * 
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
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER
                    .info("No target building for request {} - {} can be found - no request location is associated with this request.", request.getId(), request.getLongDisplayString()));
            return null;
        }

        return building.getColony().getServerBuildingManager().getBuilding(request.getRequester().getLocation().getInDimensionLocation());
    }

    /**
     * Attempts to order food for the outpost workers using the nearest restaurant.
     * 
     * @return true if food was ordered, false otherwise.
     */
    public boolean orderFoodForOutpost()
    {
        BlockPos restaurantPos = building.getColony().getServerBuildingManager().getBestBuilding(building.getPosition(), BuildingCook.class);
        boolean didOrder = false;

        if (restaurantPos == null || restaurantPos.equals(BlockPos.ZERO))
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unable to find a restaurant in the colony for food ordering."));
            return false;
        }

        IBuilding restaurant = building.getColony().getServerBuildingManager().getBuilding(restaurantPos);

        if (restaurant == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Unable to find a restaurant at position {} in the colony for food ordering.", restaurantPos));
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
            IBuilding outpostWorksite = building.getColony().getServerBuildingManager().getBuilding(outpostWorkPos);

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
                        
                        if (citizen.getJob() != null)
                        {
                            try
                            {
                                boolean outstandingRequest = outpostWorksite.isItemStackInRequest(toOrder.getItemStack());

                                if (!outstandingRequest)
                                {
                                    outpostWorksite.createRequest(citizen, requestStack, true);
                                    didOrder = true;
                                }
                            }
                            catch (IllegalArgumentException e)
                            {
                                TraceUtils.dynamicTrace(TRACE_OUTPOST,
                                    () -> LOGGER.info("Request system error ordering food for outpost."));
                            }
                        }
                    }
                    else
                    {
                        TraceUtils.dynamicTrace(TRACE_OUTPOST,
                            () -> LOGGER.info("Unable to find food to request for {}.", citizen.getName()));
                        // IDEA: Consider an alert here to warn about food availability.
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


    @Override
    protected boolean mineBlock(@NotNull final BlockPos blockToMine, @NotNull final BlockPos safeStand)
    {
        boolean canmine = false;

        try
        {
            canmine = mineBlock(blockToMine,
                safeStand,
                true,
                !IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(NullnessBridge.assumeNonnull(blockToMine))),
                null);
        }
        catch (IllegalArgumentException e)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.warn("MineColonies request system error while mining block at {}: {}", blockToMine, e));
        }


        return canmine;
    }

    @Override
    protected int getMostEfficientTool(@NotNull BlockState target, BlockPos pos)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.warn("Finding most efficient tool for block {} at {}", target, pos.toShortString()));

        int slot =  super.getMostEfficientTool(target, pos);

        // If we found a tool, use it
        if (slot >= 0)
        {
            return slot;
        }

        ServerLevel localWorld = world;

        if (localWorld == null || pos == null || BlockPos.ZERO.equals(pos))
        {
            return NO_TOOL;
        }

        // Otherwise, try to magic one out of the outpost inventory - and then use it.
        for (BlockPos outpostWorkPos : building.getWorkBuildings())
        {
            IBuilding outpostWorksite = building.getColony().getServerBuildingManager().getBuilding(outpostWorkPos);

            if (outpostWorksite != null)
            {
                final EquipmentTypeEntry toolType = WorkerUtil.getBestToolForBlock(target, target.getDestroySpeed(localWorld, pos), building, world, pos);
                final int required = WorkerUtil.getCorrectHarvestLevelForBlock(target);

                if (toolType == ModEquipmentTypes.none.get())
                {
                    return NO_TOOL;
                }

                int bestSlot = -1;
                int bestLevel = Integer.MAX_VALUE;
                @NotNull final IItemHandler inventory = outpostWorksite.getItemHandlerCap();
                final int maxToolLevel = worker.getCitizenColonyHandler().getWorkBuilding().getMaxEquipmentLevel();

                for (int i = 0; i < inventory.getSlots(); i++)
                {
                    final ItemStack item = inventory.getStackInSlot(i);
                    final int miningLevel = toolType.getMiningLevel(item);

                    if (miningLevel > -1 && miningLevel >= required && miningLevel < bestLevel && ItemStackUtils.verifyEquipmentLevel(item, miningLevel, required, maxToolLevel))
                    {
                        bestSlot = i;
                        bestLevel = miningLevel;
                    }
                }

                if (bestSlot >= 0)
                {
                    @SuppressWarnings("unused")
                    boolean canhold = InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(inventory, bestSlot, worker.getInventoryCitizen());
                    TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.warn("Took one from outpost inventory at {} ({})", outpostWorkPos, outpostWorksite.getBuildingDisplayName()));

                    break;
                }
            }   
        }

        return super.getMostEfficientTool(target, pos);
    }

    /**
     * After a request to pick up items, checks if there are any outstanding requests that can be satisfied with items in the building
     * inventory. If any items are found, the AI will pick them up and return to the build state. If no items are found, the AI will
     * return to the decide state.
     * 
     * @return The next AI state to transition to.
     */
    @Override
    public IAIState afterRequestPickUp()
    {
        return DECIDE;
    }

    @Override
    public IAIState afterDump()
    {
        return ScoutStates.RETURN_PRODUCTS; 
    }

    @Override
    public boolean canGoIdle()
    {
        // Work at the outpost is never done...
        return false;
    }
}
