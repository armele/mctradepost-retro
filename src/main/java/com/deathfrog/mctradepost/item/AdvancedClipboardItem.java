package com.deathfrog.mctradepost.item;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.gui.AdvancedWindowClipBoard;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.items.ItemClipboard;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.network.chat.Component;
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
            openWindow(clipboard, ctx.getLevel(), ctx.getPlayer());
        }
    
        return InteractionResult.SUCCESS;
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