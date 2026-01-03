package com.deathfrog.mctradepost.item;

import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.gui.AdvancedWindowClipBoard;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.items.ItemClipboard;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_CLIPBOARD_COLONY_SET;

public class AdvancedClipboardItem extends ItemClipboard
{
    public AdvancedClipboardItem(Properties properties)
    {
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
    public InteractionResultHolder<ItemStack> use(final Level worldIn, final Player playerIn, final InteractionHand hand)
    {
        if (hand == null)
        {
            return new InteractionResultHolder<>(InteractionResult.PASS, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
        }

        final ItemStack clipboard = playerIn.getItemInHand(hand);

        if (clipboard.isEmpty())
        {
            return new InteractionResultHolder<>(InteractionResult.PASS, clipboard);
        }

        if (!worldIn.isClientSide)
        {
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, clipboard);
        }

        openWindow(clipboard, worldIn, playerIn);

        return new InteractionResultHolder<>(InteractionResult.SUCCESS, clipboard);
    }

    /**
     * Handles right click on the marketplace with the Advanced Clipboard Item in hand.
     * If the marketplace is at or above the minting level, it will mint one coin and give it to the player.
     * If the marketplace is not at or above the minting level, it will write the colony information to the item.
     * If the player is on the client side, it will open the window for the Advanced Clipboard Item.
     * @param ctx the context
     * @return the result
     */
    @Override
    @NotNull
    public InteractionResult useOn(final UseOnContext ctx)
    {
        Player player = ctx.getPlayer();

        if (player == null)
        {
            MCTradePostMod.LOGGER.error("Player is null while attempting to use the AdvancedClipboardItem.");
            return InteractionResult.PASS;
        }

        final ItemStack clipboard = player.getItemInHand(NullnessBridge.assumeNonnull(ctx.getHand()));
        final BlockEntity entity = ctx.getLevel().getBlockEntity(NullnessBridge.assumeNonnull(ctx.getClickedPos()));

        if (entity != null && entity instanceof TileEntityColonyBuilding buildingEntity)
        {
            IBuilding building = buildingEntity.getBuilding();
            final int mintingLevel = MCTPConfig.mintingLevel.get();

            if (building instanceof BuildingMarketplace marketplace && marketplace.getBuildingLevel() >= mintingLevel)
            {
                ItemStack coins = marketplace.mintCoins(player, 1);
                if (!coins.isEmpty())
                {
                    player.addItem(coins);
                    return InteractionResult.SUCCESS;
                }
            }
            else
            {
                buildingEntity.writeColonyToItemStack(clipboard);

                if (!ctx.getLevel().isClientSide)
                {
                    MessageUtils.format(COM_MINECOLONIES_CLIPBOARD_COLONY_SET, buildingEntity.getColony().getName())
                        .sendTo(ctx.getPlayer());
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
     * 
     * @param stack  the item
     * @param player the player entity opening the window
     */
    private static void openWindow(ItemStack stack, Level world, Player player)
    {
        final IColonyView colonyView = ColonyId.readColonyViewFromItemStack(stack);
        if (colonyView != null)
        {
            boolean hide = false;

            final CustomData current = stack.getOrDefault(NullnessBridge.assumeNonnull(DataComponents.CUSTOM_DATA), NullnessBridge.assumeNonnull(CustomData.EMPTY));
            final CompoundTag compound = current.copyTag();

            if (compound.contains(TAG_HIDEUNIMPORTANT))
            {
                hide = compound.getBoolean(TAG_HIDEUNIMPORTANT);
            }

            new AdvancedWindowClipBoard(colonyView, hide).open();
        }
        else
        {
            player.displayClientMessage(NullnessBridge.assumeNonnull(Component.translatableEscape(TranslationConstants.COM_MINECOLONIES_CLIPBOARD_NEED_COLONY)), true);
        }
    }
}
