package com.deathfrog.mctradepost.item;

import java.util.List;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public abstract class AbstractPositionMarkerItem extends Item
{
    public static final String LINKED = "linked";
    
    public AbstractPositionMarkerItem(@Nonnull Properties properties)
    {
        super(properties);
    }
    
    public abstract void sendPlayerLinkedMessage(Player player, BlockPos clicked);
    public abstract @Nonnull String hoverMessageId(boolean set);

    /**
     * Called when the player right-clicks with the outpost claim marker item in hand.
     * If the player is not null, it sets the linked block position on the item stack and sends a message to the player.
     * If the level is not the client side, it does not send a message.
     * @param ctx the context of the interaction
     * @return the result of the interaction
     */
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext ctx)
    {
        Player player = ctx.getPlayer();

        if (player == null)
        {
            MCTradePostMod.LOGGER.error("Player is null while attempting to use a position marker.");
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        ItemStack stack = player != null ? player.getItemInHand(NullnessBridge.assumeNonnull(ctx.getHand())) : ItemStack.EMPTY;
        BlockPos clicked = ctx.getClickedPos();

        if (!level.isClientSide && !stack.isEmpty())
        {
            setLinkedBlockPos(stack, clicked);
            sendPlayerLinkedMessage(player, clicked);
        }

        return super.useOn(ctx);
    }

    // --- store/retrieve BlockPos on the item ---

    /**
     * Sets the linked block position on the given item stack to the given BlockPos.
     * If the item stack is empty, or the BlockPos is null, this method does nothing.
     * @param stack The item stack to set the linked block position on.
     * @param pos The BlockPos to set as the linked block position on the item stack.
     */
    public static void setLinkedBlockPos(ItemStack stack, BlockPos pos)
    {
        if (!stack.isEmpty() && pos != null)
        {
            stack.set(linkedBlockDataComponent(), pos);
        }
    }

    /**
     * Retrieves the linked block position from the given item stack.
     * If the item stack is empty or does not contain a linked block position, this method will return BlockPos.ZERO.
     * @param stack the item stack to retrieve the linked block position from
     * @return the linked block position from the item stack, or BlockPos.ZERO if the stack does not contain a linked block position
     */
    public static BlockPos getLinkedBlockPos(ItemStack stack)
    {
        return stack.getOrDefault(linkedBlockDataComponent(), NullnessBridge.assumeNonnull(BlockPos.ZERO));
    }

    /**
     * Checks if the given item stack has a linked block position.
     * If the item stack is empty, this method will return false.
     * @param stack the item stack to check
     * @return true if the item stack has a linked block position, false otherwise
     */
    public static boolean hasLinkedBlockPos(ItemStack stack)
    {
        return !stack.isEmpty() && stack.has(linkedBlockDataComponent());
    }


    /**
     * Clears the linked block position from the given item stack.
     * If the item stack is empty, this method does nothing.
     * @param stack The item stack to clear the linked block position from.
     */
    public static void clearLinkedBlockPos(ItemStack stack)
    {
        if (!stack.isEmpty())
        {
            stack.remove(linkedBlockDataComponent());
        }
    }


    /**
     * Returns the DataComponentType responsible for storing the linked block position on
     * the item stack. This component is used to store the block position of the outpost
     * claim marker item when it is used to claim a block for an outpost. The component is
     * used to retrieve the linked block position on the client side when the player hovers over
     * the item stack.
     * @return the DataComponentType responsible for storing the linked block position
     */
    private static @Nonnull DataComponentType<BlockPos> linkedBlockDataComponent() 
    {
        return NullnessBridge.assumeNonnull(MCTPModDataComponents.LINKED_BLOCK_POS.get());
    }

    /**
     * Appends information to the item's tooltip.
     * <p>
     * If the item has a linked block position, this method will add a line of text to the tooltip showing the linked block position.
     * </p>
     * @param stack the item stack to generate the tooltip for
     * @param ctx the tooltip context
     * @param tooltip the list of components to append to
     * @param flag the tooltip flag
     */
    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
        @Nonnull Item.TooltipContext ctx,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag)
    {
        if (hasLinkedBlockPos(stack))
        {
            BlockPos pos = getLinkedBlockPos(stack);

            if (pos != null && !BlockPos.ZERO.equals(pos))
            {
                String set = hoverMessageId(true);
                tooltip.add(Component.translatable(set, pos.toShortString()).withStyle(ChatFormatting.GRAY));
                return;
            }
        }

        String unset = hoverMessageId(false);
        tooltip.add(Component.translatable(unset).withStyle(ChatFormatting.GRAY));

    }

}
