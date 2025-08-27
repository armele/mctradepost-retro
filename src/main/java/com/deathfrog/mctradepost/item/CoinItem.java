package com.deathfrog.mctradepost.item;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.entity.CoinEntity;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CoinItem extends Item
{
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
        return null; // fallback to default
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
        // Optional: Give the coin a shiny effect if you like
        return true;
    }
}
