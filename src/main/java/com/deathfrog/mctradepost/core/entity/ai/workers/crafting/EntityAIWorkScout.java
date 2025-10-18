package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.jobs.JobScout;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.ITradeCapable;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData.TrackConnectionStatus;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.ArrayList;
import java.util.List;

import static com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer.DISCONNECTED_OUTPOST;

public class EntityAIWorkScout extends AbstractEntityAICrafting<JobScout, BuildingOutpost>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public List<ExportData> expectedShipments = new ArrayList<>();

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

        Log.getLogger().error("Citizen: " + worker.getId() + " has an expected building class of {}", buildingClass);

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

        // TODO: Compile outstanding needs of citizens, with status (NEEDED, ORDERED, DELIVERED)
        // TODO: Compare outstanding needs to available resources and reconcile
        // TODO: Place orders for anything still outstanding but not requested.

        if (expectedShipments.size() == 0)
        {
            LOGGER.info("Placing order with station.");
            placeOrderWithStation(new ItemStack(Items.COBBLESTONE), 16);
        }

        return DECIDE;
    }

    public void placeOrderWithStation(ItemStack stack, int quantity)
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

        expectedShipments.add(export);

    }

    @Override
    public void tick()
    {
        super.tick();
    }
}
