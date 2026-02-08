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
     * A suitable location is one that is either:
     * 1) A solid non-water floor with a 1-2 deep column of water/ice above it.
     * 2) A single ice block with open/air above it.
     * <p>
     * The search is done by randomly offsetting from the pet's work location within the search radius,
     * and checking if the resulting location satisfies the above conditions. This is done up to 20 times.
     * If no suitable location is found, null is returned.
     * @return a suitable location for scavenging water resources, or null if no suitable location is found.
     */
    @SuppressWarnings("null")
    @Override
    @Nullable
    public BlockPos findTarget(P pet, int searchRadius)
    {
        final Level level = pet.level();
        final BlockPos origin = pet.getWorkLocation();

        if (level == null || origin == null)
        {
            // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("findWaterScavengeLocation: level or origin is null"));
            return null;
        }

        for (int tries = 0; tries < 20; tries++)
        {
            final BlockPos candidate = origin.offset(pet.getRandom().nextInt(searchRadius * 2) - searchRadius,
                pet.getRandom().nextInt(3) - 2,
                pet.getRandom().nextInt(searchRadius * 2) - searchRadius);

            if (candidate == null) continue;

            BlockState state = level.getBlockState(candidate);
            BlockState above = level.getBlockState(candidate.above());
            BlockState twoAbove = level.getBlockState(candidate.above().above());
            BlockState threeAbove = level.getBlockState(candidate.above().above().above());

            // 1) Solid non-water (true floor): not water, not ice, not air
            boolean floorIsSolidNonWater = !state.getFluidState().is(FluidTags.WATER) && !state.is(PathingUtil.ICY) && !state.isAir();

            // 2) Single ice plate case: a single ice block with open/air above
            boolean icePlateau = state.is(PathingUtil.ICY) && PathingUtil.isOpenOrIce(above);

            // 3) Shallow columns of water/ice 1–2 deep above a solid floor
            boolean depth1 = PathingUtil.isWaterOrIce(above) && PathingUtil.isOpenOrIce(twoAbove);

            boolean depth2 = PathingUtil.isWaterOrIce(above) && PathingUtil.isWaterOrIce(twoAbove) && PathingUtil.isOpenOrIce(threeAbove);

            // Final accept:
            // - Either a solid non-water floor with a 1–2 deep column above,
            // - OR a single ice block “plateau” with air/open above it.
            boolean shallowWater =  (floorIsSolidNonWater && (depth1 || depth2)) || icePlateau;

            BlockState[] neighborhood = new BlockState[] {
                state,
                level.getBlockState(candidate.below()),
                level.getBlockState(candidate.north()),
                level.getBlockState(candidate.south()),
                level.getBlockState(candidate.east()),
                level.getBlockState(candidate.west()),
                level.getBlockState(candidate.above())
            };

            boolean hasScavengeMaterials = false;
            for (BlockState s : neighborhood) 
            {
                if (s.is(ModTags.BLOCKS.WATER_SCAVENGE_BLOCK_TAG)) 
                {
                    hasScavengeMaterials = true;
                    break;
                }
            }

            if (shallowWater && hasScavengeMaterials) {
                return candidate;
            }
        }

        return null;
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
        return state.is(NullnessBridge.assumeNonnull(ModTags.BLOCKS.WATER_SCAVENGE_BLOCK_TAG));
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
