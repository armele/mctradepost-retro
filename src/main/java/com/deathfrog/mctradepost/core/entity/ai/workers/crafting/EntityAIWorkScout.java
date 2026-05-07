package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.utils.BuilderBucket;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import static com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer.DISCONNECTED_OUTPOST;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;

public class EntityAIWorkScout extends AbstractEntityAIInteract<JobScout, BuildingOutpost>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static final int OUTPOST_COOLDOWN_TIMER = 10;
    protected static int outpostCooldown = OUTPOST_COOLDOWN_TIMER;

    protected static final int SMALL_SCOUT_XP = 1;
    protected static final int DEFAULT_SCOUT_XP = 2;
    protected static final int FOOD_DELIVERY_RESERVE = 2;
    protected static final int FOOD_ORDERING_THRESHOLD = 3;
    protected static final int FOOD_ORDERING_SIZE = 8;
    protected static final int BUILDER_SUPPORT_COOLDOWN_TIMER = 20;

    protected long lastFoodOrderCheck = 0;
    protected long lastFoodDeliveryCheck = 0;
    protected long lastReturnProductsCheck = 0;
    protected int exceptionTimer = 1;
    protected int builderSupportCooldown = 0;

    private final static VisibleCitizenStatus SCOUTING = new VisibleCitizenStatus(
        ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/outpost_scouting.png"),
        "com.mctradepost.gui.visiblestatus.scouting");

    public enum ScoutStates implements IAIState
    {
        HANDLE_OUTPOST_REQUESTS, MAKE_DELIVERY, ORDER_FOOD, DELIVER_FOOD, RETURN_PRODUCTS, UNLOAD_INVENTORY, PRE_STAGE_BUILDER_MATERIALS, BUILD;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    Collection<IToken<?>> resolverBlacklist = null;
    protected ItemStack currentDeliverableSatisfier = null;
    protected IRequest<?> currentDeliverableRequest = null;
    protected ItemStack currentFoodDeliveryStack = ItemStack.EMPTY;
    protected BlockPos currentFoodDeliveryTarget = null;

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
            new AITarget<IAIState>(ScoutStates.DELIVER_FOOD, this::deliverFoodForOutpost, 10),
            new AITarget<IAIState>(ScoutStates.RETURN_PRODUCTS, this::returnProducts, 10),
            new AITarget<IAIState>(ScoutStates.PRE_STAGE_BUILDER_MATERIALS, this::preStageBuilderMaterials, 10),
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

        if (builderSupportCooldown-- <= 0)
        {
            builderSupportCooldown = BUILDER_SUPPORT_COOLDOWN_TIMER;
            worker.getCitizenData().setVisibleStatus(SCOUTING);
            return ScoutStates.PRE_STAGE_BUILDER_MATERIALS;
        }

        long today = building.getColony().getDay();
        if (today > lastFoodOrderCheck)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Checking outpost food ordering status for day {} (last checked on {}) in colony {}.",
                    today,
                    lastFoodOrderCheck,
                    building.getColony().getName()));

            worker.getCitizenData().setVisibleStatus(SCOUTING);
            return ScoutStates.ORDER_FOOD;
        }

        if (today > lastFoodDeliveryCheck)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Checking outpost food delivery status for day {} (last checked on {}) in colony {}.",
                    today,
                    lastFoodDeliveryCheck,
                    building.getColony().getName()));

            worker.getCitizenData().setVisibleStatus(SCOUTING);
            return ScoutStates.DELIVER_FOOD;
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
     * successful in ordering food, the lastFoodOrderCheck variable will be updated to the current day.
     * 
     * @return The DECIDE state, which will cause the scout to decide what to do next.
     */
    public IAIState orderFood()
    {
        if (orderFoodForOutpost())
        {
            lastFoodOrderCheck = building.getColony().getDay();
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

            if (outpostBuilding != null && outpostBuilding.getBuildingLevel() > 0)
            {
                final IItemHandler handler = getSafeItemHandler(outpostBuilding, "return-product scan");
                if (handler == null)
                {
                    TraceUtils.dynamicTrace(TRACE_OUTPOST,
                        () -> LOGGER.info("Skipping return-product scan for {} because it has no item handler.",
                            outpostBuilding.getBuildingDisplayName()));
                    continue;
                }

                for (int i = 0; i < handler.getSlots(); i++)
                {
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
                            try
                            {
                                connectedStation.initiateReturn(new StationData(building), shipped, returnCount);
                            }
                            catch (NullPointerException e)
                            {
                                LOGGER.warn("Unable to initiate return of {} from {} to {} due to missing station data.",
                                    shipped,
                                    building.getBuildingDisplayName(),
                                    connectedStation.getBuildingDisplayName(),
                                    e);
                                continue;
                            }
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

        IItemHandler itemHandler = getSafeItemHandler(building, "scout inventory unload");
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
                final IRequest<?> liveRequest = getActiveRequest(request);
                if (liveRequest == null)
                {
                    continue;
                }

                OutpostShipmentTracking shipmentTracking = building.getActiveTrackingForRequest(liveRequest);

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
                ItemStorage satisfier = building.inventorySatisfiesRequest(liveRequest, true);
                
                if (satisfier == null)
                {
                    int deliveries = request.getDeliveries() == null ? 0 : request.getDeliveries().size();
                    TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Colony {} Scout - Nothing in building matches request: {}; {} deliveries in progress.", 
                        building.getColony().getID(), request.getLongDisplayString(), deliveries));
                    continue;
                }

                TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Colony {} Scout - We have something to deliver: {}", building.getColony().getID(), satisfier));
                final ItemStack satisfyingStack = satisfier.getItemStack().copyWithCount(satisfier.getAmount());

                if (satisfyingStack.isEmpty())
                {
                    continue;
                }

                currentDeliverableSatisfier = satisfyingStack;
                currentDeliverableRequest = liveRequest;
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
        if (currentDeliverableSatisfier == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Colony {} Scout - No current deliverable satisfier in makeDelivery.", building.getColony().getID()));
            currentDeliverableRequest = null;
            currentDeliverableSatisfier = null;
            return DECIDE;
        }

        final IRequest<?> liveRequest = getActiveRequest(currentDeliverableRequest);
        if (liveRequest == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Colony {} Scout - Current delivery request is no longer registered: {}",
                    building.getColony().getID(),
                    currentDeliverableRequest == null ? "null" : currentDeliverableRequest.getId()));
            currentDeliverableRequest = null;
            currentDeliverableSatisfier = null;
            return DECIDE;
        }

        currentDeliverableRequest = liveRequest;
        ItemStack localSatisfier = currentDeliverableSatisfier.copy();

        if (localSatisfier == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Colony {} Scout - No current deliverable satisfier in makeDelivery.", building.getColony().getID()));

            currentDeliverableRequest = null;
            currentDeliverableSatisfier = null;
            return DECIDE;
        }

        OutpostShipmentTracking tracking = building.getActiveTrackingForRequest(currentDeliverableRequest);
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
                stack -> stack != null && ItemStack.isSameItemSameComponents(stack, localSatisfier));

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
                
                try
                {
                    deliveryTarget.getColony().getRequestManager().overruleRequest(currentDeliverableRequest.getId(), localSatisfier);
                }
                catch (IllegalArgumentException e)
                {
                    TraceUtils.dynamicTrace(TRACE_OUTPOST,
                    () -> LOGGER.warn("Colony {} Scout - Delivery request {} became stale before final overrule.",
                        building.getColony().getID(),
                        currentDeliverableRequest.getId(),
                        e));
                    currentDeliverableSatisfier = null;
                    currentDeliverableRequest = null;
                    return DECIDE;
                }
                markDeliveryComplete(currentDeliverableRequest, tracking, true);
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
        final IItemHandler buildingInventory = getSafeItemHandler(building, "delivery pickup");
        if (buildingInventory == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Scout unable to collect {} because the outpost inventory is temporarily unavailable.",
                    localSatisfier));
            currentDeliverableSatisfier = null;
            currentDeliverableRequest = null;
            return DECIDE;
        }

        boolean moved = InventoryUtils.transferItemStackIntoNextFreeSlotFromItemHandler(buildingInventory, stack -> stack != null && ItemStack.isSameItemSameComponents(stack, localSatisfier), localSatisfier.getCount(), worker.getInventoryCitizen());

        if (moved)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Item to deliver ({}) found and retrieved.", localSatisfier));

            int held = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(),
                s -> s != null && ItemStack.isSameItemSameComponents(s, localSatisfier));

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
     * Looks ahead at any outpost builder worksite buckets and moves locally available materials into the builder hut before the
     * builder reaches a blocking need. Shortages are still routed through the builder hut's normal request flow.
     *
     * @return the next AI state to transition to.
     */
    public IAIState preStageBuilderMaterials()
    {
        if (building.getColony().getWorld().isClientSide())
        {
            return DECIDE;
        }

        final AbstractBuildingStructureBuilder builderTarget = findBuilderToSupport();
        if (builderTarget == null)
        {
            return DECIDE;
        }

        if (!EntityNavigationUtils.walkToBuilding(this.worker, builderTarget))
        {
            return getState();
        }

        final ICitizenData builderCitizen = builderTarget.getAllAssignedCitizen().stream().findFirst().orElse(null);
        if (builderCitizen == null)
        {
            return DECIDE;
        }

        boolean stagedAnything = false;
        final BuilderBucket currentBucket = builderTarget.getRequiredResources();
        stagedAnything |= requestAndStageBuilderBucket(builderTarget, builderCitizen, currentBucket, "current");

        if (currentBucket == null || isBucketSatisfiedInBuilding(builderTarget, currentBucket))
        {
            stagedAnything |= requestAndStageBuilderBucket(builderTarget, builderCitizen, builderTarget.getNextBucket(), "next");
        }

        if (stagedAnything)
        {
            incrementActionsDoneAndDecSaturation();
            worker.getCitizenExperienceHandler().addExperience(SMALL_SCOUT_XP);
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
        reconcileDependentRequests(request, successful);

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
     * Reconciles any still-open child requests once the outpost can satisfy a parent locally.
     * This prevents prerequisite shipment requests from surviving after the parent request is already fulfilled.
     *
     * @param request the fulfilled or failed parent request.
     * @param successful whether the parent request completed successfully.
     */
    protected void reconcileDependentRequests(IRequest<?> request, boolean successful)
    {
        if (request == null || request.getChildren() == null || request.getChildren().isEmpty())
        {
            return;
        }

        final RequestState desiredState = successful ? RequestState.CANCELLED : RequestState.FAILED;

        for (IToken<?> child : request.getChildren())
        {
            if (child == null)
            {
                continue;
            }

            final IRequest<?> childRequest = building.getColony().getRequestManager().getRequestForToken(child);
            if (childRequest == null)
            {
                continue;
            }

            if (childRequest.getState() != RequestState.CANCELLED
                && childRequest.getState() != RequestState.FAILED
                && childRequest.getState() != RequestState.COMPLETED
                && childRequest.getState() != RequestState.RESOLVED)
            {
                try
                {
                    building.getColony().getRequestManager().updateRequestState(child, desiredState);
                }
                catch (IllegalArgumentException e)
                {
                    LOGGER.warn("Colony {} Scout - Unable to reconcile child request {} for parent {} ({})",
                        building.getColony().getID(),
                        child,
                        request.getId(),
                        e.getLocalizedMessage());
                }
            }

            reconcileDependentRequests(childRequest, successful);
        }
    }

    protected IRequest<?> getActiveRequest(IRequest<?> request)
    {
        if (request == null)
        {
            return null;
        }

        final IRequest<?> liveRequest = building.getColony().getRequestManager().getRequestForToken(request.getId());
        if (liveRequest == null)
        {
            return null;
        }

        if (liveRequest.getState() == RequestState.CANCELLED
            || liveRequest.getState() == RequestState.FAILED
            || liveRequest.getState() == RequestState.COMPLETED
            || liveRequest.getState() == RequestState.RESOLVED)
        {
            return null;
        }

        try
        {
            building.getColony().getRequestManager().getResolverForRequest(liveRequest.getId());
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }

        return liveRequest;
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
     * @return The DECIDE state, which will cause the scout to decide what to do next.
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
                    if (foodAvailable != null && foodAvailable.getItem() != null)
                    {
                        ItemStorage toOrder = new ItemStorage(foodAvailable.getItem(), FOOD_ORDERING_SIZE);
                        Stack requestStack = new Stack(toOrder);
                        
                        if (citizen.getJob() != null)
                        {
                            try
                            {
                                @SuppressWarnings("null")
                                boolean outstandingRequest = building.hasWorkerOpenRequestsFiltered(
                                    worker.getCitizenData().getId(),
                                    request -> request.getRequest() instanceof Stack stackRequest
                                        && stackRequest.getStack().is(foodAvailable.getItem())
                                );

                                if (!outstandingRequest)
                                {
                                    building.createRequest(worker.getCitizenData(), requestStack, true);
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


    /**
     * Attempts to deliver food for the outpost workers.  Assumes some food will already have been ordered and delivered.
     * 
     * @return The next AI state for the food delivery task.
     */
    public IAIState deliverFoodForOutpost()
    {
        if (building.getColony().getWorld().isClientSide())
        {
            return getState();
        }

        final RestaurantMenuModule module = getNearestRestaurantMenuModule();

        if (module == null)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("Unable to find a restaurant menu module for food delivery."));
            lastFoodDeliveryCheck = building.getColony().getDay();
            return DECIDE;
        }

        if (currentFoodDeliveryStack == null || currentFoodDeliveryStack.isEmpty() || currentFoodDeliveryTarget == null)
        {
            if (!findNextFoodDelivery(module))
            {
                lastFoodDeliveryCheck = building.getColony().getDay();
                return DECIDE;
            }
        }
        
        ItemStack localFoodDelivery = currentFoodDeliveryStack;

        if (localFoodDelivery == null)
        {
            clearFoodDelivery();
            return DECIDE;
        }

        final IBuilding targetBuilding = building.getColony().getServerBuildingManager().getBuilding(currentFoodDeliveryTarget);
        if (targetBuilding == null || childOutpostHasFood(targetBuilding, localFoodDelivery))
        {
            clearFoodDelivery();
            return getState();
        }

        if (!hasFoodInScoutInventory(localFoodDelivery))
        {
            return collectFoodForDelivery();
        }

        return unloadFoodForDelivery(targetBuilding);
    }

    /**
     * Find the next child building that needs a menu food the scout can distribute.
     *
     * @param module the restaurant menu module.
     * @return true if a delivery was selected.
     */
    protected boolean findNextFoodDelivery(@Nonnull final RestaurantMenuModule module)
    {
        for (final ItemStorage menuItem : module.getMenu())
        {
            final ItemStack foodStack = menuItem.getItemStack();
            if (foodStack.isEmpty() || getAvailableFoodForDelivery(foodStack) <= FOOD_DELIVERY_RESERVE)
            {
                continue;
            }

            for (final BlockPos outpostWorkPos : building.getWorkBuildings())
            {
                final IBuilding outpostWorksite = building.getColony().getServerBuildingManager().getBuilding(outpostWorkPos);
                if (outpostWorksite != null && !childOutpostHasFood(outpostWorksite, foodStack)
                    && getSafeItemHandler(outpostWorksite, "outpost food delivery target") != null)
                {
                    currentFoodDeliveryStack = foodStack.copyWithCount(1);
                    currentFoodDeliveryTarget = outpostWorkPos;
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Pick up the selected food from the scout hut before walking it to the child building.
     *
     * @return the next AI state.
     */
    protected IAIState collectFoodForDelivery()
    {
        ItemStack localFoodDeliveryStack = currentFoodDeliveryStack;

        if (localFoodDeliveryStack == null) 
        {
            clearFoodDelivery();
            return DECIDE;
        }

        if (getAvailableFoodForDelivery(localFoodDeliveryStack) <= FOOD_DELIVERY_RESERVE)
        {
            clearFoodDelivery();
            return getState();
        }

        if (!EntityNavigationUtils.walkToBuilding(this.worker, building))
        {
            setDelay(2);
            return getState();
        }

        final IItemHandler outpostInventory = getSafeItemHandler(building, "outpost food delivery pickup");
        final IItemHandler workerInventory = worker.getInventoryCitizen();

        if (outpostInventory == null || workerInventory == null || !transferOneFoodItem(outpostInventory, localFoodDeliveryStack, workerInventory))
        {
            clearFoodDelivery();
            return getState();
        }

        building.markDirty();
        updateHeldFoodDeliveryItem();
        return getState();
    }

    /**
     * Walk to the target child building and unload the selected food from the scout inventory.
     *
     * @param targetBuilding the child building receiving food.
     * @return the next AI state.
     */
    protected IAIState unloadFoodForDelivery(@Nonnull final IBuilding targetBuilding)
    {
        if (!EntityNavigationUtils.walkToBuilding(this.worker, targetBuilding))
        {
            setDelay(2);
            return getState();
        }

        ItemStack localDeliveryStack = currentFoodDeliveryStack;

        if (localDeliveryStack == null)
        {
            clearFoodDelivery();
            return DECIDE;
        }

        final IItemHandler targetInventory = getSafeItemHandler(targetBuilding, "outpost food delivery target");
        final IItemHandler workerInventory = worker.getInventoryCitizen();
        if (targetInventory == null || workerInventory == null || !transferOneFoodItem(workerInventory, localDeliveryStack, targetInventory))
        {
            clearFoodDelivery();
            return getState();
        }

        targetBuilding.markDirty();
        updateHeldFoodDeliveryItem();
        incrementActionsDoneAndDecSaturation();
        worker.getCitizenExperienceHandler().addExperience(SMALL_SCOUT_XP);
        clearFoodDelivery();
        return getState();
    }

    /**
     * Get the menu from the nearest restaurant, if one exists.
     *
     * @return the restaurant menu module, or null if no restaurant/menu is available.
     */
    protected @Nullable RestaurantMenuModule getNearestRestaurantMenuModule()
    {
        final BlockPos restaurantPos = building.getColony().getServerBuildingManager().getBestBuilding(building.getPosition(), BuildingCook.class);
        if (restaurantPos == null || restaurantPos.equals(BlockPos.ZERO))
        {
            return null;
        }

        final IBuilding restaurant = building.getColony().getServerBuildingManager().getBuilding(restaurantPos);
        if (restaurant == null)
        {
            return null;
        }

        return restaurant.getModule(BuildingModules.RESTAURANT_MENU);
    }

    /**
     * Count menu food held by the scout or stocked in the outpost.
     *
     * @param foodStack the food to count.
     * @return the total available count.
     */
    protected int getAvailableFoodForDelivery(@Nonnull final ItemStack foodStack)
    {
        final IItemHandler outpostInventory = getSafeItemHandler(building, "outpost food delivery source");
        return getFoodCount(worker.getInventoryCitizen(), foodStack) + getFoodCount(outpostInventory, foodStack);
    }

    /**
     * Check if a child outpost building or any assigned worker already has this food.
     *
     * @param outpostWorksite the child building to inspect.
     * @param foodStack the food to find.
     * @return true if the food is already present.
     */
    protected boolean childOutpostHasFood(@Nonnull final IBuilding outpostWorksite, @Nonnull final ItemStack foodStack)
    {
        if (getFoodCount(getSafeItemHandler(outpostWorksite, "outpost food delivery inspection"), foodStack) > 0)
        {
            return true;
        }

        for (final ICitizenData citizen : outpostWorksite.getAllAssignedCitizen())
        {
            if (getFoodCount(citizen.getInventory(), foodStack) > 0)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the scout is carrying the selected food.
     *
     * @param foodStack the food to find.
     * @return true if the scout has the food.
     */
    protected boolean hasFoodInScoutInventory(@Nonnull final ItemStack foodStack)
    {
        return getFoodCount(worker.getInventoryCitizen(), foodStack) > 0;
    }

    /**
     * Count matching food in an inventory.
     *
     * @param inventory the inventory to inspect.
     * @param foodStack the food to count.
     * @return the matching item count.
     */
    protected int getFoodCount(@Nullable final IItemHandler inventory, @Nonnull final ItemStack foodStack)
    {
        return InventoryUtils.getItemCountInItemHandler(inventory,
            stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, foodStack));
    }

    /**
     * Move one matching food item between inventories.
     *
     * @param sourceInventory the source inventory.
     * @param foodStack the food to move.
     * @param targetInventory the target inventory.
     * @return true if one item was moved.
     */
    protected boolean transferOneFoodItem(
        @Nonnull final IItemHandler sourceInventory,
        @Nonnull final ItemStack foodStack,
        @Nonnull final IItemHandler targetInventory)
    {
        return InventoryUtils.transferItemStackIntoNextFreeSlotFromItemHandler(sourceInventory,
            stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, foodStack),
            1,
            targetInventory);
    }

    /**
     * Clear the current food delivery assignment.
     */
    protected void clearFoodDelivery()
    {
        currentFoodDeliveryStack = ItemStack.EMPTY;
        currentFoodDeliveryTarget = null;
        worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
    }

    /**
     * Display the carried food in the scout's hand when possible.
     */
    protected void updateHeldFoodDeliveryItem()
    {
        ItemStack localFoodDeliveryStack = currentFoodDeliveryStack;

        if (localFoodDeliveryStack == null) return;

        final int held = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(),
            stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, localFoodDeliveryStack));

        if (held >= 0)
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(worker.getInventoryCitizen().getStackInSlot(held).copy()));
        }
        else
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
        }
    }

    /**
     * Get the item handler for the building at the given position.
     * Workaround for MineColonies NPE possibility.
     * 
     * @param targetBuilding
     * @param action
     * @return
     */
    protected @Nullable IItemHandler getSafeItemHandler(@Nullable final IBuilding targetBuilding, @NotNull final String action)
    {
        if (targetBuilding == null)
        {
            return null;
        }

        try
        {
            return targetBuilding.getItemHandlerCap();
        }
        catch (NullPointerException e)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("Skipping {} for {} at {} because MineColonies reported a missing building tile entity.",
                    action,
                    targetBuilding.getBuildingDisplayName(),
                    targetBuilding.getPosition(),
                    e));
            return null;
        }
    }

    /**
     * Iterate over all the work buildings of the outpost and find the first AbstractBuildingStructureBuilder that has a work order
     * and at least one assigned citizen. If the builder has a current bucket with resources, return it immediately. If not,
     * check the next bucket and return the builder if that has resources. If no suitable builder is found, return null.
     *
     * @return the first suitable builder, or null if none is found.
     */
    protected @Nullable AbstractBuildingStructureBuilder findBuilderToSupport()
    {
        for (BlockPos workLocation : building.getWorkBuildings())
        {
            final IBuilding workBuilding = building.getColony().getServerBuildingManager().getBuilding(workLocation);
            if (!(workBuilding instanceof AbstractBuildingStructureBuilder builderWorksite))
            {
                continue;
            }

            if (!builderWorksite.hasWorkOrder() || builderWorksite.getAllAssignedCitizen().isEmpty())
            {
                continue;
            }

            final BuilderBucket currentBucket = builderWorksite.getRequiredResources();
            if (currentBucket != null && !currentBucket.getResourceMap().isEmpty())
            {
                return builderWorksite;
            }

            final BuilderBucket nextBucket = builderWorksite.getNextBucket();
            if (nextBucket != null && !nextBucket.getResourceMap().isEmpty())
            {
                return builderWorksite;
            }
        }

        return null;
    }

    /**
     * Check if the given builder should request the given bucket, and if so, attempt to stage the resources from the colony's storage into the builder's inventory.
     * 
     * @param builderTarget the builder to check
     * @param builderCitizen the citizen assigned to the builder
     * @param bucket the bucket to check
     * @param bucketName the name of the bucket for logging purposes
     * @return true if any resources were staged, false otherwise
     */
    protected boolean requestAndStageBuilderBucket(
        @Nonnull final AbstractBuildingStructureBuilder builderTarget,
        @Nonnull final ICitizenData builderCitizen,
        @Nullable final BuilderBucket bucket,
        @Nonnull final String bucketName)
    {
        if (bucket == null || bucket.getResourceMap().isEmpty())
        {
            return false;
        }

        builderTarget.checkOrRequestBucket(bucket, builderCitizen);

        boolean stagedAnything = false;
        for (String resourceKey : bucket.getResourceMap().keySet())
        {
            final BuildingBuilderResource resource = builderTarget.getNeededResources().get(resourceKey);
            if (resource == null || resource.getItemStack().isEmpty())
            {
                continue;
            }

            final int desiredCount = bucket.getResourceMap().getOrDefault(resourceKey, 0);
            if (desiredCount <= 0)
            {
                continue;
            }

            ItemStack resourceStack = resource.getItemStack();

            if (resource == null || resourceStack.isEmpty())
            {
                continue;
            }

            final int movedCount = stageBuilderMaterial(builderTarget, resourceStack, desiredCount);
            if (movedCount > 0)
            {
                stagedAnything = true;
                final int movedCountForLog = movedCount;
                TraceUtils.dynamicTrace(TRACE_OUTPOST,
                    () -> LOGGER.info("Colony {} Scout - Pre-staged {} x {} into {} for the builder's {} bucket.",
                        building.getColony().getID(),
                        movedCountForLog,
                        resource.getItemStack().getHoverName().getString(),
                        builderTarget.getBuildingDisplayName(),
                        bucketName));
            }
        }

        return stagedAnything;
    }

    /**
     * Stages the given desired stack into the builder's inventory from the outpost's inventory, up to the desired count.
     * If the desired count is already satisfied in the builder's inventory, this method will return 0.
     * <p>
     * If the outpost's inventory does not have enough items to satisfy the desired count, this method will transfer as much as possible.
     * <p>
     * If the transfer is successful, this method will return the number of items transferred and mark both the builder and outpost as dirty.
     * If the transfer fails, this method will return 0.
     * <p>
     * This method assumes that the outpost inventory and builder inventory are not null.
     * @param builderTarget the builder to stage material for
     * @param desiredStack the stack to stage into the builder's inventory
     * @param desiredCount the desired count of the stack in the builder's inventory
     * @return the number of items transferred, or 0 if the transfer failed.
     */
    protected int stageBuilderMaterial(
        @Nonnull final AbstractBuildingStructureBuilder builderTarget,
        @Nonnull final ItemStack desiredStack,
        final int desiredCount)
    {
        final IItemHandler outpostInventory = getSafeItemHandler(building, "builder support source");
        final IItemHandler builderInventory = getSafeItemHandler(builderTarget, "builder support target");
        if (outpostInventory == null || builderInventory == null)
        {
            return 0;
        }

        final int alreadyStaged = InventoryUtils.getItemCountInItemHandler(builderInventory,
            stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, desiredStack));
        if (alreadyStaged >= desiredCount)
        {
            return 0;
        }

        final int availableInOutpost = InventoryUtils.getItemCountInItemHandler(outpostInventory,
            stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, desiredStack));
        if (availableInOutpost <= 0)
        {
            return 0;
        }

        final int toMove = Math.min(desiredCount - alreadyStaged, availableInOutpost);
        final boolean moved = InventoryUtils.transferItemStackIntoNextFreeSlotFromItemHandler(outpostInventory,
            stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, desiredStack),
            toMove,
            builderInventory);

        if (!moved)
        {
            return 0;
        }

        builderTarget.markDirty();
        building.markDirty();
        final int afterCount = InventoryUtils.getItemCountInItemHandler(builderInventory,
            stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, desiredStack));
        return Math.max(afterCount - alreadyStaged, 0);
    }

    /**
     * Checks if the given bucket is satisfied in the given builder's inventory.
     * 
     * @param builderTarget the builder to check
     * @param bucket the bucket to check
     * @return true if the bucket is satisfied, false otherwise
     */
    protected boolean isBucketSatisfiedInBuilding(
        @NotNull final AbstractBuildingStructureBuilder builderTarget,
        @Nullable final BuilderBucket bucket)
    {
        if (bucket == null || bucket.getResourceMap().isEmpty())
        {
            return true;
        }

        final IItemHandler builderInventory = getSafeItemHandler(builderTarget, "builder support check");
        if (builderInventory == null)
        {
            return false;
        }

        for (String resourceKey : bucket.getResourceMap().keySet())
        {
            final BuildingBuilderResource resource = builderTarget.getNeededResources().get(resourceKey);
            if (resource == null || resource.getItemStack().isEmpty())
            {
                continue;
            }

            ItemStack resourceStack = resource.getItemStack();

            if (resource == null || resourceStack.isEmpty())
            {
                continue;
            }

            final int desiredCount = bucket.getResourceMap().getOrDefault(resourceKey, 0);
            final int stagedCount = InventoryUtils.getItemCountInItemHandler(builderInventory,
                stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, resourceStack));
            if (stagedCount < desiredCount)
            {
                return false;
            }
        }

        return true;
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

    /**
     * After dumping items from the worker's inventory into the outpost's inventory, transitions to the return products state to return items to the
     * colony.
     * 
     * @return The next AI state to transition to.
     */
    @Override
    public IAIState afterDump()
    {
        return ScoutStates.RETURN_PRODUCTS; 
    }


    /**
     * Always returns false because the outpost AI is never done working.
     * The outpost AI is always waiting for new requests from the building or the colony.
     * @return false
     */
    @Override
    public boolean canGoIdle()
    {
        // Work at the outpost is never done...
        return false;
    }
}
