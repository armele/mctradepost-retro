package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost.OutpostOrderState;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.OutpostShipmentTracking;
import com.deathfrog.mctradepost.core.colony.jobs.JobScout;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.OutpostRequestResolver;
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
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.mojang.logging.LogUtils;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.Collection;
import static com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer.DISCONNECTED_OUTPOST;

public class EntityAIWorkScout extends AbstractEntityAICrafting<JobScout, BuildingOutpost>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static final int OUTPOST_COOLDOWN_TIMER = 10;
    protected static final int DEFAULT_SCOUT_XP = 2;
    protected static int outpostCooldown = OUTPOST_COOLDOWN_TIMER;

    public enum ScoutStates implements IAIState
    {
        HANDLE_OUTPOST_REQUESTS,
        MAKE_DELIVERY,
        UNLOAD_INVENTORY;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    /**
     * Work status icons
     */
    private final static VisibleCitizenStatus OUTPOST_FARMING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/outpost_farming.png"),
            "com.mctradepost.outpost.mode.farming");

    private final static VisibleCitizenStatus OUTPOST_BUILDING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/outpost_building.png"),
            "com.mctradepost.outpost.mode.building");

    private final static VisibleCitizenStatus OUTPOST_SCOUTING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/outpost_scouting.png"),
            "com.mctradepost.outpost.mode.scouting");

    private final static VisibleCitizenStatus OUTPOST_NONE =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/icons/work/outpost_none.png"),
            "com.mctradepost.outpost.mode.none");

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

        if (worker.getRandom().nextInt(100) < Constants.WANDER_CHANCE)
        {
            return WANDER;
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

        LOGGER.info("Unloading junk from my inventory.");

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
            LOGGER.warn("No inventory handling found on the building...");
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
                request.setState(requestManager, RequestState.CANCELLED);
                continue;
            }


            if (currentlyAssignedResolver instanceof OutpostRequestResolver && currentlyAssignedResolver.getLocation().equals(building.getLocation()))
            {
                OutpostShipmentTracking shipmentTracking = building.trackingForRequest(request);

                LOGGER.info("Checking if satisfiable from the building: {} with state {}", request.getLongDisplayString(), request.getState());
                // Check if the building has a qualifying item and ship it if so. Determine state change of request status.
                ItemStorage satisfier = building.inventorySatisfiesRequest(request);

                if (satisfier != null)
                {
                    LOGGER.info("We have something to deliver: {}", satisfier);
                    
                    currentDeliverableSatisfier = satisfier.getItemStack().copy();
                    currentDeliverableRequest = request;
                    shipmentTracking.setState(OutpostOrderState.RECEIVED);

                    // Once we have the necessary thing in the outpost, we can mark all remaining outstanding children as cancelled, and remove them.
                    for (IToken<?> child : request.getChildren())
                    {
                        // IRequest<?> childRequest = requestManager.getRequestForToken(child);
                        requestManager.updateRequestState(child, RequestState.COMPLETED);
                        request.removeChild(child);
                    }
                    
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
            LOGGER.info("No current deliverable satisfier in makeDelivery.");

            currentDeliverableRequest = null;
            return DECIDE;
        }

        OutpostShipmentTracking tracking = building.trackingForRequest(currentDeliverableRequest);
        IBuilding deliveryTarget = targetBuildingForRequest(currentDeliverableRequest);

        if (tracking == null)
        {
            // Somewhere along the line something got cancelled, and we aren't expecting this any more...
            LOGGER.info("No tracking for delivery: {} - repairing.", currentDeliverableSatisfier);
            
            if (!getInventory().isEmpty())
            {
                tracking = new  OutpostShipmentTracking(deliveryTarget.getPosition(), null, OutpostOrderState.READY_FOR_DELIVERY);    
            }
            else
            {
                tracking = new  OutpostShipmentTracking(deliveryTarget.getPosition(), null, OutpostOrderState.DELIVERED);
            }
        }

        LOGGER.info("Making delivery of {} to {} (tracking status {})", currentDeliverableSatisfier, deliveryTarget.getBuildingDisplayName(), tracking.getState());

        // Walk to the destination building with the needed item.
        if (tracking.getState() == OutpostOrderState.READY_FOR_DELIVERY)
        {
            if (!EntityNavigationUtils.walkToBuilding(this.worker, deliveryTarget) && !getInventory().isEmpty())
            {
                LOGGER.info("Walking to {} with delivery: {}", deliveryTarget.getBuildingDisplayName(), currentDeliverableSatisfier);
                return getState();
            }


            // Transfer the item into the target building's inventory once we've arrived.
            int slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), stack -> ItemStack.isSameItem(stack, currentDeliverableSatisfier));

            if (slot >= 0)
            {
                InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, deliveryTarget);
                worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                tracking.setState(OutpostOrderState.DELIVERED);
                
                IRequestManager requestManager = building.getColony().getRequestManager();
                requestManager.updateRequestState(currentDeliverableRequest.getId(), RequestState.COMPLETED);

                incrementActionsDoneAndDecSaturation();
                worker.getCitizenExperienceHandler().addExperience(DEFAULT_SCOUT_XP);

                // currentDeliverableRequest.setState(requestManager, RequestState.COMPLETED);
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
            LOGGER.info("Item to deliver found in slot {}.", slot);


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

            LOGGER.info("Scout delivering item (final leg) {}.", deliveryItem);
        }
        else
        {
            LOGGER.info("Scout unable to find {} to deliver (final leg), despite a reported match.", currentDeliverableSatisfier);
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
            LOGGER.info("No request location associated with this request.");
            return null;
        }

        return building.getColony().getBuildingManager().getBuilding(request.getRequester().getLocation().getInDimensionLocation());
    }


    @Override
    public void tick()
    {
        super.tick();
    }
}
