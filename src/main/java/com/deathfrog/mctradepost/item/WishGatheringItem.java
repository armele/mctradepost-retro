package com.deathfrog.mctradepost.item;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public class WishGatheringItem extends AbstractPositionMarkerItem
{
    final protected PetTypes gatheredAnimalType;

    public WishGatheringItem(@Nonnull Properties properties, PetTypes petType)
    {
        super(properties);
        this.gatheredAnimalType = petType;
    }

    /**
     * Links one gathering wish to the clicked position. When the held stack contains multiple wishes, the newly linked wish remains in
     * the player's hand and the unchanged remainder is moved into the player's inventory. The action fails without modifying either
     * stack when the inventory cannot hold the complete remainder.
     *
     * @param context the block-use context containing the player, hand, and clicked position
     * @return {@link InteractionResult#SUCCESS} when the wish was linked or the client predicts the interaction,
     *         {@link InteractionResult#FAIL} when the remainder cannot fit, or {@link InteractionResult#PASS} without a player or item
     */
    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context)
    {
        if (context.getLevel().isClientSide())
        {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        ItemStack heldStack = context.getItemInHand();
        if (player == null || heldStack.isEmpty())
        {
            return InteractionResult.PASS;
        }

        BlockPos clicked = context.getClickedPos();
        if (heldStack.getCount() == 1)
        {
            setLinkedBlockPos(heldStack, clicked);
            sendPlayerLinkedMessage(player, clicked);
            return InteractionResult.SUCCESS;
        }

        ItemStack linkedWish = heldStack.copyWithCount(1);
        setLinkedBlockPos(linkedWish, clicked);

        ItemStack remainder = heldStack.copy();
        remainder.shrink(1);

        player.setItemInHand(context.getHand(), linkedWish);
        IItemHandler inventory = new InvWrapper(player.getInventory());
        ItemStack simulatedRemainder = ItemHandlerHelper.insertItemStacked(inventory, remainder.copy(), true);
        
        if (!simulatedRemainder.isEmpty())
        {
            player.setItemInHand(context.getHand(), heldStack);
            player.displayClientMessage(Component.translatable("com.mctradepost.wishgather.inventory_full"), true);
            return InteractionResult.FAIL;
        }

        ItemHandlerHelper.insertItemStacked(inventory, remainder, false);
        sendPlayerLinkedMessage(player, clicked);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void sendPlayerLinkedMessage(Player player, BlockPos clicked)
    {
        Component message = Component.translatable("com.mctradepost.wishgather.clicked", clicked.toShortString());
        player.displayClientMessage(NullnessBridge.assumeNonnull(message), true);
    }

    @Override
    public @Nonnull String hoverMessageId(boolean set)
    {
        if (set) return "com.mctradepost.wishgather.marker.set.tooltip";

        return "com.mctradepost.wishgather.marker.unset.tooltip";
    }
    
}
