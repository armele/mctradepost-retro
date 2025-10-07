package com.deathfrog.mctradepost.item;

import java.util.List;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.entity.CoinEntity;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CoinItem extends Item
{
    public static final int GOLD_MULTIPLIER = 8;
    public static final int DIAMOND_MULTIPLIER = 64;

    public CoinItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public Entity createEntity(@Nonnull Level level, @Nonnull Entity location, @Nonnull ItemStack stack)
    {
        if (!level.isClientSide && stack.getItem() instanceof CoinItem)
        {
            CoinEntity coin = new CoinEntity(level, location.getX(), location.getY(), location.getZ(), stack.copy());
            coin.setDeltaMovement(location.getDeltaMovement());
            return coin;
        }
        return null;
    }

    public EntityType<?> getEntityRepresentation()
    {
        return MCTradePostMod.COIN_ENTITY_TYPE.get();
    }

    /**
     * Handles using a coin in the player's hand by making it "throwable", spawning a CoinEntity and subtracting one from the stack.
     * This relies on the CoinEntity logic to follow up with wishing-well identification.
     *
     * @param level  the current level
     * @param player the player using the coin
     * @param hand   the hand the player is using
     * @return an InteractionResultHolder containing the modified stack
     */
    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && !stack.isEmpty())
        {
            // Drop one item like pressing "Q"
            ItemStack toDrop = stack.split(1);
            player.drop(toDrop, false);

            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * Handles right click on the marketplace with coins in hand.
     *
     * @param ctx the context
     * @return the result
     */
    @Override
    public InteractionResult useOn(final @Nonnull UseOnContext ctx)
    {
        Player player = ctx.getPlayer();

        if (player == null)
        {
            MCTradePostMod.LOGGER.error("Player is null while attempting to use Trade Coins.");
            return InteractionResult.PASS;
        }

        final ItemStack coinstack = player.getItemInHand(ctx.getHand());
        final BlockEntity entity = ctx.getLevel().getBlockEntity(ctx.getClickedPos());

        if (entity != null && entity instanceof TileEntityColonyBuilding buildingEntity)
        {
            IBuilding building = buildingEntity.getBuilding();

            if (building instanceof BuildingMarketplace marketplace)
            {
                marketplace.depositCoins(player, coinstack);
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Check if the given ItemStack is a Trade Coin.
     * 
     * @param stack the ItemStack to check
     * @return true if the stack is a Trade Coin, false otherwise
     */
    public static boolean isCoin(ItemStack stack)
    {
        return !stack.isEmpty() && stack.getItem() instanceof CoinItem;
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack)
    {
        // Give the coin a shiny effect
        return true;
    }

    // --- CoinItem helpers: persist/retrieve colony id on the stack ---

    /** Returns true if the stack has a minted-colony id component. */
    public static boolean hasMintColonyId(ItemStack stack)
    {
        return !stack.isEmpty() && stack.has(MCTPModDataComponents.MINT_COLONY_ID.get());
    }

    /** Set (persist) the colony id on the item stack. */
    public static void setMintColonyId(ItemStack stack, int colonyId)
    {
        if (!stack.isEmpty())
        {
            stack.set(MCTPModDataComponents.MINT_COLONY_ID.get(), colonyId);
        }
    }

    /** Get the colony id, or defaultValue if not present. */
    public static int getMintColonyId(ItemStack stack, int defaultValue)
    {
        if (stack.isEmpty()) return defaultValue;
        return stack.getOrDefault(MCTPModDataComponents.MINT_COLONY_ID.get(), defaultValue);
    }

    /** Clear the colony id. */
    public static void clearMintColonyId(ItemStack stack)
    {
        if (!stack.isEmpty())
        {
            stack.remove(MCTPModDataComponents.MINT_COLONY_ID.get());
        }
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
        @Nonnull Item.TooltipContext ctx,
        @Nonnull List<net.minecraft.network.chat.Component> tooltip,
        @Nonnull TooltipFlag flag)
    {
        boolean colonyExists = false;

        // Only show if present
        if (hasMintColonyId(stack))
        {
            int colonyId = getMintColonyId(stack, -1);

            List<IColony> colonies = IColonyManager.getInstance().getAllColonies();
            for (IColony colony : colonies)
            {
                if (colony.getID() == colonyId)
                {
                    // i18n-friendly line (preferred)
                    tooltip.add(Component.translatable("com.mctradepost.coremod.gui.econ.coins.mintcolony.tooltip", colony.getName())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                    colonyExists = true;
                    break;
                }
            }
        }

        if (!colonyExists)
        {
            // i18n-friendly line (preferred)
            tooltip.add(Component.translatable("com.mctradepost.coremod.gui.econ.coins.mintunknown.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
