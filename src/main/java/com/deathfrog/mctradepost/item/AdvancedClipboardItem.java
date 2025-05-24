package com.deathfrog.mctradepost.item;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.gui.AdvancedWindowClipBoard;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.registry.BuildingDataManager;
import com.minecolonies.core.items.ItemClipboard;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_CLIPBOARD_COLONY_SET;

// TODO: Fix exception
// Confirmed our mod. Related to serialization across the network.
// Next step: Try removing serialization from Marketplace and note the impact.
// Next step: Start turning off modules and note the impact.
/*
 * net.minecraft.ReportedException: Ticking/Updating BO screen
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.gui.screens.Screen.wrapScreenError(Screen.java:456) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.Minecraft.tick(Minecraft.java:1828) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.Minecraft.runTick(Minecraft.java:1161) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.Minecraft.run(Minecraft.java:807) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.main.Main.main(Main.java:230) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source) ~[?:?]
	at java.base/java.lang.reflect.Method.invoke(Unknown Source) ~[?:?]
	at MC-BOOTSTRAP/fml_loader@4.0.39/net.neoforged.fml.loading.targets.CommonLaunchHandler.runTarget(CommonLaunchHandler.java:136) ~[loader-4.0.39.jar%23107!/:4.0]
	at MC-BOOTSTRAP/fml_loader@4.0.39/net.neoforged.fml.loading.targets.CommonLaunchHandler.clientService(CommonLaunchHandler.java:124) ~[loader-4.0.39.jar%23107!/:4.0]
	at MC-BOOTSTRAP/fml_loader@4.0.39/net.neoforged.fml.loading.targets.CommonClientLaunchHandler.runService(CommonClientLaunchHandler.java:32) ~[loader-4.0.39.jar%23107!/:4.0]
	at MC-BOOTSTRAP/fml_loader@4.0.39/net.neoforged.fml.loading.targets.CommonLaunchHandler.lambda$launchService$4(CommonLaunchHandler.java:118) ~[loader-4.0.39.jar%23107!/:4.0]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.LaunchServiceHandlerDecorator.launch(LaunchServiceHandlerDecorator.java:30) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.LaunchServiceHandler.launch(LaunchServiceHandler.java:53) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.LaunchServiceHandler.launch(LaunchServiceHandler.java:71) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.Launcher.run(Launcher.java:103) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.Launcher.main(Launcher.java:74) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.BootstrapLaunchConsumer.accept(BootstrapLaunchConsumer.java:26) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.BootstrapLaunchConsumer.accept(BootstrapLaunchConsumer.java:23) [modlauncher-11.0.4.jar%23112!/:?]
	at cpw.mods.bootstraplauncher@2.0.2/cpw.mods.bootstraplauncher.BootstrapLauncher.run(BootstrapLauncher.java:210) [bootstraplauncher-2.0.2.jar:?]
	at cpw.mods.bootstraplauncher@2.0.2/cpw.mods.bootstraplauncher.BootstrapLauncher.main(BootstrapLauncher.java:69) [bootstraplauncher-2.0.2.jar:?]
Caused by: java.lang.NullPointerException: Cannot invoke "com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView.getJobDisplayName()" because the return value of "com.minecolonies.api.colony.buildings.views.IBuildingView.getModuleViewMatching(java.lang.Class, java.util.function.Predicate)" is null
	at TRANSFORMER/minecolonies@1.1.972-1.21.1-snapshot/com.minecolonies.core.colony.requestsystem.resolvers.PublicWorkerCraftingProductionResolver.getRequesterDisplayName(PublicWorkerCraftingProductionResolver.java:142) ~[minecolonies-1.1.972-1.21.1-snapshot.jar%23894!/:1.1.972-1.21.1-snapshot]
	at TRANSFORMER/minecolonies@1.1.972-1.21.1-snapshot/com.minecolonies.core.client.gui.AbstractWindowRequestTree$1.updateElement(AbstractWindowRequestTree.java:337) ~[minecolonies-1.1.972-1.21.1-snapshot.jar%23894!/:1.1.972-1.21.1-snapshot]
	at TRANSFORMER/blockui@1.0.199-1.21.1-snapshot/com.ldtteam.blockui.views.ScrollingListContainer.refreshElementPanes(ScrollingListContainer.java:166) ~[blockui-1.0.199-1.21.1-snapshot.jar%23688!/:1.0.199-1.21.1-snapshot]
	at TRANSFORMER/blockui@1.0.199-1.21.1-snapshot/com.ldtteam.blockui.views.ScrollingList.refreshElementPanes(ScrollingList.java:142) ~[blockui-1.0.199-1.21.1-snapshot.jar%23688!/:1.0.199-1.21.1-snapshot]
 
This is likely a symptom of:
[23May2025 15:37:07.300] [Render thread/ERROR][net.neoforged.neoforge.network.registration.NetworkRegistry/]: Failed to process a synchronized task of the payload: minecolonies:colony_view_building_view
java.util.concurrent.CompletionException: java.lang.IndexOutOfBoundsException: readerIndex(2047) + length(4) exceeds writerIndex(2048): UnpooledHeapByteBuf(ridx: 2047, widx: 2048, cap: 2048/2048)
	at java.base/java.util.concurrent.CompletableFuture.encodeThrowable(Unknown Source) ~[?:?]
	at java.base/java.util.concurrent.CompletableFuture.completeThrowable(Unknown Source) ~[?:?]
	at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(Unknown Source) ~[?:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.util.thread.BlockableEventLoop.doRunTask(BlockableEventLoop.java:148) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.util.thread.ReentrantBlockableEventLoop.doRunTask(ReentrantBlockableEventLoop.java:23) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.util.thread.BlockableEventLoop.pollTask(BlockableEventLoop.java:122) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.util.thread.BlockableEventLoop.runAllTasks(BlockableEventLoop.java:111) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.Minecraft.runTick(Minecraft.java:1155) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.Minecraft.run(Minecraft.java:807) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.main.Main.main(Main.java:230) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source) ~[?:?]
	at java.base/java.lang.reflect.Method.invoke(Unknown Source) ~[?:?]
	at MC-BOOTSTRAP/fml_loader@4.0.39/net.neoforged.fml.loading.targets.CommonLaunchHandler.runTarget(CommonLaunchHandler.java:136) ~[loader-4.0.39.jar%23107!/:4.0]
	at MC-BOOTSTRAP/fml_loader@4.0.39/net.neoforged.fml.loading.targets.CommonLaunchHandler.clientService(CommonLaunchHandler.java:124) ~[loader-4.0.39.jar%23107!/:4.0]
	at MC-BOOTSTRAP/fml_loader@4.0.39/net.neoforged.fml.loading.targets.CommonClientLaunchHandler.runService(CommonClientLaunchHandler.java:32) ~[loader-4.0.39.jar%23107!/:4.0]
	at MC-BOOTSTRAP/fml_loader@4.0.39/net.neoforged.fml.loading.targets.CommonLaunchHandler.lambda$launchService$4(CommonLaunchHandler.java:118) ~[loader-4.0.39.jar%23107!/:4.0]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.LaunchServiceHandlerDecorator.launch(LaunchServiceHandlerDecorator.java:30) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.LaunchServiceHandler.launch(LaunchServiceHandler.java:53) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.LaunchServiceHandler.launch(LaunchServiceHandler.java:71) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.Launcher.run(Launcher.java:103) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.Launcher.main(Launcher.java:74) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.BootstrapLaunchConsumer.accept(BootstrapLaunchConsumer.java:26) [modlauncher-11.0.4.jar%23112!/:?]
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.4/cpw.mods.modlauncher.BootstrapLaunchConsumer.accept(BootstrapLaunchConsumer.java:23) [modlauncher-11.0.4.jar%23112!/:?]
	at cpw.mods.bootstraplauncher@2.0.2/cpw.mods.bootstraplauncher.BootstrapLauncher.run(BootstrapLauncher.java:210) [bootstraplauncher-2.0.2.jar:?]
	at cpw.mods.bootstraplauncher@2.0.2/cpw.mods.bootstraplauncher.BootstrapLauncher.main(BootstrapLauncher.java:69) [bootstraplauncher-2.0.2.jar:?]
Caused by: java.lang.IndexOutOfBoundsException: readerIndex(2047) + length(4) exceeds writerIndex(2048): UnpooledHeapByteBuf(ridx: 2047, widx: 2048, cap: 2048/2048)
	at MC-BOOTSTRAP/io.netty.buffer@4.1.97.Final/io.netty.buffer.AbstractByteBuf.checkReadableBytes0(AbstractByteBuf.java:1442) ~[netty-buffer-4.1.97.Final.jar%23155!/:4.1.97.Final]
	at MC-BOOTSTRAP/io.netty.buffer@4.1.97.Final/io.netty.buffer.AbstractByteBuf.readInt(AbstractByteBuf.java:809) ~[netty-buffer-4.1.97.Final.jar%23155!/:4.1.97.Final]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.network.FriendlyByteBuf.readInt(FriendlyByteBuf.java:1160) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.network.FriendlyByteBuf.readInt(FriendlyByteBuf.java:1160) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	at TRANSFORMER/minecolonies@1.1.972-1.21.1-snapshot/com.minecolonies.core.colony.buildings.AbstractBuildingGuards$View.deserialize(AbstractBuildingGuards.java:776) ~[minecolonies-1.1.972-1.21.1-snapshot.jar%23894!/:1.1.972-1.21.1-snapshot]
	at TRANSFORMER/minecolonies@1.1.972-1.21.1-snapshot/com.minecolonies.core.colony.buildings.registry.BuildingDataManager.createViewFrom(BuildingDataManager.java:87) ~[minecolonies-1.1.972-1.21.1-snapshot.jar%23894!/:1.1.972-1.21.1-snapshot]
	at TRANSFORMER/minecolonies@1.1.972-1.21.1-snapshot/com.minecolonies.core.colony.ColonyView.handleColonyBuildingViewMessage(ColonyView.java:1072) ~[minecolonies-1.1.972-1.21.1-snapshot.jar%23894!/:1.1.972-1.21.1-snapshot]
	at TRANSFORMER/minecolonies@1.1.972-1.21.1-snapshot/com.minecolonies.core.colony.ColonyManager.handleColonyBuildingViewMessage(ColonyManager.java:791) ~[minecolonies-1.1.972-1.21.1-snapshot.jar%23894!/:1.1.972-1.21.1-snapshot]
	at TRANSFORMER/minecolonies@1.1.972-1.21.1-snapshot/com.minecolonies.core.network.messages.client.colony.ColonyViewBuildingViewMessage.onExecute(ColonyViewBuildingViewMessage.java:84) ~[minecolonies-1.1.972-1.21.1-snapshot.jar%23894!/:1.1.972-1.21.1-snapshot]
	at TRANSFORMER/blockui@1.0.200-1.21.1-snapshot/com.ldtteam.common.network.PlayMessageType.lambda$threadRedirect$0(PlayMessageType.java:271) ~[blockui-1.0.200-1.21.1-snapshot.jar%23688!/:1.0.200-1.21.1-snapshot]
	at TRANSFORMER/neoforge@21.1.168/net.neoforged.neoforge.network.handling.ClientPayloadContext.enqueueWork(ClientPayloadContext.java:31) ~[neoforge-21.1.168-universal.jar%23633!/:?]
	at TRANSFORMER/blockui@1.0.200-1.21.1-snapshot/com.ldtteam.common.network.PlayMessageType.lambda$threadRedirect$1(PlayMessageType.java:271) ~[blockui-1.0.200-1.21.1-snapshot.jar%23688!/:1.0.200-1.21.1-snapshot]
	at TRANSFORMER/blockui@1.0.200-1.21.1-snapshot/com.ldtteam.common.network.PlayMessageType.onClient(PlayMessageType.java:252) ~[blockui-1.0.200-1.21.1-snapshot.jar%23688!/:1.0.200-1.21.1-snapshot]
	at TRANSFORMER/neoforge@21.1.168/net.neoforged.neoforge.network.handling.MainThreadPayloadHandler.lambda$handle$0(MainThreadPayloadHandler.java:16) ~[neoforge-21.1.168-universal.jar%23633!/:?]
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.util.thread.BlockableEventLoop.lambda$submitAsync$0(BlockableEventLoop.java:60) ~[client-1.21.1-20240808.144430-srg.jar%23632!/:?]
	... 23 more
[23May2025 15:37:08.101] [Render thread/INFO][FluxNetworks/]: Released client Flux Networks cache
[23May2025 15:37:08.101] [Render thread/INFO][mezz.jei.neoforge.startup.StartEventObserver/]: JEI StartEventObserver received class net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent$LoggingOut
[23May2025 15:37:08.101] [Render thread/INFO][mezz.jei.neoforge.startup.StartEventObserver/]: JEI StartEventObserver transitioning state from JEI_STARTED to LISTENING
[23May2025 15:37:08.101] [Render thread/INFO][mezz.jei.library.startup.JeiStarter/]: Stopping JEI
[23May2025 15:37:08.101] [Render thread/INFO][mezz.jei.library.load.PluginCaller/]: Sending Runtime Unavailable...
[23May2025 15:37:08.103] [Render thread/INFO][mezz.jei.neoforge.plugins.neoforge.NeoForgeGuiPlugin/]: Stopping JEI GUI
[23May2025 15:37:08.108] [Render thread/INFO][mezz.jei.library.load.PluginCaller/]: Sending Runtime Unavailable took 5.871 ms
[23May2025 15:37:08.109] [Render thread/INFO][Framework/]: Unloading synced configs from server
[23May2025 15:37:08.111] [Render thread/INFO][Framework/]: Sending config unload event for refurbished_furniture.server.toml
[23May2025 15:37:08.258] [Render thread/INFO][minecolonies/]: Removed all colony views
[23May2025 15:37:08.286] [Render thread/INFO][ChunkBuilder/]: Stopping worker threads
[23May2025 15:37:08.584] [Render thread/INFO][com.mrbysco.neoauth.NeoAuth/]: Adding auth button to the multiplayer screen
[23May2025 15:37:08.584] [ForkJoinPool.commonPool-worker-2/INFO][com.mrbysco.neoauth.NeoAuth/]: Verifying Minecraft session...
 
    */


public class AdvancedClipboardItem extends ItemClipboard {
    
    public AdvancedClipboardItem(Properties properties) {
        super(properties);
    }

    /**
     * Handles mid air use.
     *
     * @param worldIn  the world
     * @param playerIn the player
     * @param hand     the hand
     * @return the result
     */
    @Override
    @NotNull
    public InteractionResultHolder<ItemStack> use(
            final Level worldIn,
            final Player playerIn,
            final InteractionHand hand)
    {
        final ItemStack clipboard = playerIn.getItemInHand(hand);

        if (!worldIn.isClientSide) {
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, clipboard);
        }

        openWindow(clipboard, worldIn, playerIn);

        return new InteractionResultHolder<>(InteractionResult.SUCCESS, clipboard);
    }   
    
    @Override
    @NotNull
    public InteractionResult useOn(final UseOnContext ctx)
    {
        Player player = ctx.getPlayer();

        if (player == null) {
            MCTradePostMod.LOGGER.error("Player is null while attempting to use the AdvancedClipboardItem.");
            return InteractionResult.PASS;
        }

        final ItemStack clipboard = player.getItemInHand(ctx.getHand());
        final BlockEntity entity = ctx.getLevel().getBlockEntity(ctx.getClickedPos());


        if (entity != null && entity instanceof TileEntityColonyBuilding buildingEntity)
        {
            IBuilding building = buildingEntity.getBuilding(); 
            final int mintingLevel = MCTPConfig.mintingLevel.get();
            
            if (building instanceof BuildingMarketplace marketplace && marketplace.getBuildingLevel() >= mintingLevel) {
                ItemStack coins = marketplace.mintCoins(player, 1);
                if (!coins.isEmpty()) {
                    player.addItem(coins);
                    return InteractionResult.SUCCESS;
                }                
            } else {
                buildingEntity.writeColonyToItemStack(clipboard);

                if (!ctx.getLevel().isClientSide)
                {
                    MessageUtils.format(COM_MINECOLONIES_CLIPBOARD_COLONY_SET, buildingEntity.getColony().getName()).sendTo(ctx.getPlayer());
                }
            }
        }
        else if (ctx.getLevel().isClientSide)
        {
            // TODO: Remove once tested.
            if (!testingStub(clipboard)) {
                openWindow(clipboard, ctx.getLevel(), ctx.getPlayer());  
            }
        }
    
        return InteractionResult.SUCCESS;
    }

    protected boolean testingStub(ItemStack clipboard) {
        final IColonyView colony = ColonyId.readColonyViewFromItemStack(clipboard);
        boolean foundMissingEntry = false;

        for (final IBuildingView buildingView : colony.getBuildings())
        {
            for (final IBuildingModuleView moduleView : buildingView.getAllModuleViews()) {
                if (moduleView instanceof WorkerBuildingModuleView) {
                    WorkerBuildingModuleView workerBuildingModuleView = (WorkerBuildingModuleView) moduleView;
                    
                    // The following section is the equivalent of the code causing the ATM10 crash.
                    WorkerBuildingModuleView matchingModuleView = buildingView.getModuleViewMatching(WorkerBuildingModuleView.class, m -> m.getJobEntry() == workerBuildingModuleView.getJobEntry());
                    if (matchingModuleView == null) {
                        MCTradePostMod.LOGGER.warn("Unable to find matching module view for building view: {} and module view: {}", buildingView, moduleView);
                        foundMissingEntry = true;
                        continue;
                    }
                    MutableComponent jobName = Component.translatableEscape(matchingModuleView.getJobDisplayName());
                    MCTradePostMod.LOGGER.info("Derived job entry {} from building view: {} and module view: {}",  jobName.getString(), buildingView, moduleView);
                }    
            }
        }

        return foundMissingEntry;
    }

    /**
     * Opens the clipboard window if there is a valid colony linked
     * @param stack the item
     * @param player the player entity opening the window
     */
    private static void openWindow(ItemStack stack, Level world, Player player)
    {        
        final IColonyView colonyView = ColonyId.readColonyViewFromItemStack(stack);
        if (colonyView != null)
        {
            new AdvancedWindowClipBoard(colonyView).open();
        }
        else
        {
            player.displayClientMessage(Component.translatableEscape(TranslationConstants.COM_MINECOLONIES_CLIPBOARD_NEED_COLONY), true);
        }
    }    
}