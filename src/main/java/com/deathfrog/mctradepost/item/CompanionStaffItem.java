package com.deathfrog.mctradepost.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.deathfrog.mctradepost.core.entity.pets.companion.CompanionManager;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Reusable staff that binds to a pet shop and summons its persistent dragon companion. */
public class CompanionStaffItem extends Item
{
    public static final String ID = "companion_staff";

    /**
     * Creates a non-stackable Companion Staff.
     *
     * @param properties base item properties
     */
    public CompanionStaffItem(@Nonnull Properties properties)
    {
        super(properties.stacksTo(1));
    }

    /**
     * Binds the staff to an eligible pet-shop hut block.
     *
     * @param context clicked-block interaction context
     * @return success when binding, summoning, or recalling completes; otherwise failure
     */
    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context)
    {
        if (context.getLevel().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player))
        {
            return InteractionResult.PASS;
        }

        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(context.getLevel(), context.getClickedPos());
        IBuilding building = colony == null ? null : colony.getServerBuildingManager().getBuilding(context.getClickedPos());
        if (!(building instanceof BuildingPetshop petshop))
        {
            return CompanionManager.summonOrRecall(player, context.getItemInHand())
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
        }

        if (colony != null && !colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            notify(player, "item.mctradepost.companion_staff.no_permission");
            return InteractionResult.FAIL;
        }

        if (petshop.getBuildingLevel() < 4)
        {
            notify(player, "item.mctradepost.companion_staff.shop_level");
            return InteractionResult.FAIL;
        }

        UUID currentBinding = CompanionManager.bindingId(context.getItemInHand());
        if (currentBinding != null && !player.isShiftKeyDown())
        {
            notify(player, "item.mctradepost.companion_staff.sneak_to_rebind");
            return InteractionResult.FAIL;
        }
        return CompanionManager.bind(player, context.getItemInHand(), petshop)
            ? InteractionResult.SUCCESS
            : InteractionResult.FAIL;
    }

    /**
     * Summons or recalls the staff's companion.
     *
     * @param level level in which the staff is used
     * @param player player using the staff
     * @param hand hand containing the staff
     * @return interaction result containing the unchanged staff stack
     */
    @SuppressWarnings("null")
    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide)
        {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResultHolder.pass(stack);
        }
        return CompanionManager.summonOrRecall(serverPlayer, stack)
            ? InteractionResultHolder.success(stack)
            : InteractionResultHolder.fail(stack);
    }

    /**
     * Cleans up persistent companion state when a dropped staff is destroyed.
     *
     * @param itemEntity destroyed staff entity
     * @param damageSource source which destroyed the entity
     */
    @SuppressWarnings("null")
    @Override
    public void onDestroyed(@Nonnull ItemEntity itemEntity, @Nonnull DamageSource damageSource)
    {
        UUID binding = CompanionManager.bindingId(itemEntity.getItem());
        if (binding != null)
        {
            CompanionManager.destroy(itemEntity, binding);
        }
    }

    /**
     * Displays whether the staff is bound.
     *
     * @param stack staff being described
     * @param context tooltip lookup context
     * @param tooltip mutable tooltip lines
     * @param flag tooltip display flags
     */
    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag)
    {
        String key = CompanionManager.bindingId(stack) == null
            ? "item.mctradepost.companion_staff.tooltip_unbound"
            : "item.mctradepost.companion_staff.tooltip_bound";
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    @SuppressWarnings("null")
    private static void notify(ServerPlayer player, @Nonnull String key)
    {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
