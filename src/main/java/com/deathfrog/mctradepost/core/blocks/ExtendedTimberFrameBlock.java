package com.deathfrog.mctradepost.core.blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

import com.google.common.collect.ImmutableList;
import com.ldtteam.domumornamentum.block.AbstractBlock;
import com.ldtteam.domumornamentum.block.ICachedItemGroupBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.components.SimpleRetexturableComponent;
import com.ldtteam.domumornamentum.entity.block.MateriallyTexturedBlockEntity;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeBuilder;
import com.ldtteam.domumornamentum.tag.ModTags;
import com.ldtteam.domumornamentum.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;

public abstract class ExtendedTimberFrameBlock extends AbstractBlock<ExtendedTimberFrameBlock>
    implements IMateriallyTexturedBlock, ICachedItemGroupBlock, EntityBlock
{
    public static final List<IMateriallyTexturedBlockComponent> COMPONENTS;
    public static final DirectionProperty FACING;
    private final List<ItemStack> fillItemGroupCache = new ArrayList<ItemStack>();
    
    // MateriallyTexturedBlockRecipeProvider recipeProvider = new MateriallyTexturedBlockRecipeProvider();
    // ArchitectsCutterRecipe architectsCutterRecipe = new ArchitectsCutterRecipe();

    public ExtendedTimberFrameBlock()
    {
        super(Properties.of().mapColor(MapColor.WOOD).pushReaction(PushReaction.PUSH_ONLY).strength(3.0F, 1.0F).noOcclusion());
    }

    public boolean shouldDisplayFluidOverlay(@Nonnull BlockState state, @Nonnull BlockAndTintGetter level, @Nonnull BlockPos pos, @Nonnull FluidState fluidState)
    {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(new Property[] {FACING});
    }

    public @NotNull BlockState rotate(@Nonnull BlockState state, @Nonnull Rotation rot)
    {
        return (BlockState) state.setValue(FACING, rot.rotate((Direction) state.getValue(FACING)));
    }

    public @NotNull BlockState mirror(@Nonnull BlockState state, @Nonnull Mirror mirrorIn)
    {
        return state.rotate(mirrorIn.getRotation((Direction) state.getValue(FACING)));
    }

    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context)
    {
        return (BlockState) this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    public @NotNull List<IMateriallyTexturedBlockComponent> getComponents()
    {
        return COMPONENTS;
    }

    public @Nullable BlockEntity newBlockEntity(@Nonnull  BlockPos blockPos, @Nonnull  BlockState blockState)
    {
        return new MateriallyTexturedBlockEntity(blockPos, blockState);
    }

    public void resetCache()
    {
        this.fillItemGroupCache.clear();
    }

    public ItemStack getCloneItemStack(@Nonnull BlockState state, @Nonnull HitResult target, @Nonnull LevelReader world, @Nonnull BlockPos pos, @Nonnull Player player)
    {
        return BlockUtils.getMaterializedItemStack(world.getBlockEntity(pos), world.registryAccess(), new Property[0]);
    }

    public void buildRecipes(RecipeOutput recipeOutput)
    {
        (new ArchitectsCutterRecipeBuilder(this, RecipeCategory.BUILDING_BLOCKS)).count(COMPONENTS.size() * 2).save(recipeOutput);
    }

    public float getExplosionResistance(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull Explosion explosion)
    {
        return this.getDOExplosionResistance((x$0, x$1, x$2, x$3) -> {
            return super.getExplosionResistance(x$0, x$1, x$2, x$3);
        }, state, level, pos, explosion);
    }

    public float getDestroyProgress(@Nonnull BlockState state,
        @Nonnull Player player,
        @Nonnull BlockGetter level,
        @Nonnull BlockPos pos)
    {
        return this.getDODestroyProgress((x$0, x$1, x$2, x$3) -> {
            return super.getDestroyProgress(x$0, x$1, x$2, x$3);
        }, state, player, level, pos);
    }

    public SoundType getSoundType(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos, @Nullable Entity entity)
    {
        return this.getDOSoundType((x$0, x$1, x$2, x$3) -> {
            return super.getSoundType(x$0, x$1, x$2, x$3);
        }, state, level, pos, entity);
    }

    public IMateriallyTexturedBlockComponent getMainComponent()
    {
        return (IMateriallyTexturedBlockComponent) COMPONENTS.get(0);
    }

    public void fillItemCategory(@NotNull NonNullList<ItemStack> items)
    {
        this.fillDOItemCategory(this, items, this.fillItemGroupCache);
    }

    abstract public ExtendedTimberFrameType getExtendedTimberFrameType();

    static
    {
        SimpleRetexturableComponent base = new SimpleRetexturableComponent(ResourceLocation.withDefaultNamespace("block/oak_planks"),
                ModTags.TIMBERFRAMES_FRAME,
                Blocks.OAK_PLANKS);

        SimpleRetexturableComponent frame = new SimpleRetexturableComponent(ResourceLocation.withDefaultNamespace("block/dark_oak_planks"),
                ModTags.TIMBERFRAMES_CENTER,
                Blocks.DARK_OAK_PLANKS);

        COMPONENTS = ImmutableList.<IMateriallyTexturedBlockComponent>builder()
            .add(base)
            .add(frame)
            .build();

        FACING = BlockStateProperties.FACING;
    }
}
