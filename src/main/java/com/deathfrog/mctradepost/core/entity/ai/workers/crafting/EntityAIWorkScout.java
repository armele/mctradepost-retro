package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.jobs.JobScout;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.OutpostRequestResolver;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.management.IRequestHandler;
import com.minecolonies.api.colony.requestsystem.management.IResolverHandler;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer.DISCONNECTED_OUTPOST;

public class EntityAIWorkScout extends AbstractEntityAICrafting<JobScout, BuildingOutpost>
{
    public static final Logger LOGGER = LogUtils.getLogger();

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


    @SuppressWarnings("unchecked")
    public EntityAIWorkScout(@NotNull final JobScout job)
    {
        super(job);

        super.registerTargets(
            new AITarget<IAIState>(IDLE, START_WORKING, 1),
            new AITarget<IAIState>(DECIDE, this::decide, 10)

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
        LOGGER.info("Scout deciding what to do.");

        if (building.isDisconnected())
        {
            worker.getCitizenData().triggerInteraction(
                new StandardInteraction(Component.translatable(DISCONNECTED_OUTPOST, Component.translatable(building.getBuildingDisplayName())),
                    Component.translatable(DISCONNECTED_OUTPOST),
                    ChatPriority.BLOCKING));

        }

        IAIState nextState = gatherOutpostNeeds();

        // TODO: Compare outstanding needs to available resources
        // - If resources are present, deliver them; else order them from the station.
        // TODO: Place orders for anything still outstanding but not requested.

        if (building.getExpectedShipmentSize() == 0)
        {
            LOGGER.info("Placing order with station.");
            placeOrderWithStation(new ItemStack(Items.COBBLESTONE), 16, building.getOutpostFarmer().getPosition());
        }

        return DECIDE;
    }

    /**
     * Goes through all the children of the outpost and checks if there are any open requests for stacks.
     * If so, it adds the stack to the list of needs for the outpost.
     * Finally, it returns the START_WORKING state.
     * @return the next AI state to transition to
     */
    public IAIState gatherOutpostNeeds()
    {
        if (resolverBlacklist == null)
        {
            resolverBlacklist = BuildingUtil.getAllBuildingResolversForColony(building.getColony());
            int removed = 0;
            
            for (IRequestResolver<?> resolver: building.getResolvers())
            {
                if (resolver instanceof OutpostRequestResolver)
                {
                    boolean didRemove = resolverBlacklist.remove(resolver.getId());
                    if (didRemove) removed++;
                }
            }

            LOGGER.info("Set up blacklist for {} resolvers. Outpost removed resolvers: {}", resolverBlacklist.size(), removed);
        }


        for (BlockPos child : building.getChildren())
        {
            IBuilding childBuilding = building.getColony().getBuildingManager().getBuilding(child);

            LOGGER.info("Checking requests in building {}", childBuilding.getBuildingDisplayName());

            for (ICitizenData citizen : childBuilding.getAllAssignedCitizen())
            {
                LOGGER.info("Checking requests for citizen {}", citizen.getName());


                final Collection<IRequest<?>> openRequests = childBuilding.getOpenRequests(citizen.getId());
                for (final IRequest<?> request : openRequests)
                {

                    if (request.getState() != RequestState.CREATED && request.getState() != RequestState.ASSIGNED && request.getState() != RequestState.IN_PROGRESS) 
                    {
                        LOGGER.info("Skipping request in state {}", request.getState());
                        continue;
                    }

                    LOGGER.info("Building {} has open request: {} of type {}.", childBuilding.getBuildingDisplayName(), request.getLongDisplayString(), request.getClass().getSimpleName());

                    final IStandardRequestManager requestManager = (IStandardRequestManager) building.getColony().getRequestManager();
                    final IRequestResolver<?> currentlyAssignedResolver = requestManager.getResolverForRequest(request.getId());
                    IRequestHandler requestHandler = requestManager.getRequestHandler();
                    IResolverHandler resolverHandler = requestManager.getResolverHandler();

                    if (currentlyAssignedResolver == null)
                    {
                        LOGGER.info("Assigning to Outpost.");
                        resolverHandler.addRequestToResolver(building.getOutpostResolver(), request);
                    }
                    else if (currentlyAssignedResolver.getId().equals(building.getOutpostResolver().getId()))
                    {
                        LOGGER.info("Already assigned to Outpost.");
                    }
                    else
                    {
                        IToken<?> newResolverToken = requestManager.reassignRequest(request.getId(), resolverBlacklist);

                        IBuilding oldResolverBuilding = buildingForResolverToken(currentlyAssignedResolver.getId());
                        IBuilding newResolverBuilding = buildingForResolverToken(newResolverToken);

                        LOGGER.info("Attempting reassignment from {} - resulted in assignment to {}.", 
                            oldResolverBuilding == null ? "null" : oldResolverBuilding.getBuildingDisplayName(), 
                            newResolverBuilding == null ? "null" : newResolverBuilding.getBuildingDisplayName());
                    }
                }
            }
        }

        return START_WORKING;
    }


    /**
     * Returns the building associated with the given resolver token.
     * If the request manager is null or the resolver is null, returns null.
     * Otherwise, returns the building associated with the location of the given resolver token.
     * @param token The token of the resolver to find the building for.
     * @return The building associated with the given resolver token, or null if the request manager or resolver is null.
     */
    public IBuilding buildingForResolverToken(IToken<?> token)
    {
        final IStandardRequestManager requestManager = (IStandardRequestManager) building.getColony().getRequestManager();

        if (requestManager == null)
        {
            LOGGER.error("Unable to obtain request manager from the colony.");
            return null;
        }

        IResolverHandler resolverHandler = requestManager.getResolverHandler();
        
        if (resolverHandler == null)
        {
            LOGGER.error("Unable to obtain resovler handler from the request manager.");
            return null;
        }

        IRequestResolver<?> newResolver = resolverHandler.getResolver(token);

        if (newResolver == null)
        {
            LOGGER.info("No resolver associated with this request.");
            return null;
        }

        return building.getColony().getBuildingManager().getBuilding(newResolver.getLocation().getInDimensionLocation());
    }


    /**
     * Places an order with a connected station to deliver the given item stack to this outpost.
     * The order is placed with the export module on the connected station, and the cost of the order is
     * determined by the export module.
     * If the connected station does not have an export module, a warning is logged and no order is placed.
     * If there is no connected station, an info message is logged and no order is placed.
     * 
     * @param stack the item stack to order
     * @param quantity the quantity of the item stack to order
     */
    public void placeOrderWithStation(ItemStack stack, int quantity, BlockPos outpostDestination)
    {
        BuildingStation connectedStation = building.getConnectedStation();
        if (connectedStation == null)
        {
            LOGGER.info("No connected station - cannot place order.");
            return;
        }
        BuildingStationExportModule exports = connectedStation.getModule(MCTPBuildingModules.EXPORTS);

        if (exports == null)
        {
            LOGGER.warn("No export module on connected station - cannot place order.");
            return;
        }

        int cost = 0;

        StationData destinationStation = new StationData(building);
        ExportData export = exports.addExport(destinationStation, stack, cost, quantity);

        building.addExpectedShipment(export, outpostDestination);

    }

    @Override
    public void tick()
    {
        super.tick();
    }
}
