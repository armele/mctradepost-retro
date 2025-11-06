package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.blocks.ModBlockTags;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.OutpostExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.OutpostShipmentTracking;
import com.deathfrog.mctradepost.core.colony.jobs.JobScout;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.OutpostRequestResolver;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlueprintPositionInfo;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.workers.util.IBuilderUndestroyable;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.modules.settings.BuilderModeSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveCloseToXNearY;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.tileentities.TileEntityDecorationController;
import com.minecolonies.core.util.WorkerUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriPredicate;
import net.neoforged.neoforge.items.IItemHandler;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.Collection;
import static com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer.DISCONNECTED_OUTPOST;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_OUTPOST_REQUESTS;

public class EntityAIWorkScout extends AbstractEntityAIStructureWithWorkOrder<JobScout, BuildingOutpost>
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

    private final static VisibleCitizenStatus BUILDING = new VisibleCitizenStatus(
        ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/outpost_building.png"),
        "com.mctradepost.gui.visiblestatus.building");

    public enum ScoutStates implements IAIState
    {
        HANDLE_OUTPOST_REQUESTS, MAKE_DELIVERY, ORDER_FOOD, RETURN_PRODUCTS, UNLOAD_INVENTORY, BUILD;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    /**
     * Predicate defining things we don't want the builders to ever touch.
     */
    protected TriPredicate<BlueprintPositionInfo, BlockPos, IStructureHandler> DONT_TOUCH_PREDICATE = (info, worldPos, handler) -> {
        final BlockState worldState = handler.getWorld().getBlockState(worldPos);

        return worldState.getBlock() instanceof IBuilderUndestroyable || worldState.getBlock() == Blocks.BEDROCK ||
            worldState.is(ModBlockTags.TRACK_TAG) ||
            (info.getBlockInfo().getState().getBlock() instanceof AbstractBlockHut && handler.getWorldPos().equals(worldPos) &&
                worldState.getBlock() instanceof AbstractBlockHut);
    };

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
            new AITarget<IAIState>(ScoutStates.BUILD, this::startWorkingAtOwnBuilding, 20),
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
            worker.getCitizenData()
                .triggerInteraction(new StandardInteraction(
                    Component.translatable(DISCONNECTED_OUTPOST, Component.translatable(building.getBuildingDisplayName())),
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

        if (checkForWorkOrder())
        {
            worker.getCitizenData().setVisibleStatus(BUILDING);
            return ScoutStates.BUILD;
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

                    ItemStorage candidate = new ItemStorage(inSlot.copyWithCount(1), 1, true, true);

                    if (module.isItemInList(candidate))
                    {
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
                LOGGER.warn("Unable to get resolver for request {} ({})", request, e.getLocalizedMessage());
                // request.setState(requestManager, RequestState.CANCELLED);
                // building.getColony().getRequestManager().updateRequestState(request.getId(), RequestState.CANCELLED);
                continue;
            }

            if (currentlyAssignedResolver instanceof OutpostRequestResolver && currentlyAssignedResolver.getLocation()
                .getInDimensionLocation()
                .equals(building.getLocation().getInDimensionLocation()))
            {
                OutpostShipmentTracking shipmentTracking = building.trackingForRequest(request);

                TraceUtils.dynamicTrace(TRACE_OUTPOST,
                    () -> LOGGER.info("Checking if satisfiable from the building: {} with state {}",
                        request.getLongDisplayString(),
                        request.getState()));
                // Check if the building has a qualifying item and ship it if so. Determine state change of request status.
                ItemStorage satisfier = building.inventorySatisfiesRequest(request, true);

                if (satisfier != null)
                {
                    TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("We have something to deliver: {}", satisfier));

                    currentDeliverableSatisfier = satisfier.getItemStack().copy();
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
            TraceUtils.dynamicTrace(TRACE_OUTPOST, () -> LOGGER.info("No current deliverable satisfier in makeDelivery."));

            currentDeliverableRequest = null;
            return DECIDE;
        }

        OutpostShipmentTracking tracking = building.trackingForRequest(currentDeliverableRequest);
        IBuilding deliveryTarget = targetBuildingForRequest(currentDeliverableRequest);

        if (deliveryTarget == null)
        {
            // If for some reason we don't have a delivery target, assume the shipment is in its final destination.
            markDeliverySuccessful(currentDeliverableRequest, tracking);
            return DECIDE;
        }

        if (deliveryTarget != null &&
            deliveryTarget.getLocation().getInDimensionLocation().equals(building.getLocation().getInDimensionLocation()))
        {
            markDeliverySuccessful(currentDeliverableRequest, tracking);
            return DECIDE;
        }

        if (tracking == null)
        {
            // Somewhere along the line something got cancelled, and we aren't expecting this any more...
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("No tracking for delivery: {} - repairing.", currentDeliverableSatisfier));

            if (!getInventory().isEmpty())
            {
                tracking = new OutpostShipmentTracking(OutpostOrderState.READY_FOR_DELIVERY);
            }
            else
            {
                tracking = new OutpostShipmentTracking(OutpostOrderState.DELIVERED);
            }
        }

        final OutpostShipmentTracking trackingForLog = tracking;
        TraceUtils.dynamicTrace(TRACE_OUTPOST,
            () -> LOGGER.info("Making delivery of {} to {} (tracking status {})",
                currentDeliverableSatisfier,
                deliveryTarget.getBuildingDisplayName(),
                trackingForLog.getState()));

        // Walk to the destination building with the needed item.
        if (tracking.getState() == OutpostOrderState.READY_FOR_DELIVERY)
        {
            if (!EntityNavigationUtils.walkToBuilding(this.worker, deliveryTarget) && !getInventory().isEmpty())
            {
                TraceUtils.dynamicTrace(TRACE_OUTPOST,
                    () -> LOGGER.info("Walking to {} with delivery: {}",
                        deliveryTarget.getBuildingDisplayName(),
                        currentDeliverableSatisfier));
                return getState();
            }

            // Transfer the item into the target building's inventory once we've arrived.
            int slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(),
                stack -> ItemStack.isSameItem(stack, currentDeliverableSatisfier));

            if (slot >= 0)
            {
                try
                {
                    InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, deliveryTarget);
                }
                catch (NullPointerException e)
                {
                    LOGGER.warn("Error inserting into target building {}: {}",
                        deliveryTarget.getBuildingDisplayName(),
                        e.getLocalizedMessage());
                }

                worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                markDeliverySuccessful(currentDeliverableRequest, tracking);
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
        int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(building,
            stack -> ItemStack.matches(stack, currentDeliverableSatisfier));

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
    protected void markDeliverySuccessful(IRequest<?> request, OutpostShipmentTracking tracking)
    {
        TraceUtils.dynamicTrace(TRACE_OUTPOST,
            () -> LOGGER.info("Delivery of {} (tracking status {}) has reached its destination.",
                currentDeliverableSatisfier,
                tracking.getState()));

        if (request != null)
        {
            building.getColony().getRequestManager().updateRequestState(request.getId(), RequestState.COMPLETED);
        }

        tracking.setState(OutpostOrderState.DELIVERED);

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

        return building.getColony().getBuildingManager().getBuilding(request.getRequester().getLocation().getInDimensionLocation());
    }

    /**
     * Attempts to order food for the outpost workers using the nearest restaurant.
     * 
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
                        
                        if (citizen.getJob() != null)
                        {
                            outpostWorksite.createRequest(citizen, requestStack, true);
                        }
                        didOrder = true;
                    }
                    else
                    {
                        TraceUtils.dynamicTrace(TRACE_OUTPOST,
                            () -> LOGGER.info("Unable to find food to request for {}.", citizen.getName()));
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

    /**
     * Checks if we got a valid workorder.
     *
     * @return true if we got a workorder to work with
     */
    protected boolean checkForWorkOrder()
    {
        if (!building.hasWorkOrder())
        {
            building.setProgressPos(null, BuildingProgressStage.CLEAR);
            worker.getCitizenData().setStatusPosition(null);
            return false;
        }

        final IWorkOrder wo = building.getWorkOrder();

        if (wo == null)
        {
            building.setWorkOrder(null);
            building.setProgressPos(null, null);
            worker.getCitizenData().setStatusPosition(null);
            return false;
        }

        final IBuilding workBuilding = job.getColony().getBuildingManager().getBuilding(wo.getLocation());
        if (workBuilding == null && wo instanceof WorkOrderBuilding && wo.getWorkOrderType() != WorkOrderType.REMOVE)
        {
            this.building.complete(worker.getCitizenData());
            return false;
        }

        // The Scout can only build at their own outpost.
        if (workBuilding != null &&
            !workBuilding.getLocation().getInDimensionLocation().equals(building.getLocation().getInDimensionLocation()))
        {
            building.setProgressPos(null, BuildingProgressStage.CLEAR);
            worker.getCitizenData().setStatusPosition(null);
            return false;
        }

        return true;
    }

    @Override
    public void tick()
    {
        super.tick();
    }

    @Override
    public int getPlaceSpeedLevel()
    {
        return getPrimarySkillLevel();
    }

    @Override
    public boolean shallReplaceSolidSubstitutionBlock(Block arg0, BlockState arg1)
    {
        return false;
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
                !IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(blockToMine)),
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

        // Otherwise, try to magic one out of the outpost inventory - and then use it.
        for (BlockPos outpostWorkPos : building.getWorkBuildings())
        {
            IBuilding outpostWorksite = building.getColony().getBuildingManager().getBuilding(outpostWorkPos);

            if (outpostWorksite != null)
            {
                final EquipmentTypeEntry toolType = WorkerUtil.getBestToolForBlock(target, target.getDestroySpeed(world, pos), building, world, pos);
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
                    final int level = toolType.getMiningLevel(item);

                    if (level > -1 && level >= required && level < bestLevel && ItemStackUtils.verifyEquipmentLevel(item, level, required, maxToolLevel))
                    {
                        bestSlot = i;
                        bestLevel = level;
                    }
                }

                if (bestSlot >= 0)
                {
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

    /**
     * Guard to prevent timing issues in Outpost-delivered requests from killing our building loop.
     * Calls super.structureStep and catches any exceptions that may occur in the process, returning DECIDE if an exception is caught.
     * 
     * @return The next AI state to transition to.
     */
    @Override
    public IAIState structureStep()
    {
        IAIState nextState = DECIDE;

        // Giant kludge to the problem of builder not seeing rack contents!
        /*
        if (building.getOutpostLevel() == 0)
        {
            for (int i = 0; i < outpost.getItemHandlerCap().getSlots(); i++)
            {
                if (!InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(outpost.getItemHandlerCap(), i, worker.getInventoryCitizen()))
                {
                    break;
                }
            }
        }
        */

        // Guard to prevent timing issues in Outpost-delivered requests from
        // killing our building loop.
        try
        {
            nextState = super.structureStep();
        }
        catch (IllegalArgumentException e)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.warn("MineColonies request system error while considering structure step: {}", e));
            nextState = DECIDE;
        }

        return nextState;
    }

    @Override
    public IAIState pickUpMaterial()
    {
        IAIState nextState = null;
        
        try 
        {
            nextState = super.pickUpMaterial();
        } 
        catch (IllegalArgumentException e) {
            TraceUtils.dynamicTrace(TRACE_OUTPOST_REQUESTS, () -> LOGGER.info("Request system error while considering pickUpMaterial - request may have been cancelled while shipping. {}", e ));
            nextState = DECIDE;
        }

        return nextState;
    }


    /**
     * Note that this reproduces EntityAIStructureBuilder.walkToConstructionSite
     * Copied here to satisfy the required abstract method.
     * 
     * @param currentBlock the current block position of the worker
     * @return true if the worker can continue navigating to the construction site, false otherwise
     */
    @Override
    public boolean walkToConstructionSite(final BlockPos currentBlock)
    {

        if (building.getWorkOrder() == null)
        {
            return false;
        }

        if (workFrom != null && workFrom.getX() == currentBlock.getX() &&
            workFrom.getZ() == currentBlock.getZ() &&
            workFrom.getY() >= currentBlock.getY())
        {
            // Reset working position when standing ontop
            workFrom = null;
        }

        if (workFrom == null)
        {
            if (gotoPath == null || gotoPath.isCancelled())
            {
                final PathJobMoveCloseToXNearY pathJob =
                    new PathJobMoveCloseToXNearY(world, currentBlock, building.getWorkOrder().getLocation(), 4, worker);
                gotoPath = ((MinecoloniesAdvancedPathNavigate) worker.getNavigation()).setPathJob(pathJob, currentBlock, 1.0, false);
                pathJob.getPathingOptions().dropCost = 200;
                pathJob.extraNodes = 0;
            }
            else if (gotoPath.isDone())
            {
                if (gotoPath.getPath() != null)
                {
                    workFrom = gotoPath.getPath().getTarget();
                }
                gotoPath = null;
            }

            if (prevBlockPosition != null)
            {
                return BlockPosUtil.dist(prevBlockPosition, currentBlock) <= 10;
            }
            return false;
        }

        if (!walkToSafePos(workFrom))
        {
            // Something might have changed, new wall and we can't reach the position anymore. Reset workfrom if stuck.
            if (worker.getNavigation() instanceof MinecoloniesAdvancedPathNavigate pathNavigate &&
                pathNavigate.getStuckHandler().getStuckLevel() > 0)
            {
                workFrom = null;
            }
            return false;
        }

        if (BlockPosUtil.getDistance2D(worker.blockPosition(), currentBlock) > 5)
        {
            if (BlockPosUtil.dist(workFrom, building.getWorkOrder().getLocation()) < 100)
            {
                prevBlockPosition = currentBlock;
                workFrom = null;
                return true;
            }
            workFrom = null;
            return false;
        }

        prevBlockPosition = currentBlock;
        return true;
    }

    /**
     * Loads the structure given the name, rotation and position.
     * Adapted from EntityAIStructureBuilder.loadStructure to work with the Outpost "fake level" workaround
     *
     * @param workOrder the work order.
     * @param position  the position to set it.
     * @param removal   if removal step.
     */
    public void loadStructure(@NotNull final IBuilderWorkOrder workOrder, final BlockPos position, final boolean removal)
    {
        IBuilding colonyBuilding =
            worker.getCitizenColonyHandler().getColonyOrRegister().getBuildingManager().getBuilding(position);
        final BlockEntity entity = world.getBlockEntity(position);

        if (!(colonyBuilding instanceof BuildingOutpost outpost))
        {
            super.loadStructure(workOrder, position, removal);
            return;
        }

        WorkOrderBuilding altWorkOrder = WorkOrderBuilding.create(WorkOrderType.BUILD, colonyBuilding);

        TraceUtils.dynamicTrace(TRACE_OUTPOST,
            () -> LOGGER.info("Using outpost specific loadStructure with structure path: {}", altWorkOrder.getStructurePath()));

        // Code below is copied from EntityAIStructureBuilder.loadStructure and modified
        // for the special Outpost "fake level" workaround.
        this.loadingBlueprint = true;
        workOrder.loadBlueprint(world, (blueprint -> {
            if (blueprint == null)
            {
                handleSpecificCancelActions();
                LOGGER.warn("Couldn't find structure with name: " + altWorkOrder.getStructurePath() +
                    " in: " +
                    workOrder.getStructurePack() +
                    ". Aborting loading procedure");
                this.loadingBlueprint = false;
                return;
            }

            final BuildingStructureHandler<JobScout, BuildingOutpost> structure;

            if (removal)
            {
                structure = new BuildingStructureHandler<>(world,
                    workOrder,
                    this,
                    new BuildingProgressStage[] {BuildingProgressStage.REMOVE_WATER, BuildingProgressStage.REMOVE});
                building.setTotalStages(2);
            }
            else if ((outpost != null && (outpost.getOutpostLevel() > 0 || outpost.hasParent())) ||
                (entity instanceof TileEntityDecorationController &&
                    Utils.getBlueprintLevel(((TileEntityDecorationController) entity).getBlueprintPath()) != -1))
            {
                structure = new BuildingStructureHandler<>(world,
                    workOrder,
                    this,
                    new BuildingProgressStage[] {BuildingProgressStage.CLEAR,
                        BuildingProgressStage.BUILD_SOLID,
                        BuildingProgressStage.WEAK_SOLID,
                        BuildingProgressStage.CLEAR_WATER,
                        BuildingProgressStage.CLEAR_NON_SOLIDS,
                        BuildingProgressStage.DECORATE,
                        BuildingProgressStage.SPAWN});
                building.setTotalStages(5);
            }
            else
            {
                structure = new BuildingStructureHandler<>(world,
                    workOrder,
                    this,
                    new BuildingProgressStage[] {BuildingProgressStage.CLEAR,
                        BuildingProgressStage.BUILD_SOLID,
                        BuildingProgressStage.WEAK_SOLID,
                        BuildingProgressStage.CLEAR_WATER,
                        BuildingProgressStage.CLEAR_NON_SOLIDS,
                        BuildingProgressStage.DECORATE,
                        BuildingProgressStage.SPAWN});
                building.setTotalStages(6);
            }

            setStructurePlacer(structure);

            if (getProgressPos() != null)
            {
                structure.setStage(getProgressPos().getB());
            }
            this.loadingBlueprint = false;
        }));
    }

    /**
     * Takes the existing workorder, loads the structure and tests the worker order if it is valid. Note that this adapts
     * EntityAIStructureBuilder.loadRequirements only in minor situations. 
     * TODO: PR for base Minecolonies code that removes this kludgey workaround requiring double-maintenance.
     */
    @Override
    public IAIState loadRequirements()
    {
        final IBuilderWorkOrder workOrder = building.getWorkOrder();

        if (workOrder == null)
        {
            // LOGGER.warn("No work order when loading requirements - bailing to IDLE");
            return IDLE;
        }

        // We need this workaround ONLY for level one outposts (due to their "fake 1" level).
        // Bounce to super if we're past that point.
        if (building.getOutpostLevel() > 0)
        {
            TraceUtils.dynamicTrace(TRACE_OUTPOST,
                () -> LOGGER.info("We can use the base Minecolonies code: {}, {}", building.getOutpostLevel(), workOrder.isCleared()));
            return super.loadRequirements();
        }

        TraceUtils.dynamicTrace(TRACE_OUTPOST,
            () -> LOGGER.info(
                "Attempting special clear. Outpost level: {}, is cleared: {}, building work order {}, blueprint: {}, structurePlacer {}",
                building.getOutpostLevel(),
                workOrder.isCleared(),
                building.getWorkOrder(),
                building.getWorkOrder() == null ? null : building.getWorkOrder().getBlueprint(),
                structurePlacer));

        if (building.getWorkOrder() == null || building.getWorkOrder().getBlueprint() == null || structurePlacer == null)
        {
            final BlockPos pos = workOrder.getLocation();
            if (workOrder instanceof WorkOrderBuilding &&
                worker.getCitizenColonyHandler().getColonyOrRegister().getBuildingManager().getBuilding(pos) == null)
            {
                // LOGGER.warn("AbstractBuilding does not exist - removing build request");
                worker.getCitizenColonyHandler().getColonyOrRegister().getWorkManager().removeWorkOrder(workOrder);
                return IDLE;
            }

            final boolean removal = workOrder.getWorkOrderType() == WorkOrderType.REMOVE;

            loadStructure(workOrder, pos, removal);
            workOrder.setCleared(false);
            workOrder.setRequested(removal);

            final IBuilderWorkOrder wo = building.getWorkOrder();
            if (wo == null)
            {
                LOGGER.error(String.format("Worker (%d:%d) ERROR - Starting and missing work order",
                    worker.getCitizenColonyHandler().getColonyOrRegister().getID(),
                    worker.getCitizenData().getId()), new Exception());
                building.setWorkOrder(null);
                return IDLE;
            }

            if (wo instanceof WorkOrderBuilding)
            {
                final IBuilding woBuilding = job.getColony().getBuildingManager().getBuilding(wo.getLocation());
                if (woBuilding == null)
                {
                    LOGGER.error(String.format("Worker (%d:%d) ERROR - Starting and missing building(%s)",
                        worker.getCitizenColonyHandler().getColonyOrRegister().getID(),
                        worker.getCitizenData().getId(),
                        wo.getLocation()), new Exception());
                    return IDLE;
                }

                MessageUtils
                    .forCitizen(worker,
                        TranslationConstants.COM_MINECOLONIES_COREMOD_ENTITY_BUILDER_BUILD_START,
                        this.building.getWorkOrder().getDisplayName())
                    .sendTo(worker.getCitizenColonyHandler().getColonyOrRegister().getMessagePlayerEntities());
            }
            else if (!(wo instanceof WorkOrderMiner))
            {
                MessageUtils
                    .forCitizen(worker,
                        TranslationConstants.COM_MINECOLONIES_COREMOD_ENTITY_BUILDER_BUILD_START,
                        building.getWorkOrder().getDisplayName())
                    .sendTo(worker.getCitizenColonyHandler().getColonyOrRegister().getMessagePlayerEntities());
                ;
            }
            return getState();
        }

        if (building.getWorkOrder().isRequested())
        {
            return afterStructureLoading();
        }

        final AbstractBuildingStructureBuilder buildingWorker = building;
        if (requestMaterials())
        {
            building.getWorkOrder().setRequested(true);
        }
        int newQuantity = buildingWorker.getNeededResources().values().stream().mapToInt(ItemStorage::getAmount).sum();
        if (building.getWorkOrder().getAmountOfResources() == 0 || newQuantity > building.getWorkOrder().getAmountOfResources())
        {
            building.getWorkOrder().setAmountOfResources(newQuantity);
        }

        return getState();
    }

    /**
     * Start working at the own building. If the AI can't walk to the building, it will return the current state. Otherwise, it will
     * transition to the LOAD_STRUCTURE state.
     *
     * @return the next state to transition to.
     */
    protected IAIState startWorkingAtOwnBuilding()
    {
        if (!walkToBuilding())
        {
            return getState();
        }

        worker.getCitizenData().setVisibleStatus(BUILDING);

        return LOAD_STRUCTURE;
    }

    @Override
    public void setStructurePlacer(final BuildingStructureHandler<JobScout, BuildingOutpost> structure)
    {
        if (building.getWorkOrder().getIteratorType().isEmpty())
        {
            final String mode = BuilderModeSetting.getActualValue(building);
            building.getWorkOrder().setIteratorType(mode);
        }

        structurePlacer = new Tuple<>(new StructurePlacer(structure, building.getWorkOrder().getIteratorType()), structure);
    }

    @Override
    public boolean canGoIdle()
    {
        // Work at the outpost is never done...
        return false;
    }
}
