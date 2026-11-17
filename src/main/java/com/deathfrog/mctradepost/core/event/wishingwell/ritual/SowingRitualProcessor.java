package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.datacomponent.SowingWishRecord;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.event.wishingwell.WishingWellHandler;
import com.deathfrog.mctradepost.item.WishSowingItem;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import com.minecolonies.core.blocks.MinecoloniesFarmland;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.items.ItemCrop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Plants every eligible empty position in the MineColonies farm field bound to a sowing wish. */
public final class SowingRitualProcessor
{
    private static final int MAX_SURFACE_DEPTH = 5;

    private SowingRitualProcessor()
    {
    }

    /**
     * Checks whether a configured seed maps to a crop supported by MineColonies farmer planting logic.
     *
     * @param seed configured field seed
     * @return true for vanilla crops, stems, and MineColonies crops
     */
    public static boolean isSupportedSeed(@Nonnull ItemStack seed)
    {
        return cropState(seed) != null;
    }

    /**
     * Validates and executes a Wish of Sowing.
     *
     * @param marketplace marketplace whose colony owns the wishing well
     * @param wellPos wishing-well position used for the ritual effect
     * @param state current wishing-well item state
     * @return completed when at least one crop was planted, otherwise failed without consuming payment
     */
    @SuppressWarnings("null")
    public static RitualState.RitualResult process(@Nonnull BuildingMarketplace marketplace, @Nonnull BlockPos wellPos,
        @Nonnull RitualState state)
    {
        IColony colony = marketplace.getColony();
        ServerLevel level = (ServerLevel) colony.getWorld();
        ItemEntity wishEntity = null;
        for (ItemEntity companionEntity : state.companionItems)
        {
            if (companionEntity.getItem().is(MCTradePostMod.WISH_SOWING.get()))
            {
                wishEntity = companionEntity;
                break;
            }
        }
        SowingWishRecord binding = wishEntity == null ? null : WishSowingItem.binding(wishEntity.getItem());
        UUID thrower = wishEntity != null && wishEntity.getOwner() != null ? wishEntity.getOwner().getUUID() : null;
        UUID notificationPlayer = thrower != null ? thrower : binding == null ? null : binding.bindingPlayer();

        if (binding == null)
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.unbound");
        }
        if (!level.dimension().location().toString().equals(binding.dimension()))
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.wrong_dimension");
        }
        if (binding.colonyId() != colony.getID())
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.wrong_colony");
        }
        if (!colony.getPermissions().getRank(binding.bindingPlayer()).isColonyManager()
            || thrower != null && !colony.getPermissions().getRank(thrower).isColonyManager())
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.unauthorized");
        }

        FarmField field = findField(colony, binding.fieldPos());
        if (field == null)
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.missing_field");
        }

        ItemStack seed = field.getSeed();
        if (seed.isEmpty())
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.no_seed");
        }
        if (!ItemStack.isSameItemSameComponents(seed, binding.sealedSeed()))
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.seed_changed",
                binding.sealedSeed().getHoverName(), seed.getHoverName());
        }

        BlockState cropState = cropState(seed);
        if (cropState == null)
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.unsupported_seed",
                seed.getHoverName().getString());
        }
        if (!isFieldLoaded(level, field))
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.unloaded");
        }

        int planted = plantField(level, field, seed, cropState);
        if (planted == 0)
        {
            return fail(level, colony, notificationPlayer, "ritual.mctradepost.sowing.no_space");
        }

        field.setFieldStage(FarmField.Stage.PLANTED);
        colony.markDirty();
        WishingWellHandler.showRitualEffect(level, wellPos);
        MessageUtils.format("ritual.mctradepost.sowing.completed", planted, seed.getHoverName().getString())
            .sendTo(colony)
            .forAllPlayers();
        return RitualState.RitualResult.COMPLETED;
    }

    @SuppressWarnings("null")
    private static int plantField(ServerLevel level, FarmField field, ItemStack seed, BlockState cropState)
    {
        int planted = 0;
        BlockPos anchor = field.getPosition().below();
        for (int x = -field.getRadius(Direction.WEST); x <= field.getRadius(Direction.EAST); x++)
        {
            for (int z = -field.getRadius(Direction.NORTH); z <= field.getRadius(Direction.SOUTH); z++)
            {
                BlockPos surface = findSurface(level, anchor.offset(x, 0, z), 0);
                if (surface != null && plantAt(level, field, seed, cropState, surface))
                {
                    planted++;
                }
            }
        }
        return planted;
    }

    private static boolean plantAt(ServerLevel level, FarmField field, ItemStack seed, BlockState cropState,
        BlockPos surface)
    {
        BlockPos plantingPos = surface.above();

        if (plantingPos == null) return false;

        if (field.isNoPartOfField(level, surface) || !level.isEmptyBlock(plantingPos))
        {
            return false;
        }
        if (cropState.getBlock() instanceof StemBlock && hasAdjacentStem(level, plantingPos))
        {
            return false;
        }

        BlockState oldSoil = level.getBlockState(surface);
        BlockState requiredSoil = requiredSoil(seed);

        if (requiredSoil == null) return false;

        if (!isCorrectSoil(seed, oldSoil))
        {
            if (!isConvertibleSoil(oldSoil))
            {
                return false;
            }
            level.setBlockAndUpdate(surface, requiredSoil);
        }

        if (!cropState.canSurvive(level, plantingPos) || !level.setBlockAndUpdate(plantingPos, cropState))
        {
            if (!oldSoil.equals(level.getBlockState(surface)))
            {
                level.setBlockAndUpdate(surface, oldSoil);
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings("null")
    private static boolean isFieldLoaded(ServerLevel level, FarmField field)
    {
        BlockPos anchor = field.getPosition().below();
        for (int x = -field.getRadius(Direction.WEST); x <= field.getRadius(Direction.EAST); x++)
        {
            for (int z = -field.getRadius(Direction.NORTH); z <= field.getRadius(Direction.SOUTH); z++)
            {
                if (!level.isLoaded(anchor.offset(x, 0, z)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings("null")
    @Nullable
    private static BlockPos findSurface(ServerLevel level, @Nonnull BlockPos position, int depth)
    {
        if (Math.abs(depth) > MAX_SURFACE_DEPTH || !level.isLoaded(position))
        {
            return null;
        }

        BlockState state = level.getBlockState(position);
        Block block = state.getBlock();
        boolean surfaceBlock = (isSolidSurface(level, position, state) && !(block instanceof PumpkinBlock) && block != Blocks.MELON
            && !(block instanceof WebBlock)) || !state.getFluidState().isEmpty();
        if (surfaceBlock)
        {
            return depth < 0 ? position : findSurface(level, position.above(), depth + 1);
        }
        return depth > 0 ? position.below() : findSurface(level, position.below(), depth - 1);
    }

    private static boolean isSolidSurface(@Nonnull ServerLevel level, @Nonnull BlockPos position, BlockState state)
    {
        VoxelShape collisionShape = state.getCollisionShape(level, position);
        if (collisionShape.isEmpty())
        {
            return false;
        }

        AABB bounds = collisionShape.bounds();
        return bounds.getSize() >= 0.7291666666666666D || bounds.getYsize() >= 1.0D;
    }

    @Nullable
    private static BlockState cropState(ItemStack seed)
    {
        if (seed.getItem() instanceof BlockItem blockItem
            && (blockItem.getBlock() instanceof CropBlock || blockItem.getBlock() instanceof StemBlock
                || blockItem.getBlock() instanceof MinecoloniesCropBlock))
        {
            return blockItem.getBlock().defaultBlockState();
        }
        return null;
    }

    @SuppressWarnings("null")
    private static BlockState requiredSoil(ItemStack seed)
    {
        BlockState soil;
        if (seed.getItem() instanceof ItemCrop itemCrop)
        {
            MinecoloniesCropBlock crop = (MinecoloniesCropBlock) itemCrop.getBlock();
            soil = crop.getPreferredFarmland().defaultBlockState();
        }
        else
        {
            soil = Blocks.FARMLAND.defaultBlockState();
        }

        if (soil.hasProperty(BlockStateProperties.MOISTURE))
        {
            return soil.setValue(BlockStateProperties.MOISTURE, FarmBlock.MAX_MOISTURE);
        }
        return soil;
    }

    @SuppressWarnings("null")
    private static boolean isCorrectSoil(ItemStack seed, BlockState soil)
    {
        if (seed.getItem() instanceof ItemCrop itemCrop)
        {
            MinecoloniesCropBlock crop = (MinecoloniesCropBlock) itemCrop.getBlock();
            return soil.is(crop.getPreferredFarmland());
        }
        return soil.getBlock() instanceof FarmBlock;
    }

    @SuppressWarnings("null")
    private static boolean isConvertibleSoil(BlockState soil)
    {
        return soil.is(BlockTags.DIRT) || soil.getBlock() instanceof FarmBlock
            || soil.getBlock() instanceof MinecoloniesFarmland;
    }

    @SuppressWarnings("null")
    private static boolean hasAdjacentStem(ServerLevel level, BlockPos plantingPos)
    {
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            if (level.getBlockState(plantingPos.relative(direction)).getBlock() instanceof StemBlock)
            {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static FarmField findField(IColony colony, BlockPos fieldPos)
    {
        List<IBuildingExtension> fields = colony.getServerBuildingManager().getBuildingExtensions(extension ->
            extension.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get())
                && extension.getPosition().equals(fieldPos));
        if (fields.size() == 1 && fields.getFirst() instanceof FarmField field)
        {
            return field;
        }
        return null;
    }

    @SuppressWarnings("null")
    private static RitualState.RitualResult fail(ServerLevel level, IColony colony, @Nullable UUID playerId,
        String translationKey, Object... arguments)
    {
        if (playerId != null && level.getServer().getPlayerList().getPlayer(playerId) != null)
        {
            level.getServer().getPlayerList().getPlayer(playerId)
                .displayClientMessage(net.minecraft.network.chat.Component.translatable(translationKey, arguments), false);
        }
        else
        {
            MessageUtils.format(translationKey, arguments).sendTo(colony).forAllPlayers();
        }
        return RitualState.RitualResult.FAILED;
    }
}
