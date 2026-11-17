package com.deathfrog.mctradepost.item;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.items.datacomponent.SowingWishRecord;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.SowingRitualProcessor;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.core.blocks.BlockScarecrow;
import com.minecolonies.core.colony.buildingextensions.FarmField;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Wish item that is bound to a configured MineColonies farm field before use. */
public class WishSowingItem extends Item
{
    /**
     * Creates a sowing wish.
     *
     * @param properties item properties to apply
     */
    public WishSowingItem(@Nonnull Properties properties)
    {
        super(properties.stacksTo(1));
    }

    /**
     * Binds this wish to the canonical lower position of a configured MineColonies scarecrow field.
     *
     * @param context block-use context containing the clicked scarecrow and player
     * @return success when the wish is bound, failure for an invalid or unauthorized field, or pass for non-scarecrow blocks
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
        if (!(context.getLevel() instanceof ServerLevel level) || player == null)
        {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();

        if (clickedPos == null) return InteractionResult.PASS;

        BlockState clickedState = level.getBlockState(clickedPos);
        if (!clickedState.is(ModBlocks.blockScarecrow))
        {
            return InteractionResult.PASS;
        }

        BlockPos fieldPos = clickedState.getValue(BlockScarecrow.HALF) == DoubleBlockHalf.UPPER
            ? clickedPos.below()
            : clickedPos;
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, fieldPos);
        
        if (colony == null || !colony.getPermissions().getRank(player).isColonyManager())
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.wish_sowing.unauthorized"), false);
            return InteractionResult.FAIL;
        }

        FarmField field = findField(colony, fieldPos);
        if (field == null)
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.wish_sowing.not_registered"), false);
            return InteractionResult.FAIL;
        }

        if (field.getSeed().isEmpty())
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.wish_sowing.no_seed"), false);
            return InteractionResult.FAIL;
        }

        if (!SowingRitualProcessor.isSupportedSeed(field.getSeed()))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.wish_sowing.unsupported_seed",
                field.getSeed().getHoverName()), false);
            return InteractionResult.FAIL;
        }

        ItemStack newSeed = field.getSeed().copyWithCount(1);
        ItemStack wishStack = context.getItemInHand();
        SowingWishRecord previousBinding = binding(wishStack);
        boolean sameSeed = previousBinding != null
            && ItemStack.isSameItemSameComponents(previousBinding.sealedSeed(), newSeed);

        if (!sameSeed && !removeSeed(player, newSeed))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.wish_sowing.seed_required",
                newSeed.getHoverName()), false);
            return InteractionResult.FAIL;
        }

        String cropName = newSeed.getHoverName().getString();
        SowingWishRecord record = new SowingWishRecord(colony.getID(), level.dimension().location().toString(),
            fieldPos, cropName, newSeed, player.getUUID());
        wishStack.set(component(), record);

        if (previousBinding != null && !sameSeed)
        {
            ItemStack returnedSeed = previousBinding.sealedSeed().copyWithCount(1);
            if (!player.getInventory().add(returnedSeed))
            {
                player.drop(returnedSeed, false);
            }
            player.displayClientMessage(Component.translatable("item.mctradepost.wish_sowing.rebound", cropName,
                fieldPos.toShortString(), previousBinding.cropName()), false);
        }
        else
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.wish_sowing.bound", cropName,
                fieldPos.toShortString()), false);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Adds the bound field and crop to the wish tooltip.
     *
     * @param stack item stack being described
     * @param context tooltip context
     * @param tooltip tooltip lines to append
     * @param flag tooltip display flags
     */
    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag)
    {
        SowingWishRecord record = binding(stack);
        if (record == null)
        {
            tooltip.add(Component.translatable("item.mctradepost.wish_sowing.unbound").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.translatable("item.mctradepost.wish_sowing.bound_tooltip", record.cropName())
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mctradepost.wish_sowing.field_tooltip", record.fieldPos().toShortString())
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mctradepost.wish_sowing.seed_tooltip",
            record.sealedSeed().getHoverName()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Gets the field binding stored on a wish.
     *
     * @param stack wish stack to inspect
     * @return stored binding, or {@code null} when the wish is unbound
     */
    @SuppressWarnings("null")
    public static SowingWishRecord binding(@Nonnull ItemStack stack)
    {
        return stack.get(component());
    }

    private static FarmField findField(IColony colony, BlockPos fieldPos)
    {
        List<IBuildingExtension> fields = colony.getServerBuildingManager().getBuildingExtensions(extension ->
            extension.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get())
                && extension.getPosition().equals(fieldPos));
        if (fields.size() == 1 && fields.getFirst() instanceof FarmField field)
        {
            return field;
        }
        return null;
    }

    private static boolean removeSeed(Player player, @Nonnull ItemStack requiredSeed)
    {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack candidate = player.getInventory().getItem(slot);

            if (candidate == null) continue;

            if (ItemStack.isSameItemSameComponents(candidate, requiredSeed))
            {
                player.getInventory().removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }

    private static DataComponentType<SowingWishRecord> component()
    {
        return MCTPModDataComponents.SOWING_WISH.get();
    }
}
