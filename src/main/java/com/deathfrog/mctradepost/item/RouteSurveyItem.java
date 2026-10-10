package com.deathfrog.mctradepost.item;

import java.util.List;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.items.datacomponent.RouteSurveyRecord;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.blocks.BlockMooringBay;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Records a one-way flight from one Mooring Bay to another. */
public class RouteSurveyItem extends Item
{
    public RouteSurveyItem(@Nonnull Properties properties)
    {
        super(properties);
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);

        if (!level.isClientSide)
        {
            stack.set(MCTPModDataComponents.ROUTE_SURVEY.get(), RouteSurveyRecord.empty());
            player.displayClientMessage(Component.translatable("item.mctradepost.route_survey.cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context)
    {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown())
        {
            stack.set(MCTPModDataComponents.ROUTE_SURVEY.get(), RouteSurveyRecord.empty());
            player.displayClientMessage(Component.translatable("item.mctradepost.route_survey.cleared"), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(MCTradePostMod.MOORING_BAY.get())) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel) || !BlockMooringBay.isOpenToSky(serverLevel, pos))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.route_survey.insufficient_air_clearance"), true);
            return InteractionResult.FAIL;
        }
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        
        if (colony == null)
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.route_survey.no_colony"), true);
            return InteractionResult.FAIL;
        }

        RouteSurveyRecord record = getRecord(stack);
        DimPos endpoint = new DimPos(level.dimension(), pos.immutable());

        if (record.origin().isEmpty())
        {
            stack.set(MCTPModDataComponents.ROUTE_SURVEY.get(), record.withOrigin(endpoint, colony.getName(), colony.getID()));
            player.displayClientMessage(
                Component.translatable("item.mctradepost.route_survey.origin_set", colony.getName(), pos.toShortString()),
                true);
            return InteractionResult.SUCCESS;
        }

        if (record.isComplete())
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.route_survey.already_complete"), true);
            return InteractionResult.FAIL;
        }

        if (!record.origin().get().dimension().equals(level.dimension()) || record.origin().get().pos().equals(pos))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.route_survey.invalid_destination"), true);
            return InteractionResult.FAIL;
        }

        if (record.originColonyId() == colony.getID())
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.route_survey.same_colony"), true);
            return InteractionResult.FAIL;
        }

        stack.set(MCTPModDataComponents.ROUTE_SURVEY.get(), record.withDestination(endpoint, colony.getName(), colony.getID()));
        player.displayClientMessage(
            Component.translatable("item.mctradepost.route_survey.complete", colony.getName(), pos.toShortString()),
            true);
        return InteractionResult.SUCCESS;
    }

    @SuppressWarnings("null")
    public static RouteSurveyRecord getRecord(ItemStack stack)
    {
        return stack.getOrDefault(NullnessBridge.assumeNonnull(MCTPModDataComponents.ROUTE_SURVEY.get()), RouteSurveyRecord.empty());
    }

    @SuppressWarnings("null")
    public static boolean authorizes(ItemStack stack, DimPos origin, DimPos destination)
    {
        if (!stack.is(MCTradePostMod.ROUTE_SURVEY.get())) return false;
        RouteSurveyRecord record = getRecord(stack);
        return record.isComplete() && record.origin().get().equals(origin) && record.destination().get().equals(destination);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
        @Nonnull Item.TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag)
    {
        RouteSurveyRecord record = getRecord(stack);
        if (record.origin().isEmpty())
        {
            tooltip.add(Component.translatable("item.mctradepost.route_survey.blank").withStyle(ChatFormatting.GRAY));
        }
        else
        {
            tooltip.add(Component
                .translatable("item.mctradepost.route_survey.from", record.originName(), record.origin().get().pos().toShortString())
                .withStyle(ChatFormatting.GRAY));
            if (record.destination().isPresent()) tooltip.add(
                Component
                    .translatable("item.mctradepost.route_survey.to",
                        record.destinationName(),
                        record.destination().get().pos().toShortString())
                    .withStyle(ChatFormatting.GRAY));
            else tooltip
                .add(Component.translatable("item.mctradepost.route_survey.destination_unset").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (record.origin().isPresent())
            tooltip.add(Component.translatable("item.mctradepost.route_survey.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
