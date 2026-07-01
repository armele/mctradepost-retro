package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetRoles;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scavenge profile for land-based mushroom work locations.
 * <p>
 * Mushroom scavenging supports two target types:
 * </p>
 * <ul>
 *     <li>An existing block in {@link ModTags.BLOCKS#MUSHROOM_SCAVENGE_BLOCK_TAG}, which is harvested and removed.</li>
 *     <li>An empty, dim block above valid mushroom-growing ground, where the pet discovers and plants a new mushroom.</li>
 * </ul>
 * <p>
 * Both paths award the pet an item from a data-driven loot table. Empty forage spots use the shared
 * {@code mctradepost:pet/mushroom_scavenge/forage} table, while existing mushroom blocks use a table named after the harvested
 * block path, such as {@code mctradepost:pet/mushroom_scavenge/red_mushroom}.
 * </p>
 *
 * @param <P> the concrete pet entity type using this profile
 */
public class MushroomScavengeProfile<P extends Animal & ITradePostPet> implements IScavengeProfile<P>
{
    private static final String LOOT_BASE = "pet/mushroom_scavenge";
    private static final ResourceLocation FORAGE_TABLE =
        ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, LOOT_BASE + "/forage");

    /**
     * Land mushroom scavenging is tied to the mushroom scavenging base/work location.
     *
     * @return the required pet role for this profile
     */
    @Override
    public PetRoles requiredRole()
    {
        return PetRoles.SCAVENGE_LAND;
    }

    /**
     * Randomly samples positions near the pet's work location and returns the first valid mushroom scavenge target.
     * <p>
     * A target can either be an existing tagged mushroom block or a valid empty dim spot where a new mushroom can be discovered.
     * </p>
     *
     * @param pet the pet attempting to scavenge
     * @param searchRadius the horizontal search radius around the work location
     * @return a valid mushroom scavenge target, or {@code null} if none was found
     */
    @Override
    public @Nullable BlockPos findTarget(final P pet, final int searchRadius)
    {
        final Level level = pet.level();
        final BlockPos origin = pet.getWorkLocation();

        if (!(level instanceof ServerLevel serverLevel) || origin == null || BlockPos.ZERO.equals(origin)) return null;

        final int radius = Math.max(3, searchRadius);

        RandomSource rnd = pet.getRandom();

        if (rnd == null) return null;

        for (int tries = 0; tries < 40; tries++)
        {
            final BlockPos candidate = origin.offset(
                Mth.nextInt(rnd, -radius, radius),
                Mth.nextInt(rnd, -3, 3),
                Mth.nextInt(rnd, -radius, radius));

            if (candidate == null) continue;

            if (!serverLevel.isLoaded(candidate)) continue;

            final BlockState state = serverLevel.getBlockState(candidate);
            if (isHarvestable(serverLevel, candidate, state))
            {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Mushroom targets are already walkable land positions, so the target itself is the navigation anchor.
     *
     * @param pet the pet attempting to navigate
     * @param target the chosen scavenge target
     * @return the position the pet should path toward
     */
    @Override
    public @Nonnull BlockPos navigationAnchor(final P pet, final @Nonnull BlockPos target)
    {
        return target;
    }

    /**
     * Land pets should navigate to the target block level with no vertical offset.
     *
     * @return zero vertical navigation offset
     */
    @Override
    public double navigationYOffset()
    {
        return 0.0;
    }

    /**
     * Determines whether a block position can be used by mushroom scavenging.
     * <p>
     * Existing tagged mushrooms are harvestable. Air blocks are harvestable only when they are dim enough and sit above dirt or
     * mushroom-growable blocks.
     * </p>
     *
     * @param level the server level containing the target
     * @param pos the candidate target position
     * @param state the block state at {@code pos}
     * @return {@code true} when the pet can scavenge this position
     */
    @SuppressWarnings("null")
    @Override
    public boolean isHarvestable(final ServerLevel level, final BlockPos pos, final BlockState state)
    {
        if (state.is(ModTags.BLOCKS.MUSHROOM_SCAVENGE_BLOCK_TAG))
        {
            return true;
        }

        return isValidEmptyForageSpot(level, pos, state);
    }

    /**
     * Resolves the loot table used for the selected mushroom target.
     * <p>
     * Existing mushroom blocks map to {@code pet/mushroom_scavenge/<block_path>}. Empty forage spots map to the shared
     * {@code pet/mushroom_scavenge/forage} table so the discovered mushroom can be chosen by data.
     * </p>
     *
     * @param level the server level containing the target
     * @param pos the selected target position
     * @param state the block state at {@code pos}
     * @return the loot table for this mushroom scavenge action, or {@code null} when the target is invalid
     */
    @SuppressWarnings("null")
    @Override
    public @Nullable ResourceLocation lootTableFor(final ServerLevel level, final BlockPos pos, @Nonnull final BlockState state)
    {
        if (!isHarvestable(level, pos, state))
        {
            return null;
        }

        if (state.is(ModTags.BLOCKS.MUSHROOM_SCAVENGE_BLOCK_TAG))
        {
            final Block block = state.getBlock();
            final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, LOOT_BASE + "/" + blockId.getPath());
        }

        return FORAGE_TABLE;
    }

    /**
     * Applies mushroom-specific world changes after the shared scavenge goal has awarded the generated drops to the pet.
     * <p>
     * Existing tagged mushrooms are removed after being harvested. Empty forage spots inspect the awarded drops and plant the first
     * dropped block item that is also tagged as a mushroom scavenge block, allowing the pet to both spread and collect mushrooms.
     * </p>
     *
     * @param level the server level containing the target
     * @param pos the selected target position
     * @param pet the pet that performed the scavenge
     * @param drops the drops already awarded to the pet
     */
    @SuppressWarnings("null")
    @Override
    public void onDropsAwarded(final ServerLevel level, final @Nonnull BlockPos pos, final P pet, final List<ItemStack> drops)
    {
        final BlockState state = level.getBlockState(pos);

        if (state.is(ModTags.BLOCKS.MUSHROOM_SCAVENGE_BLOCK_TAG))
        {
            level.removeBlock(pos, false);
            return;
        }

        if (!isValidEmptyForageSpot(level, pos, state))
        {
            return;
        }

        for (ItemStack drop : drops)
        {
            if (drop.getItem() instanceof BlockItem blockItem)
            {
                final BlockState planted = blockItem.getBlock().defaultBlockState();
                if (planted.is(ModTags.BLOCKS.MUSHROOM_SCAVENGE_BLOCK_TAG) && planted.canSurvive(level, pos))
                {
                    level.setBlock(pos, NullnessBridge.assumeNonnull(planted), 3);
                    return;
                }
            }
        }
    }

    /**
     * Plays mushroom-themed feedback once a scavenge action succeeds.
     *
     * @param level the server level containing the target
     * @param pos the selected target position
     * @param pet the pet that performed the scavenge
     */
    @SuppressWarnings("null")
    @Override
    public void onSuccessfulHarvest(final ServerLevel level, final @Nonnull BlockPos pos, final P pet)
    {
        level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.NEUTRAL, 0.8f, 0.75f);
        level.sendParticles(ParticleTypes.MYCELIUM,
            pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
            10, 0.25, 0.1, 0.25, 0.01);
    }

    /**
     * Checks whether an empty position can receive a newly discovered mushroom.
     *
     * @param level the server level containing the candidate
     * @param pos the candidate empty position
     * @param state the block state at {@code pos}
     * @return {@code true} when the position is empty, dim, and above valid mushroom-growing ground
     */
    private static boolean isValidEmptyForageSpot(final ServerLevel level, final BlockPos pos, final BlockState state)
    {
        if (!state.isAir()) return false;
        if (pos == null) return false;
        if (level.getMaxLocalRawBrightness(pos) >= 8) return false;

        final BlockPos below = pos.below();

        if (below == null) return false;

        final BlockState belowState = level.getBlockState(below);

        return belowState.is(NullnessBridge.assumeNonnull(BlockTags.DIRT))
            || belowState.is(NullnessBridge.assumeNonnull(BlockTags.MUSHROOM_GROW_BLOCK));
    }
}
