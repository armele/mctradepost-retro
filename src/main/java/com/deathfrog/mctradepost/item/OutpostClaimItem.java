package com.deathfrog.mctradepost.item;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class OutpostClaimItem extends Item
{
    public static final String LINKED = "linked";
    
    public OutpostClaimItem(Properties properties)
    {
        super(properties);
    }
    
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext ctx)
    {
        Player player = ctx.getPlayer();

        if (player == null)
        {
            MCTradePostMod.LOGGER.error("Player is null while attempting to stake an outpost claim.");
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        ItemStack stack = player != null ? player.getItemInHand(ctx.getHand()) : ItemStack.EMPTY;
        BlockPos clicked = ctx.getClickedPos();

        if (!level.isClientSide && !stack.isEmpty())
        {
            // TODO: Verify the outpost is connected to the colony by rail.
            setLinkedBlockPos(stack, clicked);
            player.displayClientMessage(Component.translatable("com.mctradepost.outpost.claim", clicked.toShortString()), true);
        }

        return super.useOn(ctx);
    }

    // --- store/retrieve BlockPos on the item ---

    public static void setLinkedBlockPos(ItemStack stack, BlockPos pos)
    {
        if (!stack.isEmpty() && pos != null)
        {
            stack.set(MCTPModDataComponents.LINKED_BLOCK_POS.get(), pos);
        }
    }

    @Nullable
    public static BlockPos getLinkedBlockPos(ItemStack stack)
    {
        return stack.getOrDefault(MCTPModDataComponents.LINKED_BLOCK_POS.get(), null);
    }

    public static boolean hasLinkedBlockPos(ItemStack stack)
    {
        return !stack.isEmpty() && stack.has(MCTPModDataComponents.LINKED_BLOCK_POS.get());
    }

    public static void clearLinkedBlockPos(ItemStack stack)
    {
        if (!stack.isEmpty())
        {
            stack.remove(MCTPModDataComponents.LINKED_BLOCK_POS.get());
        }
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
            tooltip.add(Component.literal("Linked block: " + pos.toShortString()).withStyle(ChatFormatting.GRAY));
        }
    }

}
