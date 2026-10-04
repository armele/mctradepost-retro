package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.core.blocks.blockentity.GasifierBlockEntity;
import com.deathfrog.mctradepost.core.blocks.blockentity.MooringBayBlockEntity;
import com.deathfrog.mctradepost.core.blocks.blockentity.LiftingGasStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/** Gasifier machine block that produces, stores, and exposes Lifting Gas. */
public class BlockGasifier extends BaseEntityBlock
{
    public static final String ID = "gasifier";
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    @SuppressWarnings("null")
    public BlockGasifier(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(LIT, false));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder)
    {
        builder.add(FACING, LIT);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec()
    {
        return simpleCodec(BlockGasifier::new);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state)
    {
        return new GasifierBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type)
    {
        return !level.isClientSide && type == MCTradePostTileEntities.GASIFIER.get() ?
            (BlockEntityTicker<T>) (BlockEntityTicker<GasifierBlockEntity>) GasifierBlockEntity::serverTick :
            null;
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
        if (stack.is(Items.BUCKET) && level.getBlockEntity(pos) instanceof GasifierBlockEntity gasifier &&
            gasifier.drainGas(MooringBayBlockEntity.BUCKET_VOLUME, true) == MooringBayBlockEntity.BUCKET_VOLUME)
        {
            if (!level.isClientSide)
            {
                gasifier.drainGas(MooringBayBlockEntity.BUCKET_VOLUME, false);
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
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GasifierBlockEntity be) player.openMenu(be, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SuppressWarnings("null")
    @Override
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState next, boolean moved)
    {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof GasifierBlockEntity be)
            Containers.dropContents(level, pos, be);
        super.onRemove(state, level, pos, next, moved);
    }

    @SuppressWarnings("null")
    @Override
    protected List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder builder)
    {
        List<ItemStack> drops = super.getDrops(state, builder);
        BlockEntity be =
            builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof GasifierBlockEntity gasifier && gasifier.gasAmount() > 0) for (ItemStack drop : drops) if (drop.is(asItem()))
        {
            CompoundTag data = new CompoundTag();
            data.putInt(LiftingGasStorage.LIFTING_GAS_NBT_KEY, gasifier.gasAmount());
            drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
            break;
        }
        return drops;
    }
}
