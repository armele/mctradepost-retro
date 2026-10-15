package com.deathfrog.mctradepost.item;

import java.util.List;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.items.datacomponent.NavigationChartRecord;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.NavigationChartingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Selects two Trade Docks and commissions a bounded, multi-tick water-route survey.
 * <p>
 * The first dock interaction is stored on the item. Using the chart on a different dock in the same dimension requests a persistent,
 * bidirectional linkage, while using it while sneaking clears the stored origin. A successful survey consumes the chart.
 */
public class NavigationChartItem extends Item
{
    /** Registry identifier shared by the item and its state data component. */
    public static final String ID = "navigation_chart";

    /**
     * Creates a Navigation Chart item.
     *
     * @param properties item properties configured by the registry
     */
    public NavigationChartItem(@Nonnull Properties properties)
    {
        super(properties);
    }

    /**
     * Reads the chart selection stored on an item stack.
     *
     * @param stack Navigation Chart stack to inspect
     * @return stored chart state, or an empty state when the component is absent
     */
    @SuppressWarnings("null")
    public static NavigationChartRecord record(ItemStack stack)
    {
        return stack.getOrDefault(NullnessBridge.assumeNonnull(MCTPModDataComponents.NAVIGATION_CHART.get()),
            NavigationChartRecord.empty());
    }

    /**
     * Handles the chart's dock-selection workflow on the logical server.
     * <p>
     * Sneaking clears the origin. Otherwise, the first valid dock becomes the origin and the second valid dock is submitted to the
     * charting manager. Interactions with other blocks are passed through without changing the chart.
     *
     * @param context item-use context containing the player, hand, level, and clicked position
     * @return the interaction result for client acknowledgement, pass-through, selection, or validation failure
     */
    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context)
    {
        if (context.getLevel().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level))
        {
            return InteractionResult.PASS;
        }
        
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        
        if (!level.getBlockState(pos).is(MCTradePostMod.TRADE_DOCK.get()))
        {
            return InteractionResult.PASS;
        }
        
        if (player.isShiftKeyDown())
        {
            stack.set(MCTPModDataComponents.NAVIGATION_CHART.get(), NavigationChartRecord.empty());
            player.displayClientMessage(Component.translatable("item.mctradepost.navigation_chart.cleared"), true);
            return InteractionResult.SUCCESS;
        }

        NavigationChartRecord data = record(stack);
        if (data.origin().isEmpty())
        {
            String name = NavigationChartingManager.endpointName(level, pos);
            stack.set(MCTPModDataComponents.NAVIGATION_CHART.get(),
                data.withOrigin(new DimPos(level.dimension(), pos.immutable()), name));
            player.displayClientMessage(
                Component.translatable("item.mctradepost.navigation_chart.origin_set", name, pos.toShortString()),
                true);
            return InteractionResult.SUCCESS;
        }

        DimPos origin = data.origin().get();
        
        if (!origin.dimension().equals(level.dimension()) || origin.pos().equals(pos))
        {
            player.displayClientMessage(Component.translatable("item.mctradepost.navigation_chart.invalid_destination"), true);
            return InteractionResult.FAIL;
        }
        
        return NavigationChartingManager.start(player, context.getHand(), origin.pos(), pos) ? InteractionResult.SUCCESS :
            InteractionResult.FAIL;
    }

    /**
     * Adds tooltip lines describing whether the chart is blank or which origin dock it currently stores.
     *
     * @param stack chart stack whose component state is displayed
     * @param context tooltip context supplied by the item renderer
     * @param tooltip mutable tooltip receiving the chart-state lines
     * @param flag active tooltip display flags
     */
    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag)
    {
        NavigationChartRecord data = record(stack);
        if (data.origin().isEmpty())
        {
            tooltip.add(Component.translatable("item.mctradepost.navigation_chart.blank").withStyle(ChatFormatting.GRAY));
        }
        else
        {
            tooltip.add(Component
                .translatable("item.mctradepost.navigation_chart.from", data.originName(), data.origin().get().pos().toShortString())
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(
                Component.translatable("item.mctradepost.navigation_chart.destination_unset").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.mctradepost.navigation_chart.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
