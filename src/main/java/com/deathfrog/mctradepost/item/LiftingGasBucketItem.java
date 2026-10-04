package com.deathfrog.mctradepost.item;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.blocks.blockentity.MooringBayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A sealed, non-placeable 1,000-unit manual container for Lifting Gas. */
public class LiftingGasBucketItem extends Item
{
    public LiftingGasBucketItem(Properties properties)
    {
        super(properties);
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context)
    {
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = context.getLevel().getBlockEntity(pos);
        
        if (!(blockEntity instanceof MooringBayBlockEntity bay) ||
            bay.fillGas(MooringBayBlockEntity.BUCKET_VOLUME, true) < MooringBayBlockEntity.BUCKET_VOLUME)
            return InteractionResult.PASS;

        if (!context.getLevel().isClientSide)
        {
            bay.fillGas(MooringBayBlockEntity.BUCKET_VOLUME, false);
            ItemStack held = context.getItemInHand();
            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild)
            {
                held.shrink(1);
                if (held.isEmpty()) context.getPlayer().setItemInHand(context.getHand(), new ItemStack(Items.BUCKET));
                else if (!context.getPlayer().getInventory().add(new ItemStack(Items.BUCKET)))
                    context.getPlayer().drop(new ItemStack(Items.BUCKET), false);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
