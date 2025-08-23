package com.deathfrog.mctradepost.core.entity;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.item.CoinItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public class CoinEntity extends ItemEntity
{
    private static final int WELL_COOLDOWN_MAX = 10; // in ticks
    private int wellSearchCooldown = WELL_COOLDOWN_MAX;

    BlockPos checkedPos = null;

    public CoinEntity(Level level, double x, double y, double z, ItemStack stack)
    {
        super(level, x, y, z, stack);
        this.setUnlimitedLifetime(); // if you want it to persist
    }

    public CoinEntity(EntityType<CoinEntity> type, Level level)
    {
        super(type, level);
        this.setUnlimitedLifetime(); // if you want it to persist
        this.setItem(new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get()));
    }

    @Override
    public void tick()
    {
        super.tick();

        if (!level().isClientSide)
        {
            if (--wellSearchCooldown > 0) return;
            if (checkedPos != null && checkedPos.equals(this.blockPosition())) return;  // Don't keep checking the same position.

            BlockPos pos = this.blockPosition(); // where the item settled

            BuildingMarketplace closestMarketplace = BuildingMarketplace.getMarketplaceFromPos(level(), pos);

            if (closestMarketplace != null)
            {
                MCTradePostMod.LOGGER.info("Nearest Marketplace to coin at {} is at {}", pos, closestMarketplace.getLocation());

                // We're inside a colony and found a marketplace, now proceed with the well logic
                WishingWellHandler.downInAWell(level(), closestMarketplace, pos);

                this.checkedPos = pos;
            }
            
            wellSearchCooldown = WELL_COOLDOWN_MAX;
        }
    }

    /**
     * Replaces dropped coins with our custom CoinEntity, which checks if it landed in a wishing well and handles the effect
     * accordingly.
     * 
     * @param event the event containing the dropped item entity
     */
    @SubscribeEvent
    public static void onItemDrop(EntityJoinLevelEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();

        if (!CoinItem.isCoin(stack)) return;

        // Prevent recursion if this is already a CoinEntity
        if (itemEntity instanceof CoinEntity) return;

        // Remove the vanilla item entity
        event.setCanceled(true);
        itemEntity.discard();

        // Create your custom CoinEntity instead
        CoinEntity coin = new CoinEntity(MCTradePostMod.COIN_ENTITY_TYPE.get(), level);
        coin.setPos(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
        coin.setDeltaMovement(itemEntity.getDeltaMovement());
        coin.setItem(stack.copy());
        coin.setPickUpDelay(10); // Optional: short delay before player can pick it up

        level.addFreshEntity(coin);

        // MCTradePostMod.LOGGER.info("Replaced dropped coin with CoinEntity at {}", coin.blockPosition());
    }
}
