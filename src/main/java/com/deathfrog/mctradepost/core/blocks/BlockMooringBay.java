package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.blocks.blockentity.MooringBayBlockEntity;
import com.deathfrog.mctradepost.core.blocks.blockentity.LiftingGasStorage;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.MooringBayRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockMooringBay extends BaseEntityBlock
{
    public static final String ID = "mooring_bay";
    private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 2.5 / 16.0, 1);

    public BlockMooringBay(Properties properties)
    {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec()
    {
        return simpleCodec(BlockMooringBay::new);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state)
    {
        return new MooringBayBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(@Nonnull BlockState s, @Nonnull BlockGetter l, @Nonnull BlockPos p, @Nonnull CollisionContext c)
    {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(@Nonnull BlockState s,
        @Nonnull BlockGetter l,
        @Nonnull BlockPos p,
        @Nonnull CollisionContext c)
    {
        return SHAPE;
    }

    @Override
    protected void onPlace(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState old, boolean moved)
    {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof ServerLevel server) MooringBayRegistry.get(server).add(pos);
    }

    @SuppressWarnings("null")
    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack,
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Player player,
        @Nonnull InteractionHand hand,
        @Nonnull BlockHitResult hit)
    {
        if (stack.is(MCTradePostMod.ROUTE_SURVEY.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

        if (stack.is(Items.BUCKET) && level.getBlockEntity(pos) instanceof MooringBayBlockEntity bay &&
            bay.drainGas(MooringBayBlockEntity.BUCKET_VOLUME, true) == MooringBayBlockEntity.BUCKET_VOLUME)
        {
            if (!level.isClientSide)
            {
                bay.drainGas(MooringBayBlockEntity.BUCKET_VOLUME, false);
                stack.shrink(1);
                ItemStack filled = new ItemStack(MCTradePostMod.LIFTING_GAS_BUCKET.get());
                if (stack.isEmpty()) player.setItemInHand(hand, filled);
                else if (!player.getInventory().add(filled)) player.drop(filled, false);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hit)
    {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MooringBayBlockEntity bay) player.openMenu(bay, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SuppressWarnings("null")
    @Override
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState next, boolean moved)
    {
        if (!state.is(next.getBlock()))
        {
            if (level instanceof ServerLevel server) MooringBayRegistry.get(server).remove(pos);
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof MooringBayBlockEntity bay)
                Containers.dropContents(level, pos, bay);
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @SuppressWarnings("null")
    @Override
    protected java.util.List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder builder)
    {
        java.util.List<ItemStack> drops = super.getDrops(state, builder);
        BlockEntity be =
            builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof MooringBayBlockEntity bay && bay.gasAmount() > 0) for (ItemStack drop : drops) if (drop.is(asItem()))
        {
            CompoundTag data = new CompoundTag();
            data.putInt(LiftingGasStorage.LIFTING_GAS_NBT_KEY, bay.gasAmount());
            drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
            break;
        }
        return drops;
    }

    @SuppressWarnings("null")
    public static boolean isOpenToSky(ServerLevel level, BlockPos bay)
    {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (!level.canSeeSky(bay.offset(x, 1, z))) return false;
        return true;
    }
}
