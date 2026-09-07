package com.deathfrog.mctradepost.core.client.gui;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.CitizenIncentiveModuleView;
import com.ldtteam.blockui.BOScreen;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.modules.TabsWindowModule;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Random;

/**
 * Adds a payroll tab to core Town Hall pages through public interaction and screen events.
 * Core hides ordinary building-module tabs in these pages and does not expose their building view.
 * The clicked hut supplies that context without reflection or changes to core window classes.
 */
@EventBusSubscriber(modid = MCTradePostMod.MODID, value = Dist.CLIENT)
public final class TownHallIncentiveTab
{
    private static final String ID_TAB_VIEW = "textures/gui/modules/econ.png_view";
    private static final long OPEN_TIMEOUT_TICKS = 200;
    private static BlockPos townHallPosition;
    private static ResourceKey<Level> townHallDimension;
    private static long clickedAt;
    private static boolean windowChainOpen;

    /** Prevents instantiation of the static client event subscriber. */
    private TownHallIncentiveTab() { }

    /**
     * Records the exact Town Hall that the player opened rather than guessing from proximity.
     * @param event client block interaction that may open a Town Hall window
     */
    @SubscribeEvent
    public static void onInteract(final PlayerInteractEvent.RightClickBlock event)
    {
        if (!event.getLevel().isClientSide) return;
        clearContext();
        if (event.getLevel().getBlockState(event.getPos()).is(ModBlocks.blockHutTownHall))
        {
            townHallPosition = event.getPos().immutable();
            townHallDimension = event.getLevel().dimension();
            clickedAt = event.getLevel().getGameTime();
        }
    }

    /**
     * Retains an explicitly known Town Hall when returning from the incentive editor to core pages.
     * @param building Town Hall whose module editor is opening
     */
    public static void rememberTownHall(final IBuildingView building)
    {
        townHallPosition = building.getID();
        townHallDimension = building.getColony().getDimension();
        windowChainOpen = true;
    }

    /**
     * Drops navigation context when the player returns to the world or leaves BlockUI screens.
     * @param event screen transition
     */
    @SubscribeEvent
    public static void onOpening(final ScreenEvent.Opening event)
    {
        if (!(event.getNewScreen() instanceof BOScreen)) clearContext();
    }

    /**
     * Adds the incentive tab to an initialized core Town Hall page, including subsequent page navigation.
     * @param event initialized client screen
     */
    @SubscribeEvent
    public static void onScreenInit(final ScreenEvent.Init.Post event)
    {
        if (!(event.getScreen() instanceof BOScreen screen) || !(screen.getWindow() instanceof AbstractWindowTownHall window)) return;
        final var level = Minecraft.getInstance().level;
        if (level == null || townHallPosition == null || !level.dimension().equals(townHallDimension)) return;
        if (!windowChainOpen && (level.getGameTime() < clickedAt || level.getGameTime() - clickedAt > OPEN_TIMEOUT_TICKS))
        {
            clearContext();
            return;
        }
        final IBuildingView building = IColonyManager.getInstance().getBuildingView(townHallDimension, townHallPosition);
        if (building == null) return;
        final CitizenIncentiveModuleView incentives = building.getModuleViewByType(CitizenIncentiveModuleView.class);
        if (incentives == null || window.findPaneByID(ID_TAB_VIEW) != null) return;
        windowChainOpen = true;
        final TabsWindowModule tabs = new TabsWindowModule(window, new Random(building.getID().hashCode()));
        // Core's book starts at x=75 and ends at x=449 inside its 524-wide window.
        tabs.setTabXOffset(88);
        tabs.setTabYOffset(73);
        tabs.renderTabButton(0, TabsWindowModule.TabImageSide.RIGHT, incentives.getIconResourceLocation(),
            incentives.getDesc().copy(), button -> incentives.getWindow().open());
    }

    /** Clears both pending interaction context and the active Town Hall navigation chain. */
    private static void clearContext()
    {
        townHallPosition = null;
        townHallDimension = null;
        windowChainOpen = false;
        clickedAt = 0;
    }
}
