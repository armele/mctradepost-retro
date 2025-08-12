package com.deathfrog.mctradepost.core.blocks;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.blocks.blockentity.PetWorkingBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;

public class AbstractBlockPetWorkingLocation extends Block implements EntityBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final String TOOLTIP_BASE = "item.mctradepost.petworkinglocation.";
    public static final String TOOLTIP_EXPANDED_BASE = "item.mctradepost.petworkinglocation.expanded.";

    public AbstractBlockPetWorkingLocation(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new PetWorkingBlockEntity(pos, state);
    }

    @NotNull
    public static AbstractBlockPetWorkingLocation[] getPetWorkBlocks()
    {
        return new AbstractBlockPetWorkingLocation[] 
        {
            MCTradePostMod.TROUGH.get(),
            MCTradePostMod.SCAVENGE.get()
        };
    }
        
    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context)
    {
        // Rotate the block to face *opposite* the player's horizontal facing direction
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public boolean isCollisionShapeFullBlock(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return false;
    }

    @Override
    public boolean useShapeForLightOcclusion(@Nonnull BlockState state) {
        return true;
    }

    /**
     * Called when the block is placed, and sets the custom name of the PetWorkingBlockEntity to the display name of the item used to place it.
     * This is used to pass the name of the pet to the block entity, so that it can be displayed in the block's inventory.
     * @param level the level the block is being placed in
     * @param pos the position the block is being placed at
     * @param state the block state of the block being placed
     * @param placer the entity that is placing the block, or null if the block is being placed by a dispenser
     * @param stack the item stack used to place the block
     */
    @Override
    public void setPlacedBy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
                            @Nullable LivingEntity placer, @Nonnull ItemStack stack) 
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PetWorkingBlockEntity petBE) {
            petBE.setCustomName(stack.getDisplayName());
        }
    }

    @Override
    public ItemInteractionResult useItemOn(
        @Nonnull ItemStack stack,
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Player player,
        @Nonnull InteractionHand hand,
        @Nonnull BlockHitResult hit)
    {
        return handleInteraction(level, pos, player);
    }

    @Override
    public InteractionResult useWithoutItem(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Player player,
        @Nonnull BlockHitResult hit)
    {
        ItemInteractionResult result = handleInteraction(level, pos, player);
        
        // Convert manually to InteractionResult
        return switch (result) {
            case CONSUME -> InteractionResult.CONSUME;
            case SUCCESS -> InteractionResult.SUCCESS;
            case FAIL -> InteractionResult.FAIL;
            default -> InteractionResult.PASS;
        };
    }
    private ItemInteractionResult handleInteraction(Level level, BlockPos pos, Player player)
    {
        if (level.isClientSide)
        {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MenuProvider provider)
        {
            player.openMenu(provider);
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Appends information to the item's tooltip.
     * <p>
     * This shows a short line of text, and if Shift is held, provides additional information.
     * </p>
     * @param stack the item stack to generate the tooltip for
     * @param context the tooltip context
     * @param tooltip the list of components to append to
     * @param flag the tooltip flag
     */
    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
                                @Nonnull Item.TooltipContext context,
                                @Nonnull List<Component> tooltip,
                                @Nonnull TooltipFlag flag) 
    {
        String suffix = this.getClass().getSimpleName().toLowerCase();

        // Always show a short line
        tooltip.add(Component.translatable(TOOLTIP_BASE + suffix)
                .withStyle(ChatFormatting.GRAY));

        // Optional: show more when Shift is held
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable(TOOLTIP_EXPANDED_BASE + suffix)
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("<shift> for more info")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
