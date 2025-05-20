package com.deathfrog.mctradepost.item;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.jobs.buildings.workerbuildings.BuildingMarketplace;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CoinItem extends Item {

    public CoinItem(Properties properties) {
        super(properties);
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

        if (player == null) {
            MCTradePostMod.LOGGER.error("Player is null while attempting to use Trade Coins.");
            return InteractionResult.PASS;
        }

        final ItemStack coinstack = player.getItemInHand(ctx.getHand());
        final BlockEntity entity = ctx.getLevel().getBlockEntity(ctx.getClickedPos());

        if (entity != null && entity instanceof TileEntityColonyBuilding buildingEntity)
        {
            IBuilding building = buildingEntity.getBuilding(); 
            
            if (building instanceof BuildingMarketplace marketplace) {
                marketplace.depositCoins(player, coinstack);          
            }
        }
    
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        // Optional: Give the coin a shiny effect if you like
        return true;
    }  
}
