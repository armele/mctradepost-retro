package com.deathfrog.mctradepost.item;

import java.util.List;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.entity.CoinEntity;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.core.component.DataComponentType;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class CoinItem extends Item
{
    public static final int GOLD_MULTIPLIER = 8;
    public static final int DIAMOND_MULTIPLIER = 64;

    public CoinItem(@Nonnull Properties properties)
    {
        super(properties);
    }

    /**
     * Creates an entity based on the given stack and location.
     * If the stack contains a CoinItem and the level is not the client side, this method will return a CoinEntity.
     * The CoinEntity will have the same position and delta movement as the given entity, and will have a copy of the stack.
     * Otherwise, this method will return null.
     * @param level the current level
     * @param location the location of the entity
     * @param stack the stack to create the entity from
     * @return an entity created from the given stack and location, or null if no entity could be created
     */
    @Override
    public Entity createEntity(@Nonnull Level level, @Nonnull Entity location, @Nonnull ItemStack stack)
    {
        if (!level.isClientSide && stack.getItem() instanceof CoinItem)
        {
            CoinEntity coin = new CoinEntity(level, location.getX(), location.getY(), location.getZ(), NullnessBridge.assumeNonnull(stack.copy()));
            coin.setDeltaMovement(NullnessBridge.assumeNonnull(location.getDeltaMovement()));
            return coin;
        }
        return null;
    }


    /**
     * Returns the EntityType of the CoinEntity, which is used to represent coins in the world.
     * This is used by the createEntity method to determine if a CoinEntity should be spawned when an item is dropped.
     * @return the EntityType of the CoinEntity
     */
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
    public InteractionResultHolder<ItemStack> use(
        @Nonnull Level level,
        @Nonnull Player player,
        @Nonnull InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && !stack.isEmpty())
        {
            ItemStack toDrop = stack.split(1);
            player.drop(NullnessBridge.assumeNonnull(toDrop), false);
        }

        return InteractionResultHolder.sidedSuccess(NullnessBridge.assumeNonnull(stack), level.isClientSide);
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

        final ItemStack coinstack = player.getItemInHand(NullnessBridge.assumeNonnull(ctx.getHand()));
        final BlockEntity entity = ctx.getLevel().getBlockEntity(NullnessBridge.assumeNonnull(ctx.getClickedPos()));

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
    public static boolean hasMintColony(ItemStack stack)
    {
        return !stack.isEmpty() && stack.has(mintColonyNameDataComponent());
    }

    /** Set (persist) the colony id on the item stack. */
    public static void setMintColony(ItemStack stack, String colonyName)
    {
        if (!stack.isEmpty())
        {
            stack.set(mintColonyNameDataComponent(), colonyName);
        }
    }

    /** Get the colony id, or defaultValue if not present. */
    public static String getMintColony(ItemStack stack, @Nonnull String defaultValue)
    {
        if (stack.isEmpty()) return defaultValue;
        return stack.getOrDefault(mintColonyNameDataComponent(), defaultValue);
    }

    /** Clear the colony id. */
    public static void clearMintColonyId(ItemStack stack)
    {
        if (!stack.isEmpty())
        {
            stack.remove(mintColonyNameDataComponent());
        }
    }


    /**
     * Returns the DataComponentType responsible for persisting the colony name on the
     * item stack when it is minted. This is used to store the colony name when the
     * player mints a coin at a MineColonies building. The component is used to
     * retrieve the colony name on the client side when the player hovers over the
     * item stack.
     * @return the DataComponentType responsible for persisting the colony name
     */
    protected static @Nonnull DataComponentType<String> mintColonyNameDataComponent ()
    {
        return NullnessBridge.assumeNonnull(MCTPModDataComponents.MINT_COLONY_NAME.get());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
        @Nonnull Item.TooltipContext ctx,
        @Nonnull List<net.minecraft.network.chat.Component> tooltip,
        @Nonnull TooltipFlag flag)
    {
        // Only show if present
        if (hasMintColony(stack))
        {
            String colonyName = getMintColony(stack, "Unknown");
            // i18n-friendly line (preferred)
            tooltip.add(Component.translatable("com.mctradepost.coremod.gui.econ.coins.mintcolony.tooltip", colonyName)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        else
        {
            // i18n-friendly line (preferred)
            tooltip.add(Component.translatable("com.mctradepost.coremod.gui.econ.coins.mintunknown.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
