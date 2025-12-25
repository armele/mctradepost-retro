package com.deathfrog.mctradepost.core.blocks;

import java.util.Map;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StewmelierIngredientModule;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction.InteractionMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class StewpotBlock extends AbstractCauldronBlock
{
    public static final class StewpotBehaviors
    {
        private StewpotBehaviors() {}

        /**
         * Behavior map passed to AbstractCauldronBlock.
         */
        public static final @Nonnull InteractionMap BEHAVIORS = 
            new InteractionMap("stewpot", NullnessBridge.assumeNonnull(Map.of()));

        /**
         * A no-op interaction function.
         */
        @SuppressWarnings("unused")
        private static InteractionResult pass(
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Player player,
            final net.minecraft.world.InteractionHand hand,
            final ItemStack stack)
        {
            return InteractionResult.PASS;
        }
    }

    public static final @Nonnull IntegerProperty LEVEL = NullnessBridge.assumeNonnull(BlockStateProperties.LEVEL_CAULDRON);

    /**
     * Required in 1.21+: blocks provide a codec used by registry/serialization plumbing.
     * Since we only need Properties -> new StewpotBlock(Properties), simpleCodec is ideal.
     */
    public static final MapCodec<StewpotBlock> CODEC = simpleCodec(StewpotBlock::new);

    public StewpotBlock(Properties props)
    {
        // Since you don't need player interactions, behaviors can be minimal/no-op.
        super(props, StewpotBehaviors.BEHAVIORS);

        // Pick whatever default makes sense visually. 1 is fine (has visible contents).
        BlockState stewlevel = this.stateDefinition.any().setValue(LEVEL, 1);

        if (stewlevel != null) this.registerDefaultState(stewlevel);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(LEVEL);
    }

    /**
     * Determines if the given stewpot block state is full.
     * A stewpot block is full if its LEVEL property is 3 or higher.
     * @param state the block state of the stewpot block
     * @return true if the stewpot block is full, false otherwise
     */
    @Override
    public boolean isFull(BlockState state)
    {
        return state.getValue(LEVEL) >= 3;
    }

    /**
     * Returns the codec used by the registry/serialization plumbing to encode/decode instances of this block.
     * This codec is used by the game to serialize/deserialize instances of this block to/from the registry, and
     * is also used by the game to serialize/deserialize instances of this block to/from {@link net.minecraft.nbt.CompoundTag}.
     * This codec is usually a simple codec that doesn't perform any additional processing, and is used solely to
     * allow the game to serialize/deserialize instances of this block.
     * @return the codec used by the registry/serialization plumbing to encode/decode instances of this block
     */
    @Override
    protected MapCodec<? extends AbstractCauldronBlock> codec()
    {
        return CODEC;
    }

    /**
     * No player interactions for stewpot. Let other handlers proceed.
     * @param stack the item stack used to interact with the block
     * @param state the block state of the block being interacted with
     * @param level the level the block is being interacted with in
     * @param pos the position of the block being interacted with
     * @param player the player interacting with the block
     * @param hand the hand used to interact with the block
     * @param hitResult the result of the block ray trace
     * @return the result of the interaction
     */
    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hitResult)
    {

        if (pos == null || level == null || player == null)
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        final ItemInteractionResult bowlResult =
            tryFillBowlWithPerpetualStew(stack, state, level, pos, player, hand);

        if (bowlResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION)
        {
            return bowlResult;
        }

        // No player interactions for stewpot. Let other handlers proceed.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * If the player is holding an empty bowl, replace one bowl with one PerpetualStew.
     * Returns a non-PASS result if it handled the interaction.
     */
    private static ItemInteractionResult tryFillBowlWithPerpetualStew(
        final ItemStack held,
        final BlockState state,
        final Level level,
        final @Nonnull BlockPos pos,
        final Player player,
        final InteractionHand hand)
    {
        // Only act on server; client will get the success result from the server-side interaction.
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;

        // Must be an empty bowl
        if (!held.is(NullnessBridge.assumeNonnull(Items.BOWL)))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // Optional: only allow if your stewpot has contents
        if (state.getValue(LEVEL) <= 0)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // Identify module from cauldron position, and reduce the stew amount in the module.
        StewmelierIngredientModule module = StewmelierIngredientModule.kitchenFromCauldronPosition(level, pos);

        if (module == null)
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Create the filled stew item
        final ItemStack filledStew = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.PERPETUAL_STEW.get()));

        // If not creative, consume exactly one bowl from the held stack
        if (!player.getAbilities().instabuild)
        {
            held.shrink(1);
        }

        // Put the filled item into the player's hand if the hand is now empty;
        // otherwise try to add to inventory; if that fails, drop it.
        if (hand != null && held.isEmpty())
        {
            player.setItemInHand(hand, filledStew);
        }
        else if (!player.getInventory().add(filledStew))
        {
            player.drop(filledStew, false);
        }

        module.addStew(-1.0f);
        level.playSound(null, pos, NullnessBridge.assumeNonnull(SoundEvents.BOTTLE_FILL), SoundSource.BLOCKS, 1.0F, 1.0F);

        return ItemInteractionResult.SUCCESS;
    }

}
