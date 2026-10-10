package com.deathfrog.mctradepost.item;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.items.datacomponent.ResurrectionWishRecord;
import com.deathfrog.mctradepost.core.event.wishingwell.resurrection.BuriedCitizenRegistry;
import com.deathfrog.mctradepost.core.event.wishingwell.resurrection.BuriedCitizenRegistry.Burial;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** 
 * Wish item that is primed against a recent permanent MineColonies grave. 
 */
public class WishResurrectionItem extends Item
{
    public static final int MAX_BURIAL_AGE = 63;

    public WishResurrectionItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context)
    {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;

        Player player = context.getPlayer();
        if (!(context.getLevel() instanceof ServerLevel level) || player == null) return InteractionResult.PASS;

        if (!level.getBlockState(context.getClickedPos()).is(ModBlocks.blockNamedGrave))
        {
            return InteractionResult.PASS;
        }

        Burial burial = BuriedCitizenRegistry.get(level).find(context.getClickedPos()).orElse(null);
        if (burial == null)
        {
            notify(player, "item.mctradepost.wish_resurrection.unregistered");
            return InteractionResult.FAIL;
        }

        IColony colony = IColonyManager.getInstance().getColonyByWorld(burial.colonyId(), level);
        if (colony == null || !colony.getPermissions().getRank(player).isColonyManager())
        {
            notify(player, "item.mctradepost.wish_resurrection.unauthorized");
            return InteractionResult.FAIL;
        }

        int cost = burialCost(colony.getDay(), burial.burialDay());
        if (cost < 0)
        {
            notify(player, "item.mctradepost.wish_resurrection.expired");
            return InteractionResult.FAIL;
        }

        ResurrectionWishRecord record = new ResurrectionWishRecord(burial.id(), burial.colonyId(),
            level.dimension().location().toString(), burial.gravePos(), burial.citizenName(), burial.burialDay(), player.getUUID());
        context.getItemInHand().set(component(), record);
        player.displayClientMessage(Component.translatable("item.mctradepost.wish_resurrection.bound",
            burial.citizenName(), cost), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag)
    {
        ResurrectionWishRecord record = stack.get(component());
        if (record == null)
        {
            tooltip.add(Component.translatable("item.mctradepost.wish_resurrection.unbound").withStyle(ChatFormatting.GRAY));
        }
        else
        {
            tooltip.add(Component.translatable("item.mctradepost.wish_resurrection.bound_tooltip",
                record.citizenName()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.mctradepost.wish_resurrection.grave_tooltip",
                record.gravePos().toShortString()).withStyle(ChatFormatting.DARK_GRAY));
            appendCostTooltip(record, tooltip);
        }
    }

    @SuppressWarnings("null")
    private static void appendCostTooltip(ResurrectionWishRecord record, List<Component> tooltip)
    {
        ResourceLocation dimensionId = ResourceLocation.tryParse(record.dimension());
        IColony colony = dimensionId == null ? null : IColonyManager.getInstance().getColonyView(
            record.colonyId(), ResourceKey.create(Registries.DIMENSION, dimensionId));

        if (colony == null)
        {
            tooltip.add(Component.translatable("item.mctradepost.wish_resurrection.cost_unavailable")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        int cost = burialCost(colony.getDay(), record.burialDay());
        tooltip.add(cost < 0
            ? Component.translatable("item.mctradepost.wish_resurrection.cost_expired").withStyle(ChatFormatting.RED)
            : Component.translatable("item.mctradepost.wish_resurrection.cost_tooltip", cost)
                .withStyle(ChatFormatting.GRAY));
    }

    public static ResurrectionWishRecord binding(ItemStack stack)
    {
        return stack.get(component());
    }

    /** Returns the dynamic diamond-coin cost, or -1 when the burial is not eligible. */
    public static int burialCost(int currentDay, int burialDay)
    {
        int age = currentDay - burialDay;
        return age < 0 || age > MAX_BURIAL_AGE ? -1 : age + 1;
    }

    @SuppressWarnings("null")
    private static @Nonnull DataComponentType<ResurrectionWishRecord> component()
    {
        return MCTPModDataComponents.RESURRECTION_WISH.get();
    }

    @SuppressWarnings("null")
    private static void notify(Player player, String key)
    {
        player.displayClientMessage(Component.translatable(key), false);
    }
}
