package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.PathingUtil;
import com.deathfrog.mctradepost.core.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class WaterScavengeProfile<P extends Animal & ITradePostPet> implements IScavengeProfile<P>
{
    protected static final String LOOT_BASE = "pet/" + ModTags.BLOCKS.WATER_SCAVENGE_TAG_KEY.getPath();

    @Override
    public PetRoles requiredRole()
    {
        return PetRoles.SCAVENGE_WATER;
    }

    /**
     * Finds a suitable location for the pet to scavenge for water resources within the given search radius.
     * A suitable source is a tagged block that is either:
     * 1) A water-containing plant or waterlogged block.
     * 2) A solid floor with a 1-2 deep column of water/ice above it.
     * 3) A single ice block with open/air above it.
     * <p>
     * The search randomly samples anchors around the pet's work location and checks each anchor plus its
     * orthogonal neighbors. It returns the actual tagged source rather than the sampled anchor. This is done up to 20 times.
     * If no suitable location is found, null is returned.
     * @return a suitable location for scavenging water resources, or null if no suitable location is found.
     */
    @SuppressWarnings("null")
    @Override
    @Nullable
    public BlockPos findTarget(P pet, int searchRadius)
    {
        final BlockPos focused = FocusedForaging.findTarget(pet, this, searchRadius,
            DredgerForageRange.MIN_VERTICAL_OFFSET, DredgerForageRange.MAX_VERTICAL_OFFSET);
        if (focused != null) return focused;

        final Level level = pet.level();
        final BlockPos origin = pet.getWorkLocation();

        if (level == null || origin == null)
        {
            // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("findWaterScavengeLocation: level or origin is null"));
            return null;
        }

        for (int tries = 0; tries < 20; tries++)
        {
            final BlockPos candidate = origin.offset(
                Mth.nextInt(pet.getRandom(), -searchRadius, searchRadius),
                Mth.nextInt(pet.getRandom(), DredgerForageRange.MIN_VERTICAL_OFFSET, DredgerForageRange.MAX_VERTICAL_OFFSET),
                Mth.nextInt(pet.getRandom(), -searchRadius, searchRadius));

            if (candidate == null) continue;

            final BlockPos[] neighborhood = new BlockPos[] {
                candidate,
                candidate.below(),
                candidate.north(),
                candidate.south(),
                candidate.east(),
                candidate.west(),
                candidate.above()
            };

            for (BlockPos source : neighborhood)
            {
                if (isValidDredgingSource(level, source)) return source;
            }

        }

        return null;
    }

    /** Returns whether a tagged block is in a configuration the dredger can work. */
    @SuppressWarnings("null")
    private boolean isValidDredgingSource(@Nonnull final Level level, @Nonnull final BlockPos source)
    {
        final BlockState state = level.getBlockState(source);
        if (!state.is(ModTags.BLOCKS.WATER_SCAVENGE_BLOCK_TAG)) return false;
        if (state.getFluidState().is(FluidTags.WATER)) return true;

        final BlockState above = level.getBlockState(source.above());
        if (state.is(PathingUtil.ICY)) return PathingUtil.isOpenOrIce(above);
        if (state.isAir()) return false;

        final BlockState twoAbove = level.getBlockState(source.above(2));
        final BlockState threeAbove = level.getBlockState(source.above(3));
        final boolean depth1 = PathingUtil.isWaterOrIce(above) && PathingUtil.isOpenOrIce(twoAbove);
        final boolean depth2 = PathingUtil.isWaterOrIce(above)
            && PathingUtil.isWaterOrIce(twoAbove) && PathingUtil.isOpenOrIce(threeAbove);
        return depth1 || depth2;
    }

    /**
     * Given a target position, returns a suitable BlockPos for the pet to navigate to.
     * In this case, we just return the top of the water column starting at the target position.
     * @param pet the pet to navigate
     * @param target the target position to navigate to
     * @return the suitable BlockPos to navigate to
     */
    @Override
    @Nonnull
    public BlockPos navigationAnchor(P pet, @Nonnull BlockPos target)
    {
        BlockPos navigationPos = PathingUtil.findTopOfWaterColumn(pet.level(), target);

        // If our target position is already at the top, use it as the navigation position.
        if (navigationPos == null) 
        {
            navigationPos = target;
        }

        return navigationPos;
    }


    /**
     * Y offset to apply when navigating to the anchor position.
     * For water scavenging, this is 1.0, as the pet should navigate to the top of the water column.
     * @return the Y offset to apply when navigating to the anchor position.
     */
    @Override
    public double navigationYOffset()
    {
        return 1.0;
    }

    /**
     * Is this block at pos a valid “harvest trigger”?
     * In this case, we just check if the block is a member of the water scavenge tag.
     * @param level the level containing the block
     * @param pos the position of the block
     * @param state the block state at pos
     * @return true if the block is harvestable, false otherwise
     */
    @Override
    public boolean isHarvestable(ServerLevel level, BlockPos pos, BlockState state)
    {
        return ScavengeHarvestability.isDredgerHarvestable(state);
    }

    @Override
    @Nullable
    public ResourceLocation lootTableFor(ServerLevel level, BlockPos pos, @Nonnull BlockState state)
    {
        Block block = state.getBlock();

        if (block == null || !isHarvestable(level, pos, state))
        {
            return null;
        }

        // Use block ID (e.g. "minecraft:clay") to build a loot table ID (e.g. "mctradepost:pet/amphibious_scavenge/clay")
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        String lootPath = LOOT_BASE + "/" + blockId.getPath();
        ResourceLocation lootTableLocation = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, lootPath);
        
        return lootTableLocation;
    }

    /**
     * Called when the pet successfully harvests at the given position.
     * This method is responsible for any visual effects or sounds that should be played when the pet harvests.
     * 
     * @param level the level containing the block that was harvested
     * @param pos the position of the block that was harvested
     * @param pet the pet that did the harvesting
     */
    @Override
    public void onSuccessfulHarvest(ServerLevel level, BlockPos pos, P pet)
    {
        // Additional particles and sound
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.BUBBLE),
            pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
            20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, pos, NullnessBridge.assumeNonnull(SoundEvents.SHOVEL_FLATTEN), SoundSource.BLOCKS, 1.0f, 1.0f);
    }
    
}
